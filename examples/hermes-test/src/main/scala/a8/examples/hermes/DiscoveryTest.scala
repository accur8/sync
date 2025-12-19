package a8.examples.hermes

import a8.hermes.bootstrap.HermesBootstrap
import a8.hermes.discovery.ServiceDiscovery
import a8.shared.app.{BootstrappedIOApp, AppCtx}
import a8.shared.zreplace.Resource

import scala.concurrent.duration.*

/**
 * Test client for service discovery.
 *
 * This demonstrates how to query for services using the godev-compatible
 * service discovery protocol.
 *
 * Usage:
 *   sbt "hermesTest/runMain a8.examples.hermes.DiscoveryTest"
 *
 * Prerequisites:
 *   1. NATS server running: nats-server -js
 *   2. Enable discovery in config: enableDynamicDiscovery = true
 *   3. Start PingService in another terminal
 */
object DiscoveryTest extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit = {
    Thread.currentThread().setName("discovery-test-main")
    logger.info("Starting Discovery Test...")
    logger.info("")

    val hermes = Resource.free.run(HermesBootstrap.resource())(using appCtx)

    hermes.dynamicServiceDiscovery match {
      case Some(discovery) =>
        logger.info("✓ Dynamic service discovery enabled")
        logger.info("")

        // Test 1: Find all ping services
        logger.info("=== Test 1: Query for ping.v1 services ===")
        val pingServices = discovery.query(
          ServiceDiscovery.DiscoveryQuery(implementsRpc = Seq("ping.v1")),
          timeout = 3.seconds
        )(using appCtx)

        logger.info(s"Found ${pingServices.size} ping services:")
        pingServices.foreach(printService)

        logger.info("")

        // Test 2: Find all services (empty query)
        logger.info("=== Test 2: Query for all services ===")
        val allServices = discovery.query(
          ServiceDiscovery.DiscoveryQuery(),
          timeout = 3.seconds
        )(using appCtx)

        logger.info(s"Found ${allServices.size} total services:")
        allServices.foreach(printService)

        logger.info("")

        // Test 3: Query by app name with wildcard
        logger.info("=== Test 3: Query by app name pattern (hermes-*) ===")
        val hermesServices = discovery.query(
          ServiceDiscovery.DiscoveryQuery(appName = Some("hermes-*")),
          timeout = 3.seconds
        )(using appCtx)

        logger.info(s"Found ${hermesServices.size} hermes services:")
        hermesServices.foreach(printService)

        logger.info("")
        logger.info("=== Discovery Test Complete ===")

      case None =>
        logger.error("❌ Dynamic service discovery not enabled!")
        logger.error("")
        logger.error("To enable discovery:")
        logger.error("  1. Edit ~/.config/hermes/bootstrap.conf")
        logger.error("  2. Add: enableDynamicDiscovery = true")
        logger.error("  3. Restart this test")
        logger.error("")
    }
  }

  private def printService(service: ServiceDiscovery.DiscoveryResponse): Unit = {
    logger.info(s"  - ${service.serviceName} (${service.appName})")
    logger.info(s"    Mailbox: ${service.mailboxAddress}")
    logger.info(s"    PID: ${service.unixPid}")
    logger.info(s"    Location: ${service.capabilities.location.user}@${service.capabilities.location.server}")
    logger.info(s"    IP Addresses: ${service.capabilities.location.ipAddresses.mkString(", ")}")
    logger.info(s"    Implemented RPCs: ${service.capabilities.implementsRpc.mkString(", ")}")

    if (service.capabilities.rpcSchemas.nonEmpty) {
      logger.info(s"    RPC Schemas:")
      service.capabilities.rpcSchemas.foreach { case (schema, methods) =>
        logger.info(s"      - $schema: ${methods.mkString(", ")}")
      }
    }

    if (service.metadata.nonEmpty) {
      logger.info(s"    Metadata:")
      service.metadata.foreach { case (k, v) =>
        logger.info(s"      - $k: $v")
      }
    }

    if (service.serviceDiscoveryMapping.nonEmpty) {
      logger.info(s"    Service Discovery Mapping:")
      service.serviceDiscoveryMapping.foreach { case (k, v) =>
        logger.info(s"      - $k: $v")
      }
    }

    logger.info("")
  }
}
