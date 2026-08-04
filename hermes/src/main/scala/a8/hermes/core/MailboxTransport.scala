package a8.hermes.core

import a8.shared.app.Ctx
import a8.shared.zreplace.XStream

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
    /**
     * Acknowledge THIS delivery — called by the consumer AFTER processing, never by the
     * transport on the consumer's behalf. The transport used to ack eagerly at the pull
     * (ack-before-deliver): stream prefetch then acked whole chunks the app had not seen,
     * and a connection death dropped them with the server unwilling to redeliver —
     * measured as contiguous 20–215-message holes with the consumer reporting
     * ackPending=0 redelivered=0 (BUG-20260802-sync-nats-kill-loses-inflight-publishes).
     * No-op by default so non-JetStream paths (core subscribe) stay ack-free.
     */
    ack: () => Unit = () => (),
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
     * Durable consumer: its position lives SERVER-side, so delivery survives connection
     * loss and resumes with the gap replayed — the property a mailbox reader needs
     * (BUG-20260801-hermes-nats-mailbox-outage-loss).
     *
     * inactiveThreshold reaps a durable nobody is reading anymore: each process run
     * mints its own reader consumer, so without it a long-lived named mailbox
     * accumulates one abandoned durable per restart, forever. It must comfortably
     * exceed any outage worth surviving — reaping DURING an outage recreates the loss
     * this consumer exists to prevent.
     */
    case class Durable(
      consumerName: String,
      deliverPolicy: DeliverPolicy,
      ackPolicy: AckPolicy,
      inactiveThreshold: Option[FiniteDuration] = scala.None,
      /**
       * ackWait bounds how long the server waits for an ack before REDELIVERING. It is
       * the recovery clock for the push-consumer void window: messages pushed into a
       * connection the outage killed are outstanding-unacked server-side, and nothing
       * re-sends them until ackWait expires. The jnats default (30s) parks that
       * redelivery far beyond any client reconnect (measured ~10ms) — a kill mid-stream
       * left 22 of 20,000 messages undelivered for 30s+ while the reconnected consumer
       * carried on with new deliveries around them
       * (BUG-20260802-sync-nats-kill-loses-inflight-publishes). Set it a little above
       * the worst honest per-message processing time, never to the default.
       */
      ackWait: Option[FiniteDuration] = scala.None,
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
   * Publish with an END-TO-END ACK and a dedup key — invariant 3's outbound leg
   * (godev docs/mesh-client/client-invariants.md) for this transport.
   *
   * A plain publish is fire-and-forget: a message already flushed into the socket an
   * outage kills has no ack to notice the loss, so every outage eats the in-flight tail
   * (measured at 1-4 messages per outage on the scala-sync-nats row,
   * BUG-20260802-hermes-nats-publish-unacked-inflight-loss). An acked publish is
   * REPUBLISHED until the stream confirms it; msgId is the dedup key that makes the
   * republish safe when the original did land. May return before the ack arrives — the
   * tracking is the transport's job.
   */
  def publishAcked(
    subject: String,
    headers: Map[String, String],
    payload: Array[Byte],
    msgId: String,
  )(using Ctx): Unit

  /**
   * Subscribe to a subject with optional queue group
   */
  def subscribe(
    subject: String,
    queueGroup: Option[String] = None,
  )(using Ctx): XStream[Envelope]

  /**
   * Consume `subject` through a JETSTREAM consumer on the stream that captures it. The
   * stream is resolved FROM the subject — the client never does stream-name arithmetic
   * (mesh-<kind>-<readerKey>-<channel> stays a server-side concern it cannot drift from).
   *
   * This — not subscribe() — is how a mailbox channel must be read: a core-NATS
   * subscribe auto-reconnects but is fire-and-forget, so everything published while the
   * connection was down is silently LOST even though it sits durably in the stream. A
   * durable consumer's position lives server-side; delivery resumes after the outage
   * with the gap replayed. Invariant 2 of godev docs/mesh-client/client-invariants.md;
   * BUG-20260801-hermes-nats-mailbox-outage-loss.
   *
   * The stream blocks while the subject is quiet and ends only when the subscription
   * is closed.
   */
  def createConsumer(
    subject: String,
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
