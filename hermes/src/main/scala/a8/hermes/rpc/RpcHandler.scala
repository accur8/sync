package a8.hermes.rpc

import a8.hermes.core.Mailbox
import a8.hermes.core.MailboxTransport.Envelope
import a8.shared.app.Ctx

/**
 * Context for RPC handler execution.
 * Contains the request envelope and sender information.
 */
case class RpcContext(
  envelope: Envelope,
  senderMailbox: Option[Mailbox.MailboxAddress],
  correlationId: Option[String],
  endpoint: String,
)(using val ctx: Ctx)

/**
 * Trait for RPC handlers.
 * Handlers process incoming RPC requests and return responses.
 */
trait RpcHandler {
  /**
   * The schema this handler implements
   */
  def schema: RpcSchema.Schema

  /**
   * Handle an RPC request
   * @param request The request payload (protobuf bytes)
   * @param ctx The RPC context
   * @return The response payload (protobuf bytes)
   */
  def handle(request: Array[Byte], ctx: RpcContext): Array[Byte]

  /**
   * Optional: Validate authorization for this handler
   * @param ctx The RPC context
   * @return true if authorized, false otherwise
   */
  def authorize(ctx: RpcContext): Boolean = {
    // Default implementation - override for custom auth
    true
  }
}

/**
 * Typed RPC handler with protobuf serialization.
 * Provides type-safe request/response handling.
 */
abstract class TypedRpcHandler[Req <: scalapb.GeneratedMessage, Resp <: scalapb.GeneratedMessage](
  using
  reqCompanion: scalapb.GeneratedMessageCompanion[Req],
  respCompanion: scalapb.GeneratedMessageCompanion[Resp],
) extends RpcHandler {

  /**
   * Type-safe handle method to be implemented by subclasses
   */
  def handleTyped(request: Req, ctx: RpcContext): Resp

  /**
   * Deserialize, handle, and serialize
   */
  override final def handle(request: Array[Byte], ctx: RpcContext): Array[Byte] = {
    val req = reqCompanion.parseFrom(request)
    val resp = handleTyped(req, ctx)
    resp.toByteArray
  }
}

/**
 * Helper object for creating simple handlers
 */
object RpcHandler {

  /**
   * Create a simple handler from a function
   */
  def apply(
    name: String,
    version: String,
    method: String,
    description: Option[String] = None,
    requiresAuth: Boolean = true,
  )(
    fn: (Array[Byte], RpcContext) => Array[Byte]
  ): RpcHandler = {
    val schemaValue = RpcSchema.register(name, version, method, description, requiresAuth)

    new RpcHandler {
      override def schema: RpcSchema.Schema = schemaValue
      override def handle(request: Array[Byte], ctx: RpcContext): Array[Byte] = fn(request, ctx)
    }
  }

  /**
   * Create a typed handler from a function
   */
  def typed[Req <: scalapb.GeneratedMessage, Resp <: scalapb.GeneratedMessage](
    name: String,
    version: String,
    method: String,
    description: Option[String] = None,
    requiresAuth: Boolean = true,
  )(
    fn: (Req, RpcContext) => Resp
  )(using
    reqCompanion: scalapb.GeneratedMessageCompanion[Req],
    respCompanion: scalapb.GeneratedMessageCompanion[Resp],
  ): RpcHandler = {
    val schemaValue = RpcSchema.register(name, version, method, description, requiresAuth)

    new TypedRpcHandler[Req, Resp] {
      override def schema: RpcSchema.Schema = schemaValue
      override def handleTyped(request: Req, ctx: RpcContext): Resp = fn(request, ctx)
    }
  }

}
