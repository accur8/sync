package a8.examples.hermes

import a8.hermes.bootstrap.HermesBootstrap
import a8.hermes.rpc.RpcServer
import a8.shared.app.{BootstrappedIOApp, AppCtx}
import a8.shared.zreplace.Resource

/**
 * PingService - A long-lived service that responds to ping requests.
 *
 * This service is useful for testing service discovery mechanisms.
 * It registers itself and responds to simple ping RPCs indefinitely.
 *
 * Flow:
 * 1. Bootstrap Hermes (NATS connection, mailbox, RPC server)
 * 2. Register ping RPC handler
 * 3. Run indefinitely, responding to ping requests
 */
object PingService extends BootstrappedIOApp {

  case class PingRequest(message: String = "")
  case class PingResponse(echo: String, mailbox: String)

  override def run()(using appCtx: AppCtx): Unit = {
    Thread.currentThread().setName("ping-service-main")
    logger.info("Starting Ping Service...")

    // Bootstrap Hermes components
    val hermes = Resource.free.run(HermesBootstrap.resource())(using appCtx)

    logger.info(s"✓ Hermes initialized")
    logger.info(s"  Mailbox: ${hermes.mailbox.metadata.address.value}")
    logger.info(s"  NATS server: ${hermes.config.natsUrl}")

    // Register ping handler
    val pingCount = new java.util.concurrent.atomic.AtomicInteger(0)

    val pingHandler = a8.hermes.rpc.RpcHandler(
      name = "ping",
      version = "v1",
      method = "Ping",
      description = Some("Simple ping service for testing service discovery"),
      requiresAuth = false,  // No auth required for testing
    ) { (requestBytes, ctx) =>
      val count = pingCount.incrementAndGet()
      val echo = if (requestBytes.isEmpty) "pong" else new String(requestBytes, "UTF-8")
      val response = PingResponse(
        echo = s"$echo (request #$count)",
        mailbox = hermes.mailbox.metadata.address.value
      )
      val sender = ctx.senderMailbox.map(_.value).getOrElse("unknown")
      logger.info(s"Ping received from $sender -> responding: ${response.echo}")
      response.toString.getBytes("UTF-8")
    }

    hermes.rpcServer.register(pingHandler)

    logger.info("")
    logger.info("✓ Ping service is ready!")
    logger.info("")
    logger.info("Service Details:")
    logger.info(s"  Mailbox Address: ${hermes.mailbox.metadata.address.value}")
    logger.info(s"  RPC Endpoint: ping.v1.Ping")
    logger.info("")
    logger.info("To test this service, send a ping RPC to the mailbox address above")
    logger.info("")
    logger.info("Press Ctrl+C to stop the service")
    logger.info("")

    // Keep the service running
    val latch = new java.util.concurrent.CountDownLatch(1)

    // Add shutdown hook for graceful termination
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      logger.info("")
      logger.info("🛑 Shutting down Ping Service...")
      logger.info(s"Total pings received: ${pingCount.get()}")
      latch.countDown()
    }))

    // Wait indefinitely
    latch.await()
  }
}
