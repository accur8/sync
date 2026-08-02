package a8.hermes.bootstrap

import a8.hermes.core.{Mailbox, MailboxTransport}
import a8.hermes.core.Mailbox.*
import a8.shared.app.Ctx
import a8.shared.zreplace.XStream
import a8.common.logging.Logging

import java.time.Instant

/**
 * Simple mailbox implementation for bootstrap.
 * Uses a named mailbox from configuration.
 *
 * Subjects are ADDRESS-based (capability-aligned naming, mesh-sprint): the
 * subject `mesh.<address>.<channel>` carries the write key a sender already
 * holds, so sending needs NO record lookup at all, and the adminKey never
 * appears on the wire.
 */
class SimpleMailbox(
  val metadata: MailboxMetadata,
  transport: MailboxTransport,
  touchFn: () => Unit = () => (), // Refreshes this mailbox's lastActivity (debounced); no-op by default
) extends Mailbox with Logging {

  override def send(
    to: MailboxAddress,
    message: MailboxMessage,
  )(using ctx: Ctx): Unit = {
    // Convert MailboxMessage to transport envelope
    val headers = Map(
      "endpoint" -> message.endpoint,
      "correlation-id" -> message.correlationId,
      "sender-mailbox" -> metadata.address.value,
      "content-type" -> message.contentType,
    ) ++ message.metadata

    // The address IS the write capability — publish straight to it.
    val targetSubject = s"mesh.${to.value}.rpc-inbox"
    logger.debug(s"Publishing to subject: $targetSubject")

    // ACKED, not fire-and-forget (invariant 3's outbound leg): a plain publish already
    // flushed into a socket the outage kills is silently gone — measured at 1-4 messages
    // per outage. The per-message msg-id keys the stream's duplicate window, so the
    // transport's republish-until-acked is safe when the original landed.
    // BUG-20260802-hermes-nats-publish-unacked-inflight-loss.
    transport.publishAcked(
      subject = targetSubject,
      headers = headers,
      payload = message.payload,
      msgId = java.util.UUID.randomUUID().toString,
    )(using ctx)
  }

  // One durable reader per mailbox instance, minted at construction: its server-side
  // position gives delivery continuity across NATS connection loss WITHIN this process
  // (a core-NATS subscribe reconnects fine but silently loses everything published
  // during the outage — BUG-20260801-hermes-nats-mailbox-outage-loss). A fresh process
  // mints a fresh consumer with DeliverPolicy.New, which is exactly the old
  // subscribe-from-now semantics; the previous run's consumer self-reaps via the
  // inactive threshold.
  private val readerConsumerName =
    "rdr" + java.util.UUID.randomUUID().toString.replace("-", "").take(16)

  override def subscribe(channel: Channel)(using ctx: Ctx): XStream[MailboxMessage] =
    subscribe(channel, MailboxTransport.DeliverPolicy.New)

  /**
   * deliverPolicy is the app's choice of START POSITION (invariant 2 of godev
   * docs/mesh-client/client-invariants.md: the first subscription of a fresh process
   * belongs to the app). New — from-now — is the daemon default; a caller that must not
   * miss anything published while its consumer was still binding (the conformance client,
   * on a freshly-minted mailbox) passes All. Measured before this existed: the first 48 of
   * 20,000 messages landed in the bind window and were never delivered.
   */
  def subscribe(channel: Channel, deliverPolicy: MailboxTransport.DeliverPolicy)(using ctx: Ctx): XStream[MailboxMessage] = {
    // Subscribe to our channel — subjects are address-based. The JetStream consumer
    // resolves the capturing stream FROM the subject; no stream-name arithmetic here.
    val channelSubject = s"mesh.${metadata.address.value}.${channel.name}"

    transport.createConsumer(
      channelSubject,
      MailboxTransport.ConsumerConfig.Durable(
        consumerName = s"$readerConsumerName-${channel.name}",
        deliverPolicy = deliverPolicy,
        ackPolicy = MailboxTransport.AckPolicy.Explicit,
        // Must outlast any outage worth surviving — reaping DURING an outage would
        // recreate the loss this consumer exists to prevent.
        inactiveThreshold = Some(scala.concurrent.duration.DurationInt(1).hour),
      ),
    )(using ctx).map { envelope =>
      MailboxMessage(
        correlationId = envelope.headers.getOrElse("correlation-id", ""),
        fromMailbox = envelope.headers.get("sender-mailbox")
          .map(MailboxAddress(_))
          .getOrElse(MailboxAddress("aa_unknown")),
        endpoint = envelope.headers.getOrElse("endpoint", ""),
        contentType = envelope.headers.getOrElse("content-type", ContentType.Protobuf),
        payload = envelope.payload,
        metadata = envelope.headers - "correlation-id" - "sender-mailbox" - "endpoint" - "content-type",
      )
    }
  }

  override def touch()(using ctx: Ctx): Unit = {
    // Refresh this mailbox's lastActivity via the mesh records endpoints
    // (debounced). Default is a no-op for placeholder mailboxes (e.g.
    // fromNamedMailbox) that have no records binding.
    touchFn()
  }

}

object SimpleMailbox {

  /**
   * Create a mailbox from a named mailbox configuration.
   * This assumes the mailbox already exists in the godev service.
   */
  def fromNamedMailbox(
    name: String,
    address: String,
    transport: MailboxTransport,
  ): SimpleMailbox = {
    // For named mailboxes, the address is exactly as configured
    val mailboxAddress = MailboxAddress(address)

    val metadata = MailboxMetadata(
      adminKey = AdminKey(s"zz${name}_admin"),  // Placeholder
      readerKey = ReaderKey(s"rr${name}_reader"),  // Placeholder
      address = mailboxAddress,
      lifecycle = LifecycleType.Named(name),
      createdAt = Instant.now(),
      expiresAt = Instant.now().plusSeconds(LifecycleType.Named(name).ttl.toSeconds),
      lastAccessedAt = Instant.now(),
    )

    new SimpleMailbox(metadata, transport)
  }

}
