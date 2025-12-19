package a8.hermes.rpc

import a8.hermes.core.{Mailbox, MailboxTransport}
import a8.hermes.core.MailboxTransport.Envelope
import a8.hermes.proto.process.wsmessages._
import a8.shared.app.Ctx
import a8.common.logging.Logging
import a8.shared.zreplace.Resource
import com.google.protobuf.ByteString

import java.util.UUID
import scala.collection.concurrent.TrieMap
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.{FiniteDuration, *}
import scala.util.{Failure, Success, Try}

/**
 * RPC client for making RPC calls to remote mailboxes.
 * Implements correlation tracking using promises.
 */
object RpcClient extends Logging {

  case class Config(
    mailbox: Mailbox,
    transport: MailboxTransport,
    defaultTimeout: FiniteDuration = 30.seconds,
  )

  /**
   * RPC call result
   */
  sealed trait RpcResult
  object RpcResult {
    case class Success(payload: Array[Byte]) extends RpcResult
    case class Error(message: String) extends RpcResult
    case object Timeout extends RpcResult
  }

  /**
   * Create an RPC client resource
   */
  def resource(config: Config)(using ctx: Ctx): Resource[RpcClient] = {
    Resource.acquireRelease {
      val client = new RpcClient(config)
      client.start()(using ctx)
      client
    } { client =>
      client.stop()
    }
  }

}

class RpcClient(config: RpcClient.Config) extends Logging {
  import RpcClient.*

  // Correlation tracking: correlationId -> Promise[RpcResult]
  private val pendingCalls = TrieMap.empty[String, Promise[RpcResult]]

  @volatile private var running = false

  /**
   * Start the RPC client (subscribe to our RPC inbox for responses)
   */
  def start()(using ctx: Ctx): Unit = {
    if (running) {
      logger.warn("RPC client already running")
      return
    }

    running = true
    logger.info("Starting RPC client")

    // Subscribe to our RPC inbox to receive responses
    val rpcInboxSubject = s"hermes.${config.mailbox.adminKey.value}.rpc-inbox"
    logger.info(s"RPC Client subscribing to: $rpcInboxSubject")

    // Get the Ox instance from the context
    val ox0 = ctx match {
      case appCtx: a8.shared.app.AppCtx => appCtx.ox0
      case childCtx: a8.shared.app.Ctx.ChildCtx => childCtx.ox0
      case _ => throw new RuntimeException("Unable to get Ox instance from Ctx")
    }

    // Fork the response reader to run in background
    ox.fork {
      val threadName = s"rpc-client-response-${config.mailbox.address.value.take(8)}"
      Thread.currentThread().setName(threadName)
      logger.debug(s"RPC Client response reader thread name: $threadName")
      config.transport.subscribe(rpcInboxSubject)(using ctx).runForeach { envelope =>
        if (running) {
          processResponse(envelope)
        }
      }
    }(using ox0)

    logger.info("✓ RPC response reader started in background")
  }

  /**
   * Process an incoming RPC response
   */
  private def processResponse(envelope: Envelope): Unit = {
    logger.debug(s"[RPC-RESPONSE] Received response envelope with ${envelope.payload.length} bytes")

    // Parse the protobuf Message from the envelope payload
    Try(Message.parseFrom(envelope.payload)) match {
      case Success(message) =>
        message.header.flatMap(_.rpcHeader) match {
          case Some(rpcHeader) =>
            // RPC Client should ONLY process Response frames (ignore requests)
            if (rpcHeader.frameType != RpcFrameType.Request) {
              val correlationId = rpcHeader.correlationId
              pendingCalls.remove(correlationId) match {
                case Some(promise) =>
                  logger.debug(s"Matched correlation ID: $correlationId, frameType: ${rpcHeader.frameType}")
                  rpcHeader.frameType match {
                    case RpcFrameType.SuccessResponse =>
                      // Extract the actual response data from message.data
                      logger.debug(s"Success response with ${message.data.size} bytes of data")
                      promise.success(RpcResult.Success(message.data.toByteArray))

                    case RpcFrameType.ErrorResponse =>
                      val errorMsg = rpcHeader.errorInfo.map(_.message).getOrElse("Unknown error")
                      logger.warn(s"[RPC-RESPONSE] Error response: $errorMsg")
                      promise.success(RpcResult.Error(errorMsg))

                    case other =>
                      logger.error(s"[RPC-RESPONSE] Unexpected frame type: $other")
                      promise.success(RpcResult.Error(s"Unexpected frame type: $other"))
                  }

                case None =>
                  logger.warn(s"[RPC-RESPONSE] Received response for unknown correlation ID: $correlationId")
              }
            }

          case None =>
            logger.warn("Received RPC response without RpcHeader")
        }

      case Failure(e) =>
        logger.error("Failed to parse RPC response message", e)
    }
  }

  /**
   * Make an RPC call
   *
   * @param targetMailbox The mailbox to send the request to
   * @param endpoint The RPC endpoint (e.g., "process.v1.Shutdown")
   * @param payload The request payload (protobuf bytes)
   * @param timeout Optional timeout (uses default if not specified)
   * @return The RPC result
   */
  def call(
    targetMailbox: Mailbox.MailboxAddress,
    endpoint: String,
    payload: Array[Byte],
    timeout: Option[FiniteDuration] = None,
  )(using ctx: Ctx): RpcResult = {
    val correlationId = UUID.randomUUID().toString
    val promise = Promise[RpcResult]()

    // Register correlation callback BEFORE sending request (critical!)
    pendingCalls.put(correlationId, promise)

    try {
      logger.debug(s"Sending RPC call: $endpoint to $targetMailbox (correlation: $correlationId)")

      // Construct proper protobuf Message with RpcHeader
      val rpcMessage = Message(
        header = Some(MessageHeader(
          sender = config.mailbox.address.value,
          contentType = ContentType.Protobuf,
          rpcHeader = Some(RpcHeader(
            correlationId = correlationId,
            endPoint = endpoint,
            frameType = RpcFrameType.Request,
          )),
        )),
        senderEnvelope = Some(SenderEnvelope(
          created = System.currentTimeMillis()
        )),
        data = ByteString.copyFrom(payload),
      )

      // Serialize the protobuf Message to bytes
      val messageBytes = rpcMessage.toByteArray

      // Use mailbox.send() which handles adminKey lookup
      config.mailbox.send(
        to = targetMailbox,
        message = Mailbox.MailboxMessage(
          correlationId = correlationId,
          fromMailbox = config.mailbox.address,
          endpoint = endpoint,
          contentType = Mailbox.ContentType.Protobuf,
          payload = messageBytes,
        )
      )(using ctx)

      // Wait for response with timeout
      val effectiveTimeout = timeout.getOrElse(config.defaultTimeout)

      import scala.concurrent.ExecutionContext.Implicits.global
      import scala.concurrent.Await

      Try(Await.result(promise.future, effectiveTimeout)) match {
        case Success(result) => result
        case Failure(_: java.util.concurrent.TimeoutException) =>
          // Clean up on timeout
          pendingCalls.remove(correlationId)
          logger.warn(s"RPC call timeout: $endpoint (correlation: $correlationId)")
          RpcResult.Timeout
        case Failure(e) =>
          pendingCalls.remove(correlationId)
          logger.error(s"RPC call failed: $endpoint", e)
          RpcResult.Error(e.getMessage)
      }

    } catch {
      case e: Exception =>
        pendingCalls.remove(correlationId)
        logger.error(s"Error sending RPC call: $endpoint", e)
        RpcResult.Error(e.getMessage)
    }
  }

  /**
   * Make a typed RPC call with protobuf serialization
   */
  def callTyped[Req <: scalapb.GeneratedMessage, Resp <: scalapb.GeneratedMessage](
    targetMailbox: Mailbox.MailboxAddress,
    endpoint: String,
    request: Req,
    timeout: Option[FiniteDuration] = None,
  )(using
    ctx: Ctx,
    respCompanion: scalapb.GeneratedMessageCompanion[Resp],
  ): Option[Resp] = {
    call(targetMailbox, endpoint, request.toByteArray, timeout)(using ctx) match {
      case RpcResult.Success(payload) =>
        Try(respCompanion.parseFrom(payload)) match {
          case Success(resp) => Some(resp)
          case Failure(e) =>
            logger.error(s"Failed to parse RPC response: $endpoint", e)
            None
        }

      case RpcResult.Error(message) =>
        logger.warn(s"RPC call returned error: $endpoint - $message")
        None

      case RpcResult.Timeout =>
        logger.warn(s"RPC call timed out: $endpoint")
        None
    }
  }

  /**
   * Stop the RPC client
   */
  def stop(): Unit = {
    if (running) {
      logger.info("Stopping RPC client")
      running = false

      // Cancel all pending calls
      pendingCalls.foreach { case (correlationId, promise) =>
        promise.trySuccess(RpcResult.Error("RPC client stopped"))
      }
      pendingCalls.clear()
    }
  }

  /**
   * Get number of pending RPC calls
   */
  def pendingCallCount: Int = pendingCalls.size

}
