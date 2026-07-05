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

    transport.publish(
      subject = targetSubject,
      headers = headers,
      payload = message.payload,
    )(using ctx)
  }

  override def subscribe(channel: Channel)(using ctx: Ctx): XStream[MailboxMessage] = {
    // Subscribe to our channel — subjects are address-based.
    val channelSubject = s"mesh.${metadata.address.value}.${channel.name}"

    transport.subscribe(channelSubject)(using ctx).map { envelope =>
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
