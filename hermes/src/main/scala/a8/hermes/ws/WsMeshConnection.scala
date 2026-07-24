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
  Subscription,
}
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp

import java.net.URI
import java.net.http.{HttpClient, WebSocket}
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.{CompletionStage, LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
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
  // Binary frames can arrive in pieces; accumulate until the JDK says `last`.
  private val partial = new AtomicReference[Array[Byte]](Array.emptyByteArray)

  private object listener extends WebSocket.Listener {

    override def onOpen(webSocket: WebSocket): Unit = {
      logger.debug(s"ws open $uri")
      webSocket.request(1)
    }

    override def onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage[?] = {
      val chunk = new Array[Byte](data.remaining())
      data.get(chunk)
      val acc = partial.get() ++ chunk
      if (last) {
        partial.set(Array.emptyByteArray)
        try inbound.put(MessageToClient.parseFrom(acc))
        catch {
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

  /** Send one MessageFromClient as a binary frame. */
  def send(msg: MessageFromClient): Unit = {
    val ws = socketRef.get()
    if (ws == null) throw new IllegalStateException("ws not connected")
    val bytes = msg.toByteArray
    ws.sendBinary(ByteBuffer.wrap(bytes), true).get(30, TimeUnit.SECONDS)
    ()
  }

  /** Take the next inbound message, or None on timeout. */
  def receive(timeout: FiniteDuration): Option[MessageToClient] =
    Option(inbound.poll(timeout.toMillis, TimeUnit.MILLISECONDS))

  def close(): Unit = {
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

    awaitSessionStarted(inlineLogin, signNonce)
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
