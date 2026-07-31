package a8.hermes.ws

import a8.common.logging.Logging
import a8.hermes.proto.auth.auth.LoginBeginRequest
import a8.hermes.proto.continuum.continuum_rpc.ProcessStartedRequest
import a8.hermes.proto.process.wsmessages.{
  ClientHello,
  ClientSessionStarted,
  LoginComplete,
  MailboxLifecycle,
  MessageFromClient,
  MessageToClient,
  ProcessSessionStart,
  ResumeSession,
  Subscription,
}
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp

import java.net.URI
import java.net.http.{HttpClient, WebSocket}
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.{CompletionStage, ConcurrentHashMap, LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import scala.jdk.FutureConverters.*
import scala.concurrent.Await
import scala.concurrent.duration.*

/**
 * A WebSocket connection to the mesh gateway, speaking the same proto frames the godev
 * client does (MessageFromClient out, MessageToClient in).
 *
 * WHY THIS EXISTS: hermes has been NATS-only, which meant it could only bootstrap over the
 * mesh.mailbox.v1.* req/reply surface — the surface being starved out. WS-native bootstrap
 * lets hermes authenticate, have the gateway mint its processrun AND mailbox, and then keep
 * that same socket as the mailbox's transport. One authenticated, load-balanced connection
 * doing bootstrap and runtime.
 * See tracker FEATURE-20260724-ws-native-bootstrap-and-mailbox-transport.
 *
 * Implementation notes:
 *   - Uses the JDK's built-in java.net.http.WebSocket (Java 11+). No new dependency: the
 *     build is already on Java 21, and adding an HTTP/WS library for one socket would be
 *     dependency churn for nothing.
 *   - Frames are BINARY protobuf. The JDK delivers binary data possibly SPLIT ACROSS
 *     onBinary callbacks, so partial frames are accumulated until `last` is set — getting
 *     this wrong yields intermittent parse failures under load rather than a clean error.
 *   - Inbound messages land on a queue; callers take from it. This keeps the listener
 *     callback non-blocking, which the JDK requires.
 */
object WsMeshConnection extends Logging {

  /** How long to wait for the gateway's reply to ClientSessionStart. */
  val BootstrapTimeout: FiniteDuration = 30.seconds

  /**
   * How long a reconnect waits for the gateway's verdict on its ResumeSession.
   *
   * Shorter than BootstrapTimeout on purpose: the socket has JUST been dialled, so the reply
   * is one round-trip away. A resume that goes unanswered this long is a connection worth
   * rebuilding, not one worth waiting on.
   */
  val ResumeVerdictTimeout: FiniteDuration = 10.seconds

  /**
   * The gateway REFUSED a resume, with its coded reason.
   *
   * Distinct from a transient failure because it must not be retried: the gateway considered
   * this exact resume and said no, so re-sending it earns the same answer forever. The caller
   * decides what to do (re-login, or fail); this client has no re-mint branch by design.
   */
  final case class ResumeRefused(reason: String)
    extends RuntimeException(s"ws resume refused by the gateway: $reason")

  /**
   * Inbound silence beyond this is treated as a dead (half-open) connection.
   *
   * Generously longer than any legitimate quiet period — an idle mailbox is normal, and
   * reconnecting a healthy-but-quiet socket costs a re-handshake for nothing. This is a
   * backstop for the case TCP cannot report, not a latency budget.
   */
  val LivenessTimeout: FiniteDuration = 5.minutes

  /**
   * What a reconnect should do with the session it was holding.
   *
   * Factored out as a PURE decision so the rule can be pinned by a test without a live
   * socket — the shape react-playground uses (remint-decision.test.ts). The rule itself is
   * the important part: RESUME is the default and RE-MINT is not an option here, because
   * re-bootstrapping mints a second mailbox and orphans the first. That leak is what this
   * drive is cleaning up.
   */
  enum ResumeAction {

    /** Reattach to the mailbox we already hold (ResumeSession with its readerKey). */
    case Resume

    /** Nothing to resume — the socket died before bootstrap completed. Caller re-bootstraps. */
    case Fresh
  }

  /**
   * Decide from what we retained. Deliberately total and boring: a readerKey means resume,
   * its absence means we never got a mailbox. There is no "re-mint" branch on purpose.
   */
  def resumeAction(resumeKey: Option[String]): ResumeAction =
    resumeKey.filter(_.nonEmpty) match {
      case Some(_) => ResumeAction.Resume
      case None    => ResumeAction.Fresh
    }

  /**
   * The backoff schedule for redial: 500ms doubling to a 10s ceiling.
   *
   * Bounded so a gateway that is down does not get hammered, and capped so a client never
   * waits absurdly long once it comes back. Matches godev's redial schedule
   * (mesh/rpcclient/mesh_rpcclient.go:781) rather than inventing a third cadence.
   */
  def backoffMillis(attempt: Int): Long =
    math.min(500L * (1L << math.min(math.max(attempt, 1) - 1, 5)), 10000L)

  /**
   * Is this connection half-open? True only when we HAVE a session (so there is something
   * worth keeping alive) and nothing has arrived for longer than the timeout. An idle
   * mailbox is normal; an idle mailbox past this window is indistinguishable from a dead
   * socket, and TCP will not tell us which.
   */
  def isHalfOpen(hasSession: Boolean, silentFor: FiniteDuration, timeout: FiniteDuration = LivenessTimeout): Boolean =
    hasSession && silentFor > timeout

  /**
   * The correlationId a request should be TRACKED under, if any.
   *
   * Only SendMessageRequests carrying a correlationId are tracked: a frame with no
   * correlation has no response to wait for, so re-sending it after a reconnect would
   * duplicate work rather than recover anything. Pure so the rule is testable.
   */
  def trackingKey(msg: MessageFromClient): Option[String] =
    msg.message match {
      case MessageFromClient.Message.SendMessageRequest(smr) =>
        smr.message.flatMap(_.header).flatMap(_.rpcHeader).map(_.correlationId).filter(_.nonEmpty)
      case _ => None
    }

  /**
   * The correlationId a response RETIRES, if any. An envelope that will not decode retires
   * nothing — leaving the entry costs one duplicate resend, whereas dropping it wrongly
   * loses the request for good.
   */
  def retiringKey(m2c: MessageToClient): Option[String] =
    m2c.message match {
      case MessageToClient.Message.MessageEnvelope(env) =>
        try {
          val inner = a8.hermes.proto.process.wsmessages.Message.parseFrom(env.messageBytes.toByteArray)
          inner.header.flatMap(_.rpcHeader).map(_.correlationId).filter(_.nonEmpty)
        } catch { case _: Exception => None }
      case _ => None
    }

  /**
   * Open a websocket to the mesh gateway. `meshRootUrl` is the http(s) base url; the
   * ws(s) scheme and the /api/ws/send_receive_proto path are derived from it, matching the
   * godev client.
   */
  def connect(meshRootUrl: String): WsMeshConnection = {
    val base = meshRootUrl.stripSuffix("/")
    val wsUrl =
      if (base.startsWith("https://")) base.replaceFirst("^https://", "wss://")
      else if (base.startsWith("http://")) base.replaceFirst("^http://", "ws://")
      else base
    val uri = URI.create(s"$wsUrl/api/ws/send_receive_proto")

    val conn = new WsMeshConnection(uri)
    conn.open()
    conn
  }

}

class WsMeshConnection(uri: URI) extends Logging {

  import WsMeshConnection.*

  private val inbound = new LinkedBlockingQueue[MessageToClient]()
  private val socketRef = new AtomicReference[WebSocket](null)

  /**
   * The gateway's verdict on a ClientHello, so a reconnect can WAIT for it.
   *
   * Signalled from the listener rather than polled off `inbound`, because a reconnect that
   * drained `inbound` looking for its own reply would swallow data frames belonging to the
   * caller. The listener still queues every frame as before; this is an additional tap.
   *
   * Left = refused (the HelloError's code and text), Right = accepted.
   */
  private val handshakeOutcome = new LinkedBlockingQueue[Either[String, Unit]]()
  // Binary frames can arrive in pieces; accumulate until the JDK says `last`.
  private val partial = new AtomicReference[Array[Byte]](Array.emptyByteArray)

  // --- reconnect state -------------------------------------------------------
  //
  // Everything below exists so a dropped socket does not kill the mailbox. Before this
  // the class opened ONE socket and that was the whole story: if it dropped, every send
  // threw "ws not connected" forever and the mailbox was dead.
  //
  // Serializes redial so concurrent senders/readers rebuild the socket ONCE rather than
  // stampeding the gateway with a connection each. Mirrors godev's reconnectMu
  // (mesh/rpcclient/mesh_rpcclient.go:304).
  private val reconnectLock = new Object

  // The keys of the mailbox the gateway minted for us. Set once bootstrap succeeds, and
  // the reason a reconnect can RESUME instead of re-minting. Re-bootstrapping on every
  // reconnect would mint a SECOND mailbox and orphan the first — the orphaned-mailbox mess
  // this drive is cleaning up (a 2026-07-07 incident left 459 dead mailboxes).
  private val resumeKeyRef = new AtomicReference[Option[String]](None)
  private val authTokenRef = new AtomicReference[String]("")
  private val subscriptionsRef = new AtomicReference[Seq[Subscription]](Seq.empty)

  // Requests sent but not yet answered, keyed by correlationId. On a successful reconnect
  // these are re-sent: a request in flight when the socket dropped is otherwise lost with
  // no error to the caller — the specific gap this ticket was filed for.
  private val inFlight = new ConcurrentHashMap[String, MessageFromClient]()

  // Stamped on EVERY inbound frame. Inbound silence — not just a socket error — is how a
  // half-open connection is detected; TCP will not report one. Mirrors godev's
  // lastInboundAtNanos (mesh_rpcclient.go:310).
  private val lastInboundAtNanos = new AtomicLong(System.nanoTime())

  private val closed = new AtomicBoolean(false)

  // Recovery counters, exposed so a caller can SAY what happened rather than infer it.
  // The conformance harness reports these per scenario; a client that cannot report them has
  // to answer -1 ("not tracked"), and -1 across the board makes a row's recovery behaviour
  // invisible exactly where it matters most.
  private val reconnectCount = new AtomicLong(0)
  private val resendCount = new AtomicLong(0)
  // Nanos of the most recent reconnect, so "drop -> first message after" is measurable. That
  // number IS what reconnect quality means, and it cannot be seen from outside the client.
  private val lastReconnectAtNanos = new AtomicLong(0)
  private val recoveryMillis = new AtomicLong(-1)

  private object listener extends WebSocket.Listener {

    override def onOpen(webSocket: WebSocket): Unit = {
      logger.debug(s"ws open $uri")
      webSocket.request(1)
    }

    override def onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage[?] = {
      // Any inbound traffic proves the socket is alive — stamp BEFORE decoding, so even a
      // frame we cannot parse still counts as liveness. Silence is the signal, not content.
      val now = System.nanoTime()
      lastInboundAtNanos.set(now)
      // First frame after a reconnect closes the recovery window. Latched (compareAndSet to
      // 0) so it measures the FIRST message back, not every later one.
      val since = lastReconnectAtNanos.getAndSet(0)
      if (since > 0) recoveryMillis.set((now - since) / 1000000L)
      val chunk = new Array[Byte](data.remaining())
      data.get(chunk)
      val acc = partial.get() ++ chunk
      if (last) {
        partial.set(Array.emptyByteArray)
        try {
          val m2c = MessageToClient.parseFrom(acc)
          retireInFlight(m2c)
          // Tap the handshake verdict on its way past. The gateway answers a ClientHello with
          // SubscribeResponse (accepted) or HelloError (refused); a reconnect blocks on this
          // so a refused resume cannot be mistaken for a working one.
          m2c.message match {
            case MessageToClient.Message.HelloError(err) =>
              handshakeOutcome.offer(Left(s"[${err.code}] ${err.message}"))
            case MessageToClient.Message.SubscribeResponse(_) =>
              handshakeOutcome.offer(Right(()))
            case _ => ()
          }
          inbound.put(m2c)
        } catch {
          case e: Exception => logger.warn(s"ws: undecodable MessageToClient (${acc.length} bytes)", e)
        }
      } else {
        partial.set(acc)
      }
      webSocket.request(1)
      null
    }

    override def onError(webSocket: WebSocket, error: Throwable): Unit =
      logger.warn(s"ws error on $uri", error)

    override def onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage[?] = {
      logger.info(s"ws closed $statusCode $reason")
      null
    }
  }

  private[ws] def open(): Unit = {
    val ws =
      HttpClient
        .newHttpClient()
        .newWebSocketBuilder()
        .buildAsync(uri, listener)
        .get(30, TimeUnit.SECONDS)
    socketRef.set(ws)
  }

  /**
   * Send one MessageFromClient as a binary frame, RECONNECTING if the socket is gone.
   *
   * A SendMessageRequest is registered as in-flight before it goes out and retired when its
   * response arrives, so a reconnect can re-send whatever was still unanswered. Without
   * that, a request that was on the wire when the socket dropped is silently lost — the
   * caller waits forever for a reply that will never come.
   */
  def send(msg: MessageFromClient): Unit = {
    trackIfRequest(msg)
    sendRaw(msg, allowReconnect = true)
  }

  /** The raw write. `allowReconnect` is false while REBUILDING, so redial cannot recurse. */
  private def sendRaw(msg: MessageFromClient, allowReconnect: Boolean): Unit = {
    val bytes = msg.toByteArray
    try {
      val ws = socketRef.get()
      if (ws == null) throw new IllegalStateException("ws not connected")
      ws.sendBinary(ByteBuffer.wrap(bytes), true).get(30, TimeUnit.SECONDS)
      ()
    } catch {
      case e: Exception if allowReconnect && !closed.get() =>
        logger.warn(s"ws send failed, reconnecting: ${e.getMessage}")
        reconnect()
        val ws = socketRef.get()
        if (ws == null) throw new IllegalStateException("ws reconnect did not produce a socket")
        ws.sendBinary(ByteBuffer.wrap(bytes), true).get(30, TimeUnit.SECONDS)
        ()
    }
  }

  /**
   * Remember a request until its response arrives. Only SendMessageRequests with a
   * correlationId are tracked — a frame with no correlation has no response to wait for,
   * so re-sending it after a reconnect would duplicate work rather than recover it.
   */
  private def trackIfRequest(msg: MessageFromClient): Unit =
    trackingKey(msg).foreach { cid =>
      inFlight.put(cid, msg)
      ()
    }

  /** A response retires its request: nothing to resend once the answer is in hand. */
  private def retireInFlight(m2c: MessageToClient): Unit =
    retiringKey(m2c).foreach { cid =>
      inFlight.remove(cid)
      ()
    }

  /** How many requests are awaiting a response. Exposed for tests and diagnostics. */
  def inFlightCount: Int = inFlight.size()

  /** How many times this connection has been rebuilt. */
  def reconnects: Long = reconnectCount.get()

  /** How many in-flight requests have been re-sent across those reconnects. */
  def resends: Long = resendCount.get()

  /**
   * Millis from the most recent reconnect to the first frame that arrived after it, or -1 if
   * no reconnect has completed a round trip yet.
   *
   * This is the number that says what reconnect QUALITY means: a client can reconnect
   * promptly and still be useless for seconds afterwards, and a pass/fail cannot see the
   * difference.
   */
  def recoveryMs: Long = recoveryMillis.get()

  /**
   * Rebuild the socket and re-assert everything the server forgot.
   *
   * RESUME, DO NOT RE-MINT. The gateway's subscription state is per-connection and in
   * memory, so it is gone after a drop — but the MAILBOX is not. Re-running the bootstrap
   * would mint a second mailbox and orphan the first, which is exactly the leak this drive
   * exists to stop. ResumeSession reattaches to the mailbox we already hold, keyed by its
   * readerKey.
   *
   * Serialized: N concurrent senders that all notice the dead socket rebuild it once.
   */
  private[ws] def reconnect(): Unit =
    reconnectLock.synchronized {
      if (closed.get()) throw new IllegalStateException("ws connection is closed")

      // Another thread may have rebuilt it while we waited on the lock.
      val current = socketRef.get()
      if (current != null && !current.isInputClosed && !current.isOutputClosed) return

      var attempt = 0
      var connected = false
      while (!connected && !closed.get()) {
        attempt += 1
        try {
          val old = socketRef.getAndSet(null)
          if (old != null) try old.abort() catch { case _: Exception => () }
          partial.set(Array.emptyByteArray)

          open()
          (resumeAction(resumeKeyRef.get()), resumeKeyRef.get()) match {
            case (ResumeAction.Resume, Some(readerKey)) =>
              // Reattach to the EXISTING mailbox and re-assert the subscriptions the
              // gateway dropped with the old connection.
              //
              // Drain FIRST: a verdict left over from the bootstrap handshake (or a previous
              // attempt) would otherwise be read as this resume's answer.
              handshakeOutcome.clear()
              sendRaw(
                MessageFromClient(
                  MessageFromClient.Message.ClientHello(
                    ClientHello(
                      start = ClientHello.Start.ResumeSession(
                        ResumeSession(readerKey = readerKey, authToken = authTokenRef.get())
                      ),
                      subscriptions = subscriptionsRef.get(),
                    )
                  )
                ),
                allowReconnect = false,
              )

              // WAIT FOR THE VERDICT. This used to log "resumed mailbox" and carry on without
              // reading the reply, so a REFUSED resume was indistinguishable from a working
              // one: the client believed it held a session, resent its in-flight requests into
              // a socket the gateway had rejected, and the backlog on the real mailbox was
              // never delivered. A refusal must be loud — re-minting here would orphan the
              // mailbox we still hold, which is the leak ResumeAction exists to prevent.
              Option(handshakeOutcome.poll(ResumeVerdictTimeout.toMillis, TimeUnit.MILLISECONDS)) match {
                case Some(Right(_)) =>
                  logger.info(s"ws reconnected (attempt $attempt), resumed mailbox readerKey=$readerKey")
                case Some(Left(reason)) =>
                  // Typically UNAUTHENTICATED on the inline-login path, which has no token to
                  // present (ClientSessionStarted returns keys but no minted token). Surfaced
                  // rather than silently re-bootstrapped; the real fix is the gateway
                  // returning its token — see the class doc.
                  //
                  // ResumeRefused, not a plain exception, because the retry loop below MUST
                  // NOT back off and try again: a coded refusal is a deliberate answer and the
                  // identical resume earns the identical refusal, forever.
                  throw ResumeRefused(reason)
                case None =>
                  throw new RuntimeException(
                    s"ws resume got no verdict within $ResumeVerdictTimeout — treating the connection as unusable"
                  )
              }
            case _ =>
              // Dropped before bootstrap ever completed — there is no mailbox to resume, so
              // the caller's bootstrap will run on this fresh socket.
              logger.info(s"ws reconnected (attempt $attempt), no session to resume yet")
          }
          lastInboundAtNanos.set(System.nanoTime())
          reconnectCount.incrementAndGet()
          // Opens the recovery window; the next inbound frame closes it.
          lastReconnectAtNanos.set(System.nanoTime())
          connected = true
        } catch {
          // A CODED refusal is final: the gateway considered this exact resume and said no, so
          // backing off and re-sending it just earns the same answer forever. Propagate to the
          // caller, who can decide (re-login, or fail) — this client deliberately has no
          // re-mint branch, because re-minting orphans the mailbox it still holds.
          case e: ResumeRefused => throw e
          case e: Exception =>
            val delayMs = backoffMillis(attempt)
            logger.debug(s"ws redial failed (attempt $attempt), retrying in ${delayMs}ms: ${e.getMessage}")
            Thread.sleep(delayMs)
        }
      }

      if (connected) resendInFlight()
    }

  /**
   * Re-send every request that never got an answer. Idempotency is the SERVER's job here:
   * SendMessageRequest carries an idempotentId, so a duplicate that did land is deduped
   * rather than double-delivered. Losing the request is the worse failure.
   */
  private def resendInFlight(): Unit = {
    val pending = inFlight.values().toArray(Array.empty[MessageFromClient])
    if (pending.nonEmpty) {
      logger.info(s"ws: resending ${pending.length} in-flight request(s) after reconnect")
      pending.foreach { msg =>
        try {
          sendRaw(msg, allowReconnect = false)
          resendCount.incrementAndGet()
          ()
        } catch { case e: Exception => logger.warn(s"ws: resend failed, will retry on next reconnect: ${e.getMessage}") }
      }
    }
  }

  /**
   * Record what a reconnect needs to resume. Called once bootstrap succeeds — before this,
   * a drop has nothing to resume and the caller re-bootstraps instead.
   */
  private[ws] def rememberSession(readerKey: String, authToken: String, subscriptions: Seq[Subscription]): Unit = {
    resumeKeyRef.set(Some(readerKey).filter(_.nonEmpty))
    authTokenRef.set(authToken)
    subscriptionsRef.set(subscriptions)
  }

  /** How long since anything arrived. The half-open detector reads this. */
  def silentFor: FiniteDuration = (System.nanoTime() - lastInboundAtNanos.get()).nanos

  /**
   * Take the next inbound message, or None on timeout.
   *
   * HALF-OPEN DETECTION: a socket can be dead without the JDK ever calling onClose — the
   * peer vanished, a NAT dropped the mapping, the network moved. TCP will not tell us. So
   * if the queue is empty AND nothing has arrived for LivenessTimeout, treat the socket as
   * dead and rebuild it rather than blocking on a connection that will never speak again.
   * The caller sees a normal empty poll and retries; the socket underneath is fresh.
   */
  def receive(timeout: FiniteDuration): Option[MessageToClient] = {
    val msg = Option(inbound.poll(timeout.toMillis, TimeUnit.MILLISECONDS))
    if (msg.isEmpty && !closed.get() && isHalfOpen(resumeKeyRef.get().isDefined, silentFor)) {
      logger.warn(s"ws: no inbound traffic for ${silentFor.toSeconds}s — treating the connection as half-open and reconnecting")
      try reconnect()
      catch { case e: Exception => logger.warn(s"ws: liveness reconnect failed: ${e.getMessage}") }
    }
    msg
  }

  def close(): Unit = {
    // Set FIRST: a concurrent send that fails mid-close must not try to reconnect a
    // connection the caller is deliberately tearing down.
    closed.set(true)
    val ws = socketRef.getAndSet(null)
    if (ws != null) {
      try {
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS)
        ()
      } catch { case e: Exception => logger.debug(s"ws close failed (ignored): ${e.getMessage}") }
    }
  }

  /**
   * The WS-NATIVE BOOTSTRAP exchange: send ClientHello and wait for the gateway's
   * ClientSessionStarted carrying the keys of the mailbox it minted.
   *
   * ONE frame does session-start AND subscribe. The old shape needed two (mint, then re-send
   * the keys the gateway had just handed back, only to subscribe) — the subscriptions ride on
   * the hello now, so there is nothing to send afterwards.
   *
   * The gateway writes the processrun row SYNCHRONOUSLY and mints the mailbox linked to it
   * before replying, so by the time this returns the mailbox's owner is durable — which is
   * exactly what the create-mailbox hard check enforces server-side.
   *
   * Auth rides ON the hello, as one of two variants: an already-minted token, or a
   * login-begin that gets answered with a nonce to sign (see awaitSessionStarted). Either
   * way the connection is not authenticated until the gateway says so, and nothing about
   * the socket itself can be leaned on as a credential.
   */
  def bootstrap(
    authToken: String,
    processUid: String,
    appName: String,
    lifecycleKind: MailboxLifecycle,
    channels: Seq[String],
    subscriptions: Seq[Subscription] = Seq.empty,
    sshPublicKey: String = "",
    sshOrigin: String = "",
    signNonce: Array[Byte] => Array[Byte] = null,
  ): ClientSessionStarted = {
    val inlineLogin = authToken.isEmpty && signNonce != null && sshPublicKey.nonEmpty
    require(
      authToken.nonEmpty || inlineLogin,
      "ws-native bootstrap needs either an auth token or inline-login credentials (sshPublicKey + signNonce)",
    )

    val now = Instant.now()
    val processStart =
      ProcessStartedRequest(
        processUid = processUid,
        command = Seq(appName),
        startedAt = Some(Timestamp(seconds = now.getEpochSecond, nanos = now.getNano)),
      )

    // ONE FRAME: the session announcement AND the subscriptions. No workerUid — the
    // gateway derives it from the authenticated identity, so a client cannot assert one.
    val auth: ProcessSessionStart.Auth =
      if (inlineLogin) ProcessSessionStart.Auth.LoginBegin(LoginBeginRequest(sshPublicKey = sshPublicKey, origin = sshOrigin))
      else ProcessSessionStart.Auth.AuthToken(authToken)

    send(
      MessageFromClient(
        MessageFromClient.Message.ClientHello(
          ClientHello(
            start = ClientHello.Start.ProcessSession(
              ProcessSessionStart(
                process = Some(processStart),
                lifecycle = lifecycleKind,
                channels = channels,
                auth = auth,
              )
            ),
            subscriptions = subscriptions,
          )
        )
      )
    )

    val started = awaitSessionStarted(inlineLogin, signNonce)

    // From here a dropped socket can RESUME this mailbox instead of minting another. The
    // token is retained because ResumeSession re-authenticates: the gateway trusts the
    // credential, never the socket.
    //
    // LIMITATION, deliberately explicit: on the INLINE-LOGIN path we have no token to
    // retain — ClientSessionStarted carries address/keys/processUid/workerUid but no
    // minted token, so there is nothing to present on a resume. Such a connection records
    // its readerKey and resumes with an EMPTY token; if the gateway refuses that, the
    // reconnect surfaces the refusal rather than silently re-minting a second mailbox.
    // Fixing it properly means the gateway returning the token it minted (a proto change),
    // which belongs with the ClientSessionStart typed-proto work rather than here.
    rememberSession(
      readerKey = started.readerKey,
      authToken = authToken,
      subscriptions = subscriptions,
    )

    started
  }

  /**
   * Read the gateway's answer, handling the INLINE LOGIN challenge when it comes: the
   * gateway replies with a nonce, signing it proves possession of the key, and the session
   * continues on this same socket. One TLS handshake instead of three.
   */
  private def awaitSessionStarted(
    inlineLogin: Boolean,
    signNonce: Array[Byte] => Array[Byte],
  ): ClientSessionStarted = {
    receive(BootstrapTimeout) match {
      case None =>
        throw new RuntimeException(s"ws-native bootstrap: no reply within $BootstrapTimeout (is a mesh server running at $uri?)")
      case Some(msg) =>
        msg.message match {
          case MessageToClient.Message.ClientSessionStarted(started) =>
            logger.info(s"ws-native bootstrap: gateway minted mailbox ${started.address} for processrun ${started.processUid}")
            started

          case MessageToClient.Message.LoginChallenge(challenge) if inlineLogin =>
            val signature = signNonce(challenge.nonce.toByteArray)
            send(
              MessageFromClient(
                MessageFromClient.Message.LoginComplete(
                  LoginComplete(
                    sessionId = challenge.sessionId,
                    signature = ByteString.copyFrom(signature),
                  )
                )
              )
            )
            awaitSessionStarted(inlineLogin = false, signNonce = null)

          case MessageToClient.Message.HelloError(err) =>
            // A CODED refusal: the code is what a retry policy reads, the text is the
            // actual reason. Surface both.
            throw new RuntimeException(s"ws-native bootstrap refused by the gateway [${err.code}]: ${err.message}")

          case MessageToClient.Message.Notification(n) =>
            throw new RuntimeException(s"ws-native bootstrap refused by the gateway: ${n.message}")

          case other =>
            throw new RuntimeException(s"ws-native bootstrap: expected ClientSessionStarted, got ${other.getClass.getSimpleName}")
        }
    }
  }

}
