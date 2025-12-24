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

  private def buildNatsOptions(config: Config): io.nats.client.Options = {
    val builder = new io.nats.client.Options.Builder()
      .server(config.natsUrl)
      .maxReconnects(config.maxReconnects)
      .reconnectWait(config.reconnectWait.toJava)

    config.username.zip(config.password).foreach { case (u, p) =>
      builder.userInfo(u, p)
    }

    config.token.foreach(builder.token)
    config.connectionName.foreach(builder.connectionName)

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
    streamName: String,
    config: ConsumerConfig,
  )(using Ctx): XStream[Envelope] = {
    val consumerConfig = toNatsConsumerConfig(config)
    val subscription = jetStream.subscribe(
      streamName,
      config match {
        case ConsumerConfig.Durable(name, _, _) =>
          io.nats.client.PushSubscribeOptions.builder()
            .stream(streamName)
            .durable(name)
            .configuration(consumerConfig)
            .build()
        case ConsumerConfig.Ephemeral(_, _) =>
          io.nats.client.PushSubscribeOptions.builder()
            .stream(streamName)
            .configuration(consumerConfig)
            .build()
      }
    )

    XStream.acquireRelease {
      (subscription, new Iterator[Envelope] {
        override def hasNext: Boolean = subscription.isActive

        override def next(): Envelope = {
          val msg = subscription.nextMessage(java.time.Duration.ofMillis(100))
          if (msg != null) {
            msg.ack()  // Acknowledge message
            fromNatsMessage(msg)
          } else {
            throw new NoSuchElementException("No message available")
          }
        }
      })
    } { sub =>
      sub.unsubscribe()
    }
  }

  override def createRealtimeConsumer(
    streamName: String,
    config: ConsumerConfig,
  )(using Ctx): XStream[Envelope] = {
    val consumerConfig = toNatsConsumerConfig(config)
    val subscription = jetStream.subscribe(
      streamName,
      config match {
        case ConsumerConfig.Durable(name, _, _) =>
          io.nats.client.PushSubscribeOptions.builder()
            .stream(streamName)
            .durable(name)
            .configuration(consumerConfig)
            .build()
        case ConsumerConfig.Ephemeral(_, _) =>
          io.nats.client.PushSubscribeOptions.builder()
            .stream(streamName)
            .configuration(consumerConfig)
            .build()
      }
    )

    XStream.acquireRelease {
      (subscription, new Iterator[Envelope] {
        override def hasNext: Boolean = subscription.isActive

        override def next(): Envelope = {
          // Block for up to 30 seconds waiting for a message
          val msg = subscription.nextMessage(java.time.Duration.ofSeconds(30))
          if (msg != null) {
            msg.ack()  // Acknowledge message
            fromNatsMessage(msg)
          } else {
            // Timeout - no message in 30 seconds, retry
            next()  // Recursive call to wait again
          }
        }
      })
    } { sub =>
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
      case ConsumerConfig.Durable(_, dp, ap) => (dp, ap)
      case ConsumerConfig.Ephemeral(dp, ap) => (dp, ap)
    }

    builder.deliverPolicy(toNatsDeliverPolicy(deliverPolicy))
    builder.ackPolicy(toNatsAckPolicy(ackPolicy))

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
