package a8.hermes.ws

import a8.common.logging.{Level, Logging, LoggingBootstrapConfig}
import a8.hermes.auth.SshAuth
import a8.hermes.bootstrap.SimpleMailbox
import a8.hermes.core.Mailbox.*
import a8.hermes.nats.NatsTransport
import a8.hermes.proto.process.wsmessages as ws
import a8.shared.app.{AppCtx, Ctx}
import com.google.protobuf.ByteString

import java.time.Instant
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import scala.collection.mutable
import scala.concurrent.duration.*
import scala.io.StdIn

/**
 * The HERMES REFERENCE CLIENT for the cross-language WS conformance harness.
 *
 * A remote control for hermes's own WS client: godev's harness drives it over stdin/stdout
 * and it does exactly what it is told, reporting what it observed. It makes NO judgements —
 * dups=3 is a failure in one scenario and expected in another, and only the harness knows
 * which.
 *
 * Grown from WsBootstrapProbeMain, which proves the same protocol in one shot; this is that
 * probe turned into a command loop so an outage can be injected while it is mid-transaction.
 *
 * Contract: godev/docs/2026-07/20260726-wsconform-driver-contract.md
 *   → CAPABILITIES              ← WSC-OK transports=ws
 *   → CONNECT transport=ws      ← WSC-OK address=… readerKey=…
 *   → SEND n=… seed=…           ← WSC-OK sent=…
 *   → EXPECT n=… timeoutMs=…    ← WSC-OK received=… gaps=… dups=… reorders=…
 *   → REPORT                    ← WSC-OK reconnects=… resends=… remints=… recoveryMs=…
 *   → CLOSE                     ← WSC-OK
 *
 * STDOUT IS THE PROTOCOL. Every diagnostic goes to stderr, which the harness captures for
 * failure messages but never parses — hence overrideSystemOut=false below, without which the
 * logging subsystem would interleave lines into the contract stream and the harness would
 * read garbage.
 */
object WsConformClientMain extends Logging {

  private val CounterCorrelation = "wsconform-counter"

  private var conn: Option[WsMeshConnection] = None
  private var address: String = ""

  // --- direct-NATS transport state (the scala-sync-nats row) -----------------------------
  //
  // The data plane is hermes's REAL direct-NATS client — NatsTransport + SimpleMailbox with
  // its durable consumer (sync@66613ef) — through the harness's proxy. The mailbox is
  // minted over an UNPROXIED ws bootstrap first (protocol §6's bootstrap-only-WS shape):
  // the mint is control plane, not the thing under test, and the ws socket is closed the
  // moment the keys are in hand.
  private var natsConn: Option[io.nats.client.Connection] = None
  private var natsReader: Option[Thread] = None
  private var transportMode: String = "ws"
  private val natsReconnects = new AtomicLong(0)
  private val natsLastReconnectAtNanos = new AtomicLong(0)
  private val natsRecoveryMs = new AtomicLong(-1)

  // A minimal Ctx for this standalone remote-control process. The transport API threads a
  // Ctx, but this client's streams are plain iterator pulls (no ox concurrency) and there
  // is no app bootstrap to hang a real AppCtx off — the logging hazard in main() is exactly
  // why this binary must not run the full bootstrap.
  private object conformCtx extends Ctx with Ctx.InternalCtx {
    override def parent: Ctx = this
    override def parentOpt: Option[Ctx] = None
    override def ancestry: Iterator[Ctx] = Iterator(this)
    override def appCtx: AppCtx =
      throw new UnsupportedOperationException("wsconform client has no AppCtx")
  }

  // Observation state. The ws path is single-threaded by contract, but the NATS reader
  // fills the accumulator from its own thread, so all observe() calls take this lock.
  private val observeLock = new Object
  private val observed = mutable.Map[Int, Int]()
  private var received = 0
  private var dups = 0
  private var reorders = 0
  private var foreign = 0
  private var maxSeen = -1
  private var first = -1
  private var last = -1
  private var seed = 0

  def main(args: Array[String]): Unit = {
    LoggingBootstrapConfig.finalizeConfig(
      LoggingBootstrapConfig(
        overrideSystemErr = false,
        overrideSystemOut = false, // the WSC-* contract lines must reach stdout unmangled
        setDefaultUncaughtExceptionHandler = true,
        fileLogging = false,
        // MUST stay false. The shared ConsoleAppender defaults to Kind.Daemon when
        // hasColorConsole=false (ConsoleAppender.scala:24-30), and Daemon writes INFO to
        // STDOUT — the protocol stream — sending only WARN+ to stderr. Turning console
        // logging on therefore does not surface diagnostics; it pipes hermes's INFO logs into
        // the contract channel. The driver drops them as non-WSC- noise, but that is luck: a
        // log line containing "WSC-" would corrupt the protocol. Diagnostics go to
        // System.err directly instead — see diag() below.
        consoleLogging = false,
        hasColorConsole = false,
        appName = "wsconform-hermes",
        defaultLogLevel = Level.Info,
      )
    )

    var running = true
    while (running) {
      val line = Option(StdIn.readLine())
      line match {
        case None => running = false // stdin closed; harness went away
        case Some(raw) =>
          val trimmed = raw.trim
          if (trimmed.nonEmpty) running = dispatch(trimmed)
      }
    }
    System.exit(0)
  }

  /** @return false to stop the loop */
  private def dispatch(line: String): Boolean = {
    val parts = line.split("\\s+").toSeq
    val verb = parts.head
    val args = parts.tail.flatMap { f =>
      f.split("=", 2) match {
        case Array(k, v) => Some(k -> v)
        case _           => None
      }
    }.toMap

    verb match {
      case "CAPABILITIES" =>
        // hermes speaks BOTH transports in principle — WsMailbox over the socket, and the
        // bootstrap-only-WS-then-direct-NATS mode from protocol §6. Only the WS path is
        // implemented in this client today, so it declares only that. Claiming `nats` and
        // then failing CONNECT would produce a FAIL where the honest answer is "not
        // implemented", and a matrix that lies is worse than one with holes.
        //
        // knobs= declares the liveness timing this client ACCEPTS on CONNECT and applies
        // to the REAL client. Only silenceDeadlineMs: on ws hermes has no client-driven
        // ping (its detection is inbound-silence in receive()); on nats the deadline maps
        // onto the java client's OWN ping machinery (pingInterval = deadline/2,
        // maxPingsOut=2). Declaring pingIntervalMs would be a knob the ws path silently
        // ignores — the dishonesty the declaration exists to prevent.
        // FEATURE-20260802-wsconform-testbed-injected-timeouts.
        ok("transports=ws,nats knobs=silenceDeadlineMs")
        true
      case "CONNECT" => connect(args); true
      case "SEND"    => send(args); true
      case "EXPECT"  => expect(args); true
      case "REPORT"  => report(); true
      case "CLOSE" =>
        conn.foreach(c => try c.close() catch { case _: Throwable => () })
        natsConn.foreach(c => try c.close() catch { case _: Throwable => () })
        natsReader.foreach(t => try t.interrupt() catch { case _: Throwable => () })
        ok("")
        false
      case other =>
        err(s"unknown command $other")
        true
    }
  }

  private def connect(args: Map[String, String]): Unit = {
    args.getOrElse("transport", "ws") match {
      case "ws" =>
        try {
          val meshRootUrl = required("WSC_MESH_URL")
          val privateKey = required("WSC_SSH_KEY")
          val processUid = required("WSC_PROCESS_UID")
          val publicKey = SshAuth.readPublicKey(privateKey + ".pub")

          // Injected liveness, when the harness sent the knob this client declared —
          // applied to the REAL connection, so the production detection mechanism runs
          // at test scale rather than at its 5-minute default.
          val livenessTimeout =
            args.get("silenceDeadlineMs").flatMap(_.toLongOption).filter(_ > 0)
              .map(_.milliseconds)
              .getOrElse(WsMeshConnection.LivenessTimeout)

          val c = WsMeshConnection.connect(meshRootUrl, livenessTimeout)
          val started =
            c.bootstrap(
              authToken = "",
              processUid = processUid,
              appName = "wsconform-hermes",
              lifecycleKind = ws.MailboxLifecycle.SHORT_LIVED_CLI,
              channels = Seq("rpc-inbox"),
              subscriptions = Seq(
                ws.Subscription(
                  ws.Subscription.Oneof.Mailbox(
                    ws.MailboxSubscription(id = "rpc-inbox", channel = "rpc-inbox", startSeq = "first")
                  )
                )
              ),
              sshPublicKey = publicKey,
              sshOrigin = "wsconform",
              signNonce = nonce => SshAuth.signNonce(nonce, privateKey),
            )
          conn = Some(c)
          address = started.address
          ok(s"address=${started.address} readerKey=${started.readerKey}")
        } catch {
          case e: Throwable => err(s"${e.getClass.getSimpleName}: ${e.getMessage}")
        }
      case "nats" =>
        try {
          val meshRootUrl = required("WSC_MESH_URL") // DIRECT gateway — control plane, unproxied
          val natsUrl = required("WSC_NATS_URL")     // PROXIED nats — the data plane under test
          val privateKey = required("WSC_SSH_KEY")
          val processUid = required("WSC_PROCESS_UID")
          val publicKey = SshAuth.readPublicKey(privateKey + ".pub")

          // 1. CONTROL PLANE: mint the mailbox over an unproxied ws hello with NO
          // subscriptions, and close the socket the moment the keys are in hand. The mint
          // also creates the channel streams server-side; the mailbox itself is durable.
          val wsConn = WsMeshConnection.connect(meshRootUrl)
          val started =
            try
              wsConn.bootstrap(
                authToken = "",
                processUid = processUid,
                appName = "wsconform-hermes-nats",
                lifecycleKind = ws.MailboxLifecycle.SHORT_LIVED_CLI,
                channels = Seq("rpc-inbox"),
                subscriptions = Seq.empty,
                sshPublicKey = publicKey,
                sshOrigin = "wsconform",
                signNonce = nonce => SshAuth.signNonce(nonce, privateKey),
              )
            finally wsConn.close()

          // 2. DATA PLANE through the proxy. Reconnect settings mirror production
          // (NatsTransport.scala: reconnectWait 2s); the injected silence deadline maps
          // onto the java client's own ping machinery so half-open is detectable inside a
          // test-scale window.
          val silenceDeadlineMs =
            args.get("silenceDeadlineMs").flatMap(_.toLongOption).filter(_ > 0)
          val optionsBuilder =
            io.nats.client.Options.builder()
              .server(natsUrl)
              .maxReconnects(-1) // the SCENARIO decides when giving up matters; the window bounds the run
              .reconnectWait(java.time.Duration.ofSeconds(2))
              .connectionListener((_, event) => {
                if (event == io.nats.client.ConnectionListener.Events.RECONNECTED) {
                  natsReconnects.incrementAndGet()
                  natsLastReconnectAtNanos.set(System.nanoTime())
                  ()
                }
              })
          silenceDeadlineMs.foreach { ms =>
            optionsBuilder
              .pingInterval(java.time.Duration.ofMillis(math.max(ms / 2, 250)))
              .maxPingsOut(2)
            ()
          }
          val connection = io.nats.client.Nats.connect(optionsBuilder.build())
          val transport = new NatsTransport(connection)

          val now = Instant.now()
          val metadata =
            MailboxMetadata(
              adminKey = AdminKey(started.adminKey),
              readerKey = ReaderKey(started.readerKey),
              address = MailboxAddress(started.address),
              lifecycle = LifecycleType.Ephemeral,
              createdAt = now,
              expiresAt = now.plusSeconds(LifecycleType.Ephemeral.ttl.toSeconds),
              lastAccessedAt = now,
            )
          val mbox = new SimpleMailbox(metadata, transport)

          // 3. The reader: hermes's REAL subscribe path — the durable JetStream consumer
          // (sync@66613ef) whose server-side position is what survives the outage.
          val reader = new Thread(() => {
            try
              mbox
                // DeliverPolicy.All, not New: this mailbox was minted moments ago, so All
                // means exactly "everything this run sends" — and it closes the bind-window
                // race where the first ~48 sends landed before the consumer existed and
                // were never delivered (New only delivers what arrives after binding).
                .subscribe(Channel.RpcInbox, a8.hermes.core.MailboxTransport.DeliverPolicy.All)(using conformCtx)
                .runForeach { mm =>
                  val nowNanos = System.nanoTime()
                  val since = natsLastReconnectAtNanos.getAndSet(0)
                  if (since > 0) natsRecoveryMs.set((nowNanos - since) / 1000000L)
                  observeLock.synchronized {
                    observe(new String(mm.payload, java.nio.charset.StandardCharsets.UTF_8))
                  }
                }(using conformCtx)
            catch {
              case e: Throwable => diag(s"wsconform: nats reader ended: ${e.getMessage}")
            }
          })
          reader.setDaemon(true)
          reader.setName("wsconform-nats-reader")
          reader.start()

          natsConn = Some(connection)
          natsReader = Some(reader)
          natsMailboxRef = Some(mbox)
          address = started.address
          transportMode = "nats"
          ok(s"address=${started.address} readerKey=${started.readerKey}")
        } catch {
          case e: Throwable => err(s"${e.getClass.getSimpleName}: ${e.getMessage}")
        }

      case other =>
        err(s"transport $other not implemented by this client (CAPABILITIES says ws,nats)")
    }
  }

  private var natsMailboxRef: Option[SimpleMailbox] = None

  // The direct-NATS send: hermes's REAL publish path (SimpleMailbox.send -> core publish to
  // mesh.<address>.rpc-inbox, captured by the channel stream). Failures are reported, not
  // fatal, for the same reason as the ws branch — the outage severing the path mid-SEND is
  // the scenario, and the java client's own reconnect buffering is part of what is measured.
  private def sendNats(args: Map[String, String], mbox: SimpleMailbox): Unit = {
    val n = intArg(args, "n", 0)
    seed = intArg(args, "seed", 0)
    val sent = new AtomicInteger(0)
    var i = 0
    while (i < n) {
      try {
        mbox.send(
          to = MailboxAddress(address),
          message = MailboxMessage(
            correlationId = CounterCorrelation,
            fromMailbox = MailboxAddress(address),
            endpoint = "wsconform.counter",
            contentType = ContentType.Protobuf,
            payload = payload(seed, i).getBytes(java.nio.charset.StandardCharsets.UTF_8),
          ),
        )(using conformCtx)
        sent.incrementAndGet()
        ()
      } catch {
        case e: Throwable => diag(s"wsconform: nats send $i failed: ${e.getMessage}")
      }
      i += 1
    }
    ok(s"sent=${sent.get()}")
  }

  private def send(args: Map[String, String]): Unit =
    if (transportMode == "nats") {
      natsMailboxRef match {
        case None       => err("SEND before CONNECT")
        case Some(mbox) => sendNats(args, mbox)
      }
    } else
    conn match {
      case None => err("SEND before CONNECT")
      case Some(c) =>
        val n = intArg(args, "n", 0)
        seed = intArg(args, "seed", 0)
        val sent = new AtomicInteger(0)
        var i = 0
        while (i < n) {
          val frame =
            ws.MessageFromClient(
              ws.MessageFromClient.Message.SendMessageRequest(
                ws.SendMessageRequest(
                  to = Seq(address),
                  channel = "rpc-inbox",
                  // PER-MESSAGE key. Without it the transport falls back to correlationId,
                  // which is per CONVERSATION — and this run sends every message under ONE
                  // correlation, so 20,000 sends collapse to a single in-flight entry and
                  // exactly one can ever be resent. It is also what the stream's dedup window
                  // matches on, so a resend that did land is dropped rather than duplicated.
                  idempotentId = java.util.UUID.randomUUID().toString,
                  message = Some(
                    ws.Message(
                      header = Some(
                        ws.MessageHeader(
                          sender = address,
                          rpcHeader = Some(
                            ws.RpcHeader(correlationId = CounterCorrelation, endPoint = "wsconform.counter")
                          ),
                        )
                      ),
                      data = ByteString.copyFromUtf8(payload(seed, i)),
                    )
                  ),
                )
              )
            )
          // A send failure is REPORTED, not fatal: severing the socket mid-SEND is the whole
          // point of kill-mid-request, and a client that exited there would be measuring its
          // own exit rather than the transport's recovery.
          try {
            c.send(frame)
            sent.incrementAndGet()
            ()
          } catch {
            case e: Throwable => diag(s"wsconform: send $i failed: ${e.getMessage}")
          }
          i += 1
        }
        ok(s"sent=${sent.get()}")
    }

  // The direct-NATS EXPECT: the reader THREAD fills the accumulator (durable-consumer
  // deliveries), so this just waits on it. All accumulator access is under observeLock.
  private def expectNats(args: Map[String, String]): Unit = {
    val want = intArg(args, "n", 0)
    val timeoutMs = intArg(args, "timeoutMs", 30000).toLong
    val deadline = System.currentTimeMillis() + timeoutMs
    var timedOut = false

    while (!timedOut && observeLock.synchronized(observed.size) < want) {
      if (System.currentTimeMillis() >= deadline) timedOut = true
      else Thread.sleep(100)
    }

    val line = observeLock.synchronized {
      val gaps = (0 until want).count(i => !observed.contains(i))
      val base = s"received=$received gaps=$gaps dups=$dups reorders=$reorders first=$first last=$last"
      val withForeign = if (foreign > 0) s"$base foreign=$foreign" else base
      if (timedOut || gaps > 0)
        diag(s"wsconform: nats EXPECT ended want=$want $withForeign " +
          s"reconnects=${natsReconnects.get()} recoveryMs=${natsRecoveryMs.get()}")
      withForeign
    }
    ok(if (timedOut) s"$line timedOut=true" else line)
  }

  private def expect(args: Map[String, String]): Unit =
    if (transportMode == "nats") expectNats(args)
    else
    conn match {
      case None => err("EXPECT before CONNECT")
      case Some(c) =>
        val want = intArg(args, "n", 0)
        val timeoutMs = intArg(args, "timeoutMs", 30000).toLong
        val deadline = System.currentTimeMillis() + timeoutMs
        var timedOut = false

        while (observed.size < want && !timedOut) {
          val remaining = deadline - System.currentTimeMillis()
          if (remaining <= 0) timedOut = true
          else
            c.receive(remaining.millis.min(2.seconds)) match {
              case Some(m2c) => observeEnvelope(m2c)
              case None      => if (System.currentTimeMillis() >= deadline) timedOut = true
            }
        }

        val gaps = (0 until want).count(i => !observed.contains(i))
        val base = s"received=$received gaps=$gaps dups=$dups reorders=$reorders first=$first last=$last"
        val withForeign = if (foreign > 0) s"$base foreign=$foreign" else base

        // The client's own account of what recovery did, on the stream the harness keeps for
        // failure tails. Without this a failing cell shows only the harness's view of the
        // numbers, and which reconnect branch ran has to be inferred from gateway logs —
        // exactly the blind spot that cost a diagnostic pass on this suite.
        if (timedOut || gaps > 0)
          diag(s"wsconform: EXPECT ended want=$want $withForeign " +
            s"reconnects=${c.reconnects} resends=${c.resends} recoveryMs=${c.recoveryMs} staleDrops=${c.staleDrops}")

        ok(if (timedOut) s"$withForeign timedOut=true" else withForeign)
    }

  private def observeEnvelope(m2c: ws.MessageToClient): Unit =
    m2c.message match {
      case ws.MessageToClient.Message.MessageEnvelope(env) =>
        try {
          val inner = ws.Message.parseFrom(env.messageBytes.toByteArray)
          observeLock.synchronized(observe(inner.data.toStringUtf8))
        } catch { case _: Throwable => () }
      case _ => () // pings, notifications, subscribe responses — not this stream's business
    }

  private def observe(data: String): Unit =
    parsePayload(data) match {
      case None => ()
      case Some((s, index)) =>
        if (s != seed) foreign += 1
        else {
          received += 1
          val prior = observed.getOrElse(index, 0)
          observed.update(index, prior + 1)
          if (prior > 0) dups += 1
          else {
            // A replayed OLD index is a dup, NOT also a reorder — double-counting would make
            // every startSeq="first" replay look like the transport was shuffling messages.
            if (maxSeen >= 0 && index < maxSeen) reorders += 1
            if (index > maxSeen) maxSeen = index
          }
          if (first < 0 || index < first) first = index
          if (index > last) last = index
        }
    }

  private def report(): Unit =
    if (transportMode == "nats") {
      // reconnects/recoveryMs from the java client's own ConnectionListener; resends is -1
      // because the outbound leg is the java client's reconnect buffer, which it does not
      // count — claiming 0 would assert "nothing needed resending", which we cannot know.
      ok(s"reconnects=${natsReconnects.get()} resends=-1 remints=-1 recoveryMs=${natsRecoveryMs.get()}")
    } else
    conn match {
      case None =>
        ok("reconnects=-1 resends=-1 remints=-1 recoveryMs=-1")
      case Some(c) =>
        // remints is -1, not 0: hermes has NO re-mint branch at all (it resumes, by design),
        // so it does not track one. Reporting 0 would claim "we tried and never re-minted",
        // which is a different and stronger statement than "this client cannot re-mint".
        ok(s"reconnects=${c.reconnects} resends=${c.resends} remints=-1 recoveryMs=${c.recoveryMs}")
    }

  // --- payload ---------------------------------------------------------------
  //
  // Must match wsconform.Payload and the Go client byte for byte, in every language.

  private def payload(seed: Int, index: Int): String = s"wsc:$seed:$index"

  private def parsePayload(s: String): Option[(Int, Int)] =
    s.trim.split(":") match {
      case Array("wsc", a, b) => a.toIntOption.zip(b.toIntOption)
      case _                  => None
    }

  // --- protocol plumbing -----------------------------------------------------

  private def ok(body: String): Unit = {
    if (body.isEmpty) println("WSC-OK") else println(s"WSC-OK $body")
    System.out.flush()
  }

  private def err(message: String): Unit = {
    println(s"WSC-ERR message=$message")
    System.out.flush()
  }

  /**
   * Diagnostics for the harness's failure tail. STDERR ONLY, and written directly rather than
   * through the logger: the shared ConsoleAppender's Daemon default would put INFO on stdout,
   * which is the protocol stream (see the consoleLogging note in main).
   */
  private def diag(message: String): Unit = {
    System.err.println(message)
    System.err.flush()
  }

  private def intArg(args: Map[String, String], key: String, default: Int): Int =
    args.get(key).flatMap(_.toIntOption).getOrElse(default)

  private def required(name: String): String =
    sys.env.getOrElse(name, throw new IllegalStateException(s"$name is not set"))

}
