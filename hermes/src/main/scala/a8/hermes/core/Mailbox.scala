package a8.hermes.core

import a8.shared.{CompanionGen, StringValue}
import a8.shared.json.{JsonCodec, JsonTypedCodec, ast}

import java.time.Instant
import scala.concurrent.duration.*

object Mailbox {

  // Triple-key system for mailbox access control

  object AdminKey extends StringValue.Companion[AdminKey]
  case class AdminKey(value: String) extends StringValue

  object ReaderKey extends StringValue.Companion[ReaderKey]
  case class ReaderKey(value: String) extends StringValue

  object MailboxAddress extends StringValue.Companion[MailboxAddress]
  case class MailboxAddress(value: String) extends StringValue

  // Mailbox lifecycle types
  sealed trait LifecycleType {
    def ttl: FiniteDuration
  }

  object LifecycleType {

    // String-encoded: "ephemeral" | "non-durable" | "named:<name>" (the
    // ServiceDiscovery.SerializationFormat idiom). Was an unimplemented ???
    // stub — a NotImplementedError landmine for whoever first serialized a
    // LifecycleType (TASK-20260702-server-lifecycle-audit).
    implicit lazy val jsonCodec: JsonCodec[LifecycleType] =
      JsonCodec.string.dimap[LifecycleType](
        {
          case "ephemeral"                 => Ephemeral
          case "non-durable"               => NonDurable
          case s if s.startsWith("named:") => Named(s.stripPrefix("named:"))
          case other                       => throw new IllegalArgumentException(s"unknown LifecycleType: $other")
        },
        {
          case Ephemeral   => "ephemeral"
          case NonDurable  => "non-durable"
          case Named(name) => s"named:$name"
        },
      ).asJsonCodec

    case object Ephemeral extends LifecycleType {
      override def ttl: FiniteDuration = 24.hours
    }

    case object NonDurable extends LifecycleType {
      override def ttl: FiniteDuration = 15.minutes
    }

    case class Named(name: String) extends LifecycleType {
      override def ttl: FiniteDuration = 90.days
    }
  }

  // Mailbox channels (NATS subjects)
  sealed trait Channel {
    def name: String
  }

  object Channel {
    case object RpcInbox extends Channel {
      override val name: String = "rpc-inbox"
    }

    case object RpcSent extends Channel {
      override val name: String = "rpc-sent"
    }
  }

  // Mailbox metadata
  @CompanionGen
  case class MailboxMetadata(
    adminKey: AdminKey,
    readerKey: ReaderKey,
    address: MailboxAddress,
    lifecycle: LifecycleType,
    createdAt: Instant,
    expiresAt: Instant,
    lastAccessedAt: Instant,
  )

  // Message envelope for RPC communication
//  @CompanionGen
  case class MailboxMessage(
    correlationId: String,
    fromMailbox: MailboxAddress,
    endpoint: String,  // RPC endpoint (schema.version.method)
    contentType: String,  // "application/protobuf" or "application/json"
    payload: Array[Byte],
    metadata: Map[String, String] = Map.empty,
  )

  // Content type constants
  object ContentType {
    val Protobuf = "application/protobuf"
    val Json = "application/json"
  }

}

/**
 * Represents a mailbox instance that can send and receive messages.
 * This is a client-side abstraction - actual mailbox storage is in godev service.
 */
trait Mailbox {
  import Mailbox.*

  def metadata: MailboxMetadata

  def adminKey: AdminKey = metadata.adminKey
  def readerKey: ReaderKey = metadata.readerKey
  def address: MailboxAddress = metadata.address

  // Send message to another mailbox
  def send(
    to: MailboxAddress,
    message: MailboxMessage,
  )(using ctx: a8.shared.app.Ctx): Unit

  // Subscribe to incoming messages on a channel
  def subscribe(channel: Channel)(using ctx: a8.shared.app.Ctx): a8.shared.zreplace.XStream[MailboxMessage]

  // Touch to update last accessed time
  def touch()(using ctx: a8.shared.app.Ctx): Unit
}
