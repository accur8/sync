package a8.hermes.nats

import a8.hermes.core.MailboxTransport
import a8.hermes.core.MailboxTransport.*
import a8.shared.app.Ctx
import a8.shared.CompanionGen
import a8.shared.zreplace.{Resource, XStream}
import io.nats.client.{Connection, JetStream, JetStreamManagement, Message}
import io.nats.client.api.{ConsumerConfiguration, StreamConfiguration, RetentionPolicy, AckPolicy as NatsAckPolicy, DeliverPolicy as NatsDeliverPolicy, StorageType}
import io.nats.client.impl.{Headers as NatsHeaders}

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*
import scala.jdk.DurationConverters.*

object NatsTransport {

  @CompanionGen
  case class Config(
    natsUrl: String,
    username: Option[String] = None,
    password: Option[String] = None,
    token: Option[String] = None,
    connectionName: Option[String] = None,
    maxReconnects: Int = 60,
    reconnectWait: FiniteDuration = scala.concurrent.duration.FiniteDuration(2, "seconds"),
    connectionTimeout: FiniteDuration = scala.concurrent.duration.FiniteDuration(5, "seconds"),
    appName: Option[String] = None, // For auto-generating connection names
  ) {
    /**
     * Generate connection name in format: user@hostname:appname
     * Used when connectionName is None but appName is provided
     */
    def effectiveConnectionName: Option[String] =
      connectionName.orElse(appName.map { app =>
        val username = System.getProperty("user.name", "unknown")
        val hostname = try {
          java.net.InetAddress.getLocalHost.getHostName
        } catch {
          case _: Exception => "unknown"
        }
        s"$username@$hostname:$app"
      })
  }

  /**
   * Load configuration from environment variables or system properties
   */
  def fromEnv(appName: String): Config = {
    val url = sys.env.getOrElse("NATS_URL",
      sys.props.getOrElse("nats.url", "nats://localhost:4222"))
    val username = sys.env.get("NATS_USER")
      .orElse(sys.props.get("nats.user"))
      .filter(_.nonEmpty)
    val password = sys.env.get("NATS_PASSWORD")
      .orElse(sys.props.get("nats.password"))
      .filter(_.nonEmpty)

    Config(
      natsUrl = url,
      username = username,
      password = password,
      appName = Some(appName)
    )
  }

  /**
   * Configuration for glen-starbak.accur8.net
   */
  def starbak(appName: String): Config =
    Config(
      natsUrl = "nats://glen-starbak.accur8.net:4222",
      appName = Some(appName)
    )

  /**
   * Create a NatsTransport resource from config
   */
  def resource(config: Config): Resource[NatsTransport] = {
    Resource.acquireRelease {
      val options = buildNatsOptions(config)
      val connection = io.nats.client.Nats.connect(options)
      new NatsTransport(connection)
    } { transport =>
      transport.close()
    }
  }

  /**
   * Create a daemon thread factory for NATS executor.
   *
   * By default, NATS Java client creates non-daemon threads which prevent
   * JVM shutdown. We configure a custom executor that creates daemon threads
   * so the app can shut down cleanly even if NATS connections aren't explicitly closed.
   */
  private def createDaemonThreadFactory(): java.util.concurrent.ThreadFactory = {
    new java.util.concurrent.ThreadFactory {
      private val counter = new java.util.concurrent.atomic.AtomicInteger(0)
      private val defaultFactory = java.util.concurrent.Executors.defaultThreadFactory()

      override def newThread(r: Runnable): Thread = {
        val thread = defaultFactory.newThread(r)
        thread.setDaemon(true)  // Make all NATS threads daemon threads
        thread.setName(s"nats-daemon-${counter.incrementAndGet()}")
        thread
      }
    }
  }

  private def buildNatsOptions(config: Config): io.nats.client.Options = {
    // Create executor with daemon threads
    val daemonExecutor = java.util.concurrent.Executors.newCachedThreadPool(createDaemonThreadFactory())

    val builder = new io.nats.client.Options.Builder()
      .server(config.natsUrl)
      .maxReconnects(config.maxReconnects)
      .reconnectWait(config.reconnectWait.toJava)
      .connectionTimeout(config.connectionTimeout.toJava)
      .executor(daemonExecutor)  // Use daemon thread executor

    config.username.zip(config.password).foreach { case (u, p) =>
      builder.userInfo(u, p)
    }

    config.token.foreach(t => builder.token(t.toCharArray))
    config.effectiveConnectionName.foreach(builder.connectionName)

    builder.build()
  }

}

/**
 * NATS implementation of MailboxTransport
 */
class NatsTransport(val connection: Connection) extends MailboxTransport with a8.common.logging.Logging {

  lazy val jetStream: JetStream = connection.jetStream()
  lazy val jetStreamManagement: JetStreamManagement = connection.jetStreamManagement()

  override def publish(
    subject: String,
    headers: Map[String, String],
    payload: Array[Byte],
  )(using Ctx): Unit = {
    val natsHeaders = toNatsHeaders(headers)
    val msg = io.nats.client.impl.NatsMessage.builder()
      .subject(subject)
      .headers(natsHeaders)
      .data(payload)
      .build()
    connection.publish(msg)
    connection.flush(java.time.Duration.ofMillis(100))  // Ensure message is sent immediately
  }

  // --- acked publish (invariant 3's outbound leg) ----------------------------------------
  //
  // Bounded outstanding window: backpressure, not memory. A sender that outruns the acks
  // blocks here until the window drains — which during an outage is exactly the brake that
  // keeps the unacked tail small.
  // Counters, not logs — the same doctrine godev's client earned on
  // BUG-20260731-godev-no-recovery-from-half-open-socket: under outage load, log lines
  // drop and their absence gets misread. These four numbers say exactly where an acked
  // publish's story ended, and the conformance driver prints them with its verdict
  // (BUG-20260802-sync-nats-kill-loses-inflight-publishes).
  // Delivery-side instrumentation for
  // BUG-20260803-hermes-nats-partition-heal-rare-message-loss.
  //
  // The iterator keeps a one-message LOOKAHEAD: pulled from the server, not yet handed to
  // the app, deliberately unacked. When a stream ends mid-flight that envelope is
  // discarded. Unacked SHOULD mean redelivery at ackWait, so this is not assumed to be a
  // defect — it is the quantity that decides whether it is one, and nothing counted it.
  //
  // consumerBinds counts how many times a consumer was created for a subject; a durable
  // being REBOUND and a durable being RECREATED look identical in a log line otherwise,
  // and they have opposite consequences for in-flight unacked messages.
  val lookaheadDropped = new java.util.concurrent.atomic.AtomicLong(0)
  val consumerBinds = new java.util.concurrent.atomic.AtomicLong(0)

  val ackedPublishOk = new java.util.concurrent.atomic.AtomicLong(0)
  val ackedPublishRetries = new java.util.concurrent.atomic.AtomicLong(0)
  val ackedPublishGaveUp = new java.util.concurrent.atomic.AtomicLong(0)

  private val ackedWindowPermits = 1024
  private val ackedWindow = new java.util.concurrent.Semaphore(ackedWindowPermits)

  /** Publishes still in the retry pipeline (unacked, not yet given up). */
  def ackedPublishOutstanding: Int = ackedWindowPermits - ackedWindow.availablePermits()
  private val ackedRetryExec = {
    java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r =>
      val t = new Thread(r, "nats-acked-publish-retry")
      t.setDaemon(true)
      t
    }
  }

  /**
   * Publish through JetStream with a Nats-Msg-Id, and REPUBLISH until the stream acks —
   * the fix for the fire-and-forget loss (a message flushed into a socket the outage
   * kills has no ack to notice it is gone). The msg-id makes every republish safe: the
   * stream's duplicate window drops a resend whose original landed. Async — one
   * outstanding window, ack-driven — because 20k sequential ack round-trips would
   * measure latency, not deliver messages. Gives up loudly after ~2.5 minutes of
   * retries; by then the outage has outlived the java client's own reconnect story.
   */
  override def publishAcked(
    subject: String,
    headers: Map[String, String],
    payload: Array[Byte],
    msgId: String,
  )(using Ctx): Unit = {
    ackedWindow.acquire()
    publishAckedAttempt(subject, headers, payload, msgId, attempt = 1)
  }

  private def publishAckedAttempt(
    subject: String,
    headers: Map[String, String],
    payload: Array[Byte],
    msgId: String,
    attempt: Int,
  ): Unit = {
    val maxAttempts = 30
    def backoffMs(n: Int): Long = math.min(250L * (1L << math.min(n - 1, 5)), 2000L)
    def retryOrGiveUp(reason: => String): Unit = {
      if (attempt >= maxAttempts) {
        ackedWindow.release()
        ackedPublishGaveUp.incrementAndGet()
        logger.warn(s"acked publish GAVE UP after $attempt attempts msgId=$msgId: $reason")
      } else {
        ackedPublishRetries.incrementAndGet()
        ackedRetryExec.schedule(
          new Runnable {
            override def run(): Unit =
              publishAckedAttempt(subject, headers, payload, msgId, attempt + 1)
          },
          backoffMs(attempt),
          java.util.concurrent.TimeUnit.MILLISECONDS,
        )
        ()
      }
    }

    try {
      val natsHeaders = toNatsHeaders(headers)
      natsHeaders.put("Nats-Msg-Id", msgId)
      val msg = io.nats.client.impl.NatsMessage.builder()
        .subject(subject)
        .headers(natsHeaders)
        .data(payload)
        .build()
      jetStream
        .publishAsync(msg)
        // OUR OWN deadline on the ack. An ask written into a socket the outage KILLED
        // never gets a reply and jnats never completes the future — no failure, no
        // retry, message silently gone (measured: gaps=40 on kill-mid-request while
        // half-open, whose asks were buffered rather than written into a corpse,
        // passed). orTimeout turns the hang into the failure the retry path handles.
        .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
        .whenComplete { (_, err) =>
          if (err == null) {
            ackedWindow.release()
            ackedPublishOk.incrementAndGet()
            ()
          } else retryOrGiveUp(err.getMessage)
        }
      ()
    } catch {
      // publishAsync can throw synchronously (reconnect buffer full, connection state) —
      // the same retry path applies.
      case e: Exception => retryOrGiveUp(e.getMessage)
    }
  }

  /**
   * Publish to JetStream with headers
   * Returns the PublishAck for sequence tracking
   */
  def publishToJetStream(
    subject: String,
    headers: Map[String, String],
    payload: Array[Byte],
  )(using Ctx): io.nats.client.api.PublishAck = {
    val natsHeaders = toNatsHeaders(headers)
    val msg = io.nats.client.impl.NatsMessage(subject, null, natsHeaders, payload)
    jetStream.publish(msg)
  }

  override def request(
    subject: String,
    headers: Map[String, String],
    payload: Array[Byte],
    timeout: FiniteDuration,
  )(using Ctx): Option[Envelope] = {
    val natsHeaders = toNatsHeaders(headers)
    val msg = io.nats.client.impl.NatsMessage.builder()
      .subject(subject)
      .headers(natsHeaders)
      .data(payload)
      .build()

    try {
      val response = connection.request(msg, timeout.toJava)
      Option(response).map(fromNatsMessage)
    } catch {
      case _: java.util.concurrent.TimeoutException => None
    }
  }

  override def subscribe(
    subject: String,
    queueGroup: Option[String] = None,
  )(using Ctx): XStream[Envelope] = {
    // Use a queue to buffer messages between callback and iterator
    val messageQueue = new java.util.concurrent.LinkedBlockingQueue[Envelope]()
    val active = new java.util.concurrent.atomic.AtomicBoolean(true)

    val messageHandler: io.nats.client.MessageHandler = msg => {
      messageQueue.offer(fromNatsMessage(msg))
      // Auto-ack JetStream messages to prevent redelivery
      if (msg.isJetStream) {
        msg.ack()
      }
    }

    val dispatcher = connection.createDispatcher(messageHandler)
    queueGroup match {
      case Some(group) => dispatcher.subscribe(subject, group)
      case None => dispatcher.subscribe(subject)
    }

    // Create stream from the queue
    XStream.acquireRelease {
      (dispatcher, new Iterator[Envelope] {
        override def hasNext: Boolean = active.get()

        override def next(): Envelope = {
          // Poll with timeout to allow checking if subscription is still active
          val msg = messageQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
          if (msg != null) msg
          else if (active.get()) next() // Try again if still active
          else throw new NoSuchElementException("Subscription closed")
        }
      })
    } { _ =>
      active.set(false)
      dispatcher.unsubscribe(subject)
    }
  }

  override def createConsumer(
    subject: String,
    config: ConsumerConfig,
  )(using Ctx): XStream[Envelope] = {
    val consumerConfig = toNatsConsumerConfig(config)
    val options = config match {
      case ConsumerConfig.Durable(name, _, _, _, _) =>
        io.nats.client.PushSubscribeOptions.builder()
          .durable(name)
          .configuration(consumerConfig)
          .build()
      case ConsumerConfig.Ephemeral(_, _) =>
        io.nats.client.PushSubscribeOptions.builder()
          .configuration(consumerConfig)
          .build()
    }
    // No .stream(): the js client resolves the stream FROM the subject, so this
    // transport never re-derives mesh-<kind>-<readerKey>-<channel> and cannot drift
    // from the server's naming. (The previous, never-called version of this method
    // passed a stream NAME where the subject belongs — it could not have worked.)
    //
    // The subscribe is EAGER — it runs when createConsumer is CALLED, deliberately
    // outside the XStream acquire. CONTRACT: when createConsumer returns, the
    // consumer EXISTS and a DeliverPolicy.New reader misses nothing published after
    // this point. Callers must therefore invoke createConsumer on the thread that
    // needs that guarantee, BEFORE signalling readiness — calling it inside a
    // background fork reopens the bind window in which anything published is skipped
    // forever (BUG-20260802-hermes-first-rpc-on-fresh-client-times-out: the first
    // LoginBegin's reply landed in exactly that window on 3/3 checkpoint boots; same
    // physics as the 48-of-20000 bind-window loss in SimpleMailbox.subscribe's doc).
    val subscription = jetStream.subscribe(subject, options)

    consumerBinds.incrementAndGet()

    // Set while the iterator holds a pulled-but-undelivered envelope. The release hook
    // only receives the subscription, so this is how it learns the stream is ending with
    // a message the app never saw.
    val holdingLookahead = new java.util.concurrent.atomic.AtomicBoolean(false)

    XStream.acquireRelease {
      (subscription, new Iterator[Envelope] {
        // Lookahead pull: BLOCK (in poll slices, so unsubscribe is noticed) until a
        // message arrives or the subscription closes. The previous iterator threw
        // NoSuchElementException on any 100ms lull, ending the stream the moment the
        // mailbox went quiet.
        private var lookahead: Envelope = null

        override def hasNext: Boolean = {
          while (lookahead == null && subscription.isActive) {
            val msg = subscription.nextMessage(java.time.Duration.ofMillis(500))
            if (msg != null) {
              // NO ack here. Acking at the pull (ack-before-deliver) let stream prefetch
              // ack whole chunks the consumer had not processed; a connection death then
              // dropped them acked-but-unseen, unredeliverable — the contiguous-hole loss.
              // The envelope carries the ack; the CONSUMER fires it after processing.
              val ackThunk: () => Unit = () => msg.ack()
              lookahead = fromNatsMessage(msg).copy(ack = ackThunk)
              holdingLookahead.set(true)
            }
          }
          lookahead != null
        }

        override def next(): Envelope = {
          if (!hasNext) throw new NoSuchElementException("consumer subscription closed")
          val v = lookahead
          lookahead = null
          holdingLookahead.set(false)
          v
        }
      })
    } { sub =>
      // A stream ending while the iterator still holds a pulled envelope means the app
      // never saw that message. It was deliberately NOT acked, so the durable should
      // redeliver it at ackWait — this counts the event rather than assuming either way.
      // BUG-20260803-hermes-nats-partition-heal-rare-message-loss.
      if (holdingLookahead.getAndSet(false)) {
        val n = lookaheadDropped.incrementAndGet()
        logger.warn(s"consumer stream ended holding an undelivered message (total=$n) subject=$subject")
      }
      // Stops THIS subscriber's interest. A DURABLE's server-side position survives
      // (that is the point); the abandoned consumer itself is reaped by its
      // inactiveThreshold rather than deleted here.
      sub.unsubscribe()
    }
  }

  override def createStream(
    name: String,
    subjects: Seq[String],
    retention: StreamRetention,
    maxAge: FiniteDuration,
  )(using Ctx): Unit = {
    val config = StreamConfiguration.builder()
      .name(name)
      .subjects(subjects.asJava)
      .retentionPolicy(toNatsRetentionPolicy(retention))
      .maxAge(maxAge.toJava)
      .storageType(StorageType.File)
      .build()

    try {
      jetStreamManagement.addStream(config)
    } catch {
      case e: io.nats.client.JetStreamApiException if e.getMessage.contains("stream name already in use") =>
        // Stream already exists, that's fine
        ()
    }
  }

  override def streamExists(name: String)(using Ctx): Boolean = {
    try {
      jetStreamManagement.getStreamInfo(name)
      true
    } catch {
      case _: io.nats.client.JetStreamApiException => false
    }
  }

  override def streamInfo(name: String)(using Ctx): Option[StreamInfo] = {
    try {
      val info = jetStreamManagement.getStreamInfo(name)
      Some(StreamInfo(
        name = info.getConfiguration.getName,
        subjects = info.getConfiguration.getSubjects.asScala.toSeq,
        messageCount = info.getStreamState.getMsgCount,
        byteCount = info.getStreamState.getByteCount,
      ))
    } catch {
      case _: io.nats.client.JetStreamApiException => None
    }
  }

  /**
   * Delete a stream
   */
  def deleteStream(name: String)(using Ctx): Boolean = {
    try {
      jetStreamManagement.deleteStream(name)
      true
    } catch {
      case _: io.nats.client.JetStreamApiException => false
    }
  }

  def close(): Unit = {
    connection.close()
  }

  // Helper methods for converting between NATS and our types

  private def toNatsHeaders(headers: Map[String, String]): NatsHeaders = {
    val natsHeaders = new NatsHeaders()
    headers.foreach { case (k, v) => natsHeaders.add(k, v) }
    natsHeaders
  }

  private def fromNatsHeaders(headers: NatsHeaders): Map[String, String] = {
    if (headers == null) Map.empty
    else {
      headers.keySet().asScala.map { key =>
        key -> headers.getFirst(key)
      }.toMap
    }
  }

  private def fromNatsMessage(msg: Message): Envelope = {
    Envelope(
      subject = msg.getSubject,
      headers = fromNatsHeaders(msg.getHeaders),
      payload = msg.getData,
      replyTo = Option(msg.getReplyTo),
    )
  }

  private def toNatsConsumerConfig(config: ConsumerConfig): ConsumerConfiguration = {
    val builder = ConsumerConfiguration.builder()

    val (deliverPolicy, ackPolicy) = config match {
      case ConsumerConfig.Durable(_, dp, ap, _, _) => (dp, ap)
      case ConsumerConfig.Ephemeral(dp, ap) => (dp, ap)
    }

    builder.deliverPolicy(toNatsDeliverPolicy(deliverPolicy))
    builder.ackPolicy(toNatsAckPolicy(ackPolicy))

    // Self-reap an abandoned durable (each process run mints its own reader consumer);
    // see ConsumerConfig.Durable for why the threshold must outlast any survivable outage.
    config match {
      case ConsumerConfig.Durable(_, _, _, Some(threshold), _) =>
        builder.inactiveThreshold(java.time.Duration.ofMillis(threshold.toMillis))
        ()
      case _ => ()
    }

    // The redelivery clock for outstanding-unacked deliveries — the void-window fix;
    // see ConsumerConfig.Durable.ackWait for the mechanism and the measurement.
    config match {
      case ConsumerConfig.Durable(_, _, _, _, Some(wait)) =>
        builder.ackWait(java.time.Duration.ofMillis(wait.toMillis))
        ()
      case _ => ()
    }

    // Set start sequence if using ByStartSequence policy
    deliverPolicy match {
      case DeliverPolicy.ByStartSequence(seq) => builder.startSequence(seq)
      case _ => ()
    }

    builder.build()
  }

  private def toNatsDeliverPolicy(policy: DeliverPolicy): NatsDeliverPolicy = policy match {
    case DeliverPolicy.All => NatsDeliverPolicy.All
    case DeliverPolicy.New => NatsDeliverPolicy.New
    case DeliverPolicy.Last => NatsDeliverPolicy.Last
    case DeliverPolicy.ByStartSequence(_) => NatsDeliverPolicy.ByStartSequence
  }

  private def toNatsAckPolicy(policy: AckPolicy): NatsAckPolicy = policy match {
    case AckPolicy.None => NatsAckPolicy.None
    case AckPolicy.All => NatsAckPolicy.All
    case AckPolicy.Explicit => NatsAckPolicy.Explicit
  }

  private def toNatsRetentionPolicy(retention: StreamRetention): RetentionPolicy = retention match {
    case StreamRetention.Limits => RetentionPolicy.Limits
    case StreamRetention.Interest => RetentionPolicy.Interest
    case StreamRetention.WorkQueue => RetentionPolicy.WorkQueue
  }

}
