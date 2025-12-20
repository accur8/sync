package a8.examples.hermes

import a8.hermes.bootstrap.HermesBootstrap
import a8.hermes.rpc.RpcServer
import a8.shared.app.{BootstrappedIOApp, AppCtx}
import a8.shared.zreplace.Resource

import scala.concurrent.duration.*

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

  override def run()(using appCtx: AppCtx): Unit = {
    Thread.currentThread().setName("ping-service-main")
    logger.info("Starting Ping Service...")

    // Bootstrap Hermes components
    val hermes = Resource.free.run(HermesBootstrap.resource())(using appCtx)

    logger.info(s"✓ Hermes initialized")
    logger.info(s"  Mailbox: ${hermes.mailbox.metadata.address.value}")
    logger.info(s"  NATS server: ${hermes.config.natsUrl}")

    // Register process.v1.Ping handler (godev standard)
    val pingCount = new java.util.concurrent.atomic.AtomicInteger(0)

    val processPingHandler = a8.hermes.rpc.RpcHandler(
      name = "process",
      version = "v1",
      method = "Ping",
      description = Some("Process ping endpoint - echoes payload with timestamp"),
      requiresAuth = false,
    ) { (requestBytes, ctx) =>
      val count = pingCount.incrementAndGet()
      val payload = if (requestBytes.isEmpty) "pong" else new String(requestBytes, "UTF-8")
      val sender = ctx.senderMailbox.map(_.value).getOrElse("unknown")
      logger.info(s"process.v1.Ping #$count from $sender -> payload: $payload")

      // Simple response: payload + timestamp + process info
      val response = s"""{"payload":"$payload","timestamp":"${java.time.Instant.now()}","processId":"${hermes.mailbox.metadata.address.value}"}"""
      response.getBytes("UTF-8")
    }

    hermes.rpcServer.register(processPingHandler)

    logger.info("")
    logger.info("✓ Ping service is ready!")
    logger.info("")
    logger.info("Service Details:")
    logger.info(s"  Mailbox Address: ${hermes.mailbox.metadata.address.value}")
    logger.info(s"  RPC Endpoint: process.v1.Ping")
    logger.info("")

    // Test discovery if enabled
    hermes.dynamicServiceDiscovery.foreach { discovery =>
      logger.info("✓ Dynamic service discovery enabled")
      logger.info("  Querying for process.v1 services...")

      import a8.hermes.discovery.ServiceDiscovery
      val query = ServiceDiscovery.DiscoveryQuery(
        implementsRpc = Seq("process.v1")
      )

      val services = discovery.query(query, timeout = 2.seconds)(using appCtx)
      logger.info(s"  Found ${services.size} services with process.v1:")

      services.foreach { service =>
        logger.info(s"    - ${service.serviceName} @ ${service.mailboxAddress}")
        logger.info(s"      PID: ${service.unixPid}")
        logger.info(s"      Location: ${service.capabilities.location.user}@${service.capabilities.location.server}")
        logger.info(s"      RPCs: ${service.capabilities.implementsRpc.mkString(", ")}")
      }
      logger.info("")
    }

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
