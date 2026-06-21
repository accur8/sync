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
 * This is a minimal implementation - full mailbox client
 * functionality will be added later.
 */
class SimpleMailbox(
  val metadata: MailboxMetadata,
  transport: MailboxTransport,
  lookupAdminKey: MailboxAddress => Option[AdminKey], // Function to look up adminKey by address
) extends Mailbox with Logging {

  override def send(
    to: MailboxAddress,
    message: MailboxMessage,
  )(using ctx: Ctx): Unit = {
    logger.debug(s"Looking up adminKey for mailbox address: ${to.value}")

    // Look up recipient's adminKey from KV store
    val recipientAdminKey = lookupAdminKey(to).getOrElse {
      logger.error(s"Mailbox address not found in KV store: ${to.value}")
      throw new RuntimeException(s"Mailbox address not found: ${to.value}")
    }

    logger.debug(s"Found adminKey for ${to.value}: ${recipientAdminKey.value}")

    // Convert MailboxMessage to transport envelope
    val headers = Map(
      "endpoint" -> message.endpoint,
      "correlation-id" -> message.correlationId,
      "sender-mailbox" -> metadata.address.value,
      "content-type" -> message.contentType,
    ) ++ message.metadata

    // Publish to recipient's RPC inbox using their adminKey
    val targetSubject = s"mesh.${recipientAdminKey.value}.rpc-inbox"
    logger.debug(s"Publishing to subject: $targetSubject")

    transport.publish(
      subject = targetSubject,
      headers = headers,
      payload = message.payload,
    )(using ctx)
  }

  override def subscribe(channel: Channel)(using ctx: Ctx): XStream[MailboxMessage] = {
    // Subscribe to our channel using our adminKey
    val channelSubject = s"mesh.${metadata.adminKey.value}.${channel.name}"

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
    // No-op for now - this would update last accessed time in mailbox service
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
    lookupAdminKey: MailboxAddress => Option[AdminKey],
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

    new SimpleMailbox(metadata, transport, lookupAdminKey)
  }

}
