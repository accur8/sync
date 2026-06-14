package a8.hermes.rpc

import a8.hermes.core.{Mailbox, MailboxTransport}
import a8.hermes.core.MailboxTransport.Envelope
import a8.hermes.proto.process.wsmessages._
import a8.shared.app.Ctx
import a8.common.logging.Logging
import a8.shared.zreplace.{Resource, XStream}
import com.google.protobuf.ByteString

import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success, Try}

/**
 * RPC server for handling incoming RPC requests.
 * Manages handler registration and message processing.
 */
object RpcServer extends Logging {

  case class Config(
    mailbox: Mailbox,
    transport: MailboxTransport,
    parallelism: Int = 10, // Number of concurrent message processors
  )

  /**
   * Create an RPC server resource
   */
  def resource(config: Config): Resource[RpcServer] = {
    Resource.acquireRelease {
      new RpcServer(config)
    } { server =>
      server.stop()
    }
  }

}

class RpcServer(config: RpcServer.Config) extends Logging {
  import RpcServer.*

  private val handlers = TrieMap.empty[RpcSchema.SchemaName, RpcHandler]
  @volatile private var running = false

  /**
   * Register an RPC handler
   */
  def register(handler: RpcHandler): Unit = {
    logger.info(s"Registering RPC handler: ${handler.schema.name.value}")
    handlers.put(handler.schema.name, handler)
  }

  /**
   * Register multiple handlers
   */
  def registerAll(handlers: Seq[RpcHandler]): Unit = {
    handlers.foreach(register)
  }

  /**
   * Start the RPC server (subscribe to mailbox's RPC inbox)
   */
  def start()(using ctx: Ctx): Unit = {
    if (running) {
      logger.warn("RPC server already running")
      return
    }

    running = true
    logger.info("Starting RPC server")
    logger.info(s"Listening on mailbox: ${config.mailbox.address.value}")
    logger.info(s"Registered ${handlers.size} handlers")

    // Subscribe to the RPC inbox channel in a background fork
    val rpcInboxSubject = s"hermes.${config.mailbox.adminKey.value}.rpc-inbox"
    logger.info(s"RPC Server subscribing to: $rpcInboxSubject")

    // Get the Ox instance from the context
    val ox0 = ctx match {
      case appCtx: a8.shared.app.AppCtx => appCtx.ox0
      case childCtx: a8.shared.app.Ctx.ChildCtx => childCtx.ox0
      case _ => throw new RuntimeException("Unable to get Ox instance from Ctx")
    }

    // Fork the RPC inbox reader to run in background
    // Use forkUser so that failures in this fork will propagate and stop the app
    ox.forkUser {
      val threadName = s"rpc-server-inbox-${config.mailbox.address.value.take(8)}"
      Thread.currentThread().setName(threadName)
      logger.info(s"RPC Server inbox reader thread started: $threadName")
      try {
        config.transport.subscribe(rpcInboxSubject)(using ctx).runForeach { envelope =>
          if (running) {
            processMessage(envelope)(using ctx)
          }
        }
      } catch {
        case th: Throwable =>
          logger.error(s"RPC inbox reader failed - this will stop the application", th)
          throw th
      } finally {
        logger.info(s"RPC inbox reader stopped")
      }
    }(using ox0)

    logger.info("✓ RPC inbox reader started in background")
  }

  /**
   * Process an incoming RPC message
   */
  private def processMessage(envelope: Envelope)(using ctx: Ctx): Unit = {
    Try {
      // Parse protobuf Message from envelope payload
      val message = Message.parseFrom(envelope.payload)

      // Extract endpoint from protobuf Message.header.rpcHeader
      message.header.flatMap(_.rpcHeader) match {
        case None =>
          logger.warn("Missing RpcHeader in protobuf Message")

        case Some(rpcHeader) =>
          // RPC Server should ONLY process Request frames (ignore responses)
          if (rpcHeader.frameType == RpcFrameType.Request) {
            val endpoint = rpcHeader.endPoint
            val correlationId = Some(rpcHeader.correlationId)
            val senderMailbox = message.header.map(_.sender).map(Mailbox.MailboxAddress(_))
            // The wire format the caller used for the request body — handlers decode/encode by it, and
            // the response echoes it (a JSON request gets a JSON response).
            val requestContentType = message.header.map(_.contentType).getOrElse(ContentType.UnspecifiedCT)

            logger.debug(s"Received RPC request: $endpoint (correlation: ${correlationId.getOrElse("none")})")

            // Create RPC context
            val rpcContext = RpcContext(
              envelope = envelope,
              senderMailbox = senderMailbox,
              correlationId = correlationId,
              endpoint = endpoint,
              contentType = requestContentType,
            )(using ctx)

            // Lookup handler (case-insensitive)
            val schemaName = RpcSchema.SchemaName(endpoint)
            handlers.get(schemaName) match {
              case Some(handler) =>
                // Dispatch (authorize + handle + respond) is wrapped so that ANY exception once we hold
                // a correlation — a request-decode failure inside the handler, a throwing authorize, an
                // NPE, a bug in user code — is turned into an ErrorResponse sent back to the caller for
                // THIS correlation. Without this, a throwing handler leaves the caller's RPC promise
                // unresolved forever (the request never gets a reply): an RPC that never returns. The
                // framework's contract is "exactly one reply per request" — success or error — so callers
                // never hang and never need a client-side timeout to defend against a buggy handler.
                Try {
                  if (handler.schema.requiresAuth && !handler.authorize(rpcContext)) {
                    logger.warn(s"Authorization denied for endpoint: $endpoint")
                    sendError(senderMailbox, "Authorization denied", correlationId)(using ctx)
                  } else {
                    // Execute handler with the actual request data from message.data
                    logger.debug(s"Handling RPC call: $endpoint (correlation: ${correlationId.getOrElse("none")})")
                    val response = handler.handle(message.data.toByteArray, rpcContext)

                    // Send response back to sender, in the same wire format the request used.
                    senderMailbox.foreach { sender =>
                      sendResponse(sender, response, correlationId, endpoint, requestContentType)(using ctx)
                    }
                  }
                } match {
                  case Success(_) => // replied (response or auth-denied error)
                  case Failure(e) =>
                    logger.error(s"RPC handler failed for $endpoint (correlation: ${correlationId.getOrElse("none")})", e)
                    val detail = Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getName)
                    sendError(senderMailbox, s"handler error: $detail", correlationId)(using ctx)
                }

              case None =>
                logger.warn(s"No handler found for endpoint: $endpoint")
                sendError(senderMailbox, s"Handler not found: $endpoint", correlationId)(using ctx)
            }
          }
      }

    } match {
      case Success(_) => // OK
      case Failure(e) =>
        logger.error("Error processing RPC message", e)
    }
  }

  /**
   * Send RPC response. `responseContentType` is the wire format of `payload` and is echoed on the
   * response header so the caller decodes it the same way it encoded its request (JSON request →
   * protojson response → the caller's JSON.parse reads it; protobuf request → binary response).
   */
  private def sendResponse(
    senderMailbox: Mailbox.MailboxAddress,
    payload: Array[Byte],
    correlationId: Option[String],
    endpoint: String,
    responseContentType: ContentType,
  )(using ctx: Ctx): Unit = {
    // Construct protobuf Message with SuccessResponse frame type
    val responseMessage = Message(
      header = Some(MessageHeader(
        sender = config.mailbox.address.value,
        contentType = if (responseContentType == ContentType.UnspecifiedCT) ContentType.Protobuf else responseContentType,
        rpcHeader = Some(RpcHeader(
          correlationId = correlationId.getOrElse(""),
          endPoint = endpoint,
          frameType = RpcFrameType.SuccessResponse,
        )),
      )),
      senderEnvelope = Some(SenderEnvelope(
        created = System.currentTimeMillis()
      )),
      data = ByteString.copyFrom(payload),
    )

    // Send via mailbox
    config.mailbox.send(
      to = senderMailbox,
      message = Mailbox.MailboxMessage(
        correlationId = correlationId.getOrElse(""),
        fromMailbox = config.mailbox.address,
        endpoint = endpoint,
        contentType = Mailbox.ContentType.Protobuf,
        payload = responseMessage.toByteArray,
      )
    )(using ctx)

    logger.debug(s"Sent RPC response for $endpoint to ${senderMailbox.value}")
  }

  /**
   * Send error response
   */
  private def sendError(
    senderMailboxOpt: Option[Mailbox.MailboxAddress],
    errorMessage: String,
    correlationId: Option[String],
  )(using ctx: Ctx): Unit = {
    senderMailboxOpt.foreach { senderMailbox =>
      // Construct protobuf Message with ErrorResponse frame type
      val errorResponseMessage = Message(
        header = Some(MessageHeader(
          sender = config.mailbox.address.value,
          contentType = ContentType.Protobuf,
          rpcHeader = Some(RpcHeader(
            correlationId = correlationId.getOrElse(""),
            endPoint = "",
            frameType = RpcFrameType.ErrorResponse,
            errorInfo = Some(RpcErrorInfo(
              message = errorMessage,
            )),
          )),
        )),
        senderEnvelope = Some(SenderEnvelope(
          created = System.currentTimeMillis()
        )),
        data = ByteString.EMPTY,
      )

      // Send via mailbox
      config.mailbox.send(
        to = senderMailbox,
        message = Mailbox.MailboxMessage(
          correlationId = correlationId.getOrElse(""),
          fromMailbox = config.mailbox.address,
          endpoint = "",
          contentType = Mailbox.ContentType.Protobuf,
          payload = errorResponseMessage.toByteArray,
        )
      )(using ctx)

      logger.debug(s"Sent RPC error to ${senderMailbox.value}: $errorMessage")
    }
  }

  /**
   * Stop the RPC server
   */
  def stop(): Unit = {
    if (running) {
      logger.info("Stopping RPC server")
      running = false
    }
  }

  /**
   * Get list of registered handlers
   */
  def registeredHandlers: Seq[RpcHandler] = handlers.values.toSeq

}
