package a8.hermes.nats

import a8.hermes.core.MailboxTransport
import a8.hermes.core.MailboxTransport.*
import a8.shared.app.Ctx
import a8.shared.{CompanionGen, FileSystem}
import a8.shared.zreplace.{Resource, XStream}
import io.nats.client.{Connection, JetStream, JetStreamManagement, Message, Subscription as NatsSubscription}
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
class NatsTransport(val connection: Connection) extends MailboxTransport {

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
      case ConsumerConfig.Durable(name, _, _, _) =>
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
    val subscription = jetStream.subscribe(subject, options)

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
              msg.ack()
              lookahead = fromNatsMessage(msg)
            }
          }
          lookahead != null
        }

        override def next(): Envelope = {
          if (!hasNext) throw new NoSuchElementException("consumer subscription closed")
          val v = lookahead
          lookahead = null
          v
        }
      })
    } { sub =>
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
      case ConsumerConfig.Durable(_, dp, ap, _) => (dp, ap)
      case ConsumerConfig.Ephemeral(dp, ap) => (dp, ap)
    }

    builder.deliverPolicy(toNatsDeliverPolicy(deliverPolicy))
    builder.ackPolicy(toNatsAckPolicy(ackPolicy))

    // Self-reap an abandoned durable (each process run mints its own reader consumer);
    // see ConsumerConfig.Durable for why the threshold must outlast any survivable outage.
    config match {
      case ConsumerConfig.Durable(_, _, _, Some(threshold)) =>
        builder.inactiveThreshold(java.time.Duration.ofMillis(threshold.toMillis))
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
