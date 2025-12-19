package a8.hermes.core

import a8.shared.app.Ctx
import a8.shared.zreplace.{Resource, XStream}

import scala.concurrent.duration.FiniteDuration

/**
 * Transport-agnostic interface for mailbox communication.
 * This abstraction allows for multiple implementations:
 * - NATS (current)
 * - WebSocket/gRPC (future)
 */
object MailboxTransport {

  /**
   * Transport-agnostic message envelope
   */
  case class Envelope(
    subject: String,
    headers: Map[String, String],
    payload: Array[Byte],
    replyTo: Option[String] = None,
  )

  /**
   * Subscription handle for managing message subscriptions
   */
  trait Subscription {
    def unsubscribe(): Unit
    def isActive: Boolean
  }

  /**
   * Consumer configuration for durable/ephemeral message consumption
   */
  sealed trait ConsumerConfig

  object ConsumerConfig {
    /**
     * Durable consumer that survives process restarts
     */
    case class Durable(
      consumerName: String,
      deliverPolicy: DeliverPolicy,
      ackPolicy: AckPolicy,
    ) extends ConsumerConfig

    /**
     * Ephemeral consumer deleted on process exit
     */
    case class Ephemeral(
      deliverPolicy: DeliverPolicy,
      ackPolicy: AckPolicy,
    ) extends ConsumerConfig
  }

  /**
   * Message delivery policy
   */
  sealed trait DeliverPolicy

  object DeliverPolicy {
    case object All extends DeliverPolicy  // Deliver all available messages
    case object New extends DeliverPolicy  // Deliver only new messages
    case object Last extends DeliverPolicy  // Deliver only the last message
    case class ByStartSequence(seq: Long) extends DeliverPolicy  // Start from specific sequence
  }

  /**
   * Acknowledgment policy
   */
  sealed trait AckPolicy

  object AckPolicy {
    case object None extends AckPolicy  // No acknowledgment required
    case object All extends AckPolicy  // Acknowledge all messages
    case object Explicit extends AckPolicy  // Explicit per-message acknowledgment
  }

  /**
   * Stream retention policy
   */
  sealed trait StreamRetention

  object StreamRetention {
    case object Limits extends StreamRetention  // Retain based on limits (size, age, count)
    case object Interest extends StreamRetention  // Retain while there are consumers
    case object WorkQueue extends StreamRetention  // One message per consumer
  }

}

/**
 * Main transport interface
 */
trait MailboxTransport {

  import MailboxTransport.*

  /**
   * Publish a message to a subject
   */
  def publish(
    subject: String,
    headers: Map[String, String],
    payload: Array[Byte],
  )(using Ctx): Unit

  /**
   * Publish with request-reply pattern (blocking with timeout)
   */
  def request(
    subject: String,
    headers: Map[String, String],
    payload: Array[Byte],
    timeout: FiniteDuration,
  )(using Ctx): Option[Envelope]

  /**
   * Subscribe to a subject with optional queue group
   */
  def subscribe(
    subject: String,
    queueGroup: Option[String] = None,
  )(using Ctx): XStream[Envelope]

  /**
   * Create a durable or ephemeral consumer for a stream
   */
  def createConsumer(
    streamName: String,
    config: ConsumerConfig,
  )(using Ctx): XStream[Envelope]

  /**
   * Create a NATS JetStream stream
   */
  def createStream(
    name: String,
    subjects: Seq[String],
    retention: StreamRetention,
    maxAge: FiniteDuration,
  )(using Ctx): Unit

  /**
   * Check if a stream exists
   */
  def streamExists(name: String)(using Ctx): Boolean

  /**
   * Get stream info
   */
  def streamInfo(name: String)(using Ctx): Option[StreamInfo]

  /**
   * Stream information
   */
  case class StreamInfo(
    name: String,
    subjects: Seq[String],
    messageCount: Long,
    byteCount: Long,
  )

}
