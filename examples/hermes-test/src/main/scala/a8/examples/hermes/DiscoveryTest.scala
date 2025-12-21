package a8.examples.hermes

import a8.hermes.bootstrap.HermesBootstrap
import a8.hermes.discovery.{ServiceDiscovery, DiscoveryQuery, DiscoveryResponse}
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
 *   2. Start another Hermes service in another terminal to discover it
 */
object DiscoveryTest extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit = {
    Thread.currentThread().setName("discovery-test-main")
    logger.info("Starting Discovery Test...")
    logger.info("")

    val hermes = Resource.free.run(HermesBootstrap.resource())(using appCtx)
    val discovery = hermes.dynamicServiceDiscovery

    logger.info("✓ Dynamic service discovery enabled")
    logger.info("")

    // Test 1: Find all process.v1 services
    logger.info("=== Test 1: Query for process.v1 services ===")
    val processServices = discovery.query(
      DiscoveryQuery(implements_rpc = Seq("process.v1")),
      timeout = 3.seconds
    )(using appCtx)

    logger.info(s"Found ${processServices.size} services with process.v1:")
    processServices.foreach(printService)

    logger.info("")

    // Test 2: Find all services (empty query)
    logger.info("=== Test 2: Query for all services ===")
    val allServices = discovery.query(
      DiscoveryQuery(),
      timeout = 3.seconds
    )(using appCtx)

    logger.info(s"Found ${allServices.size} total services:")
    allServices.foreach(printService)

    logger.info("")

    // Test 3: Query by app name with wildcard
    logger.info("=== Test 3: Query by app name pattern (hermes-*) ===")
    val hermesServices = discovery.query(
      DiscoveryQuery(app_name = Some("hermes-*")),
      timeout = 3.seconds
    )(using appCtx)

    logger.info(s"Found ${hermesServices.size} hermes services:")
    hermesServices.foreach(printService)

    logger.info("")
    logger.info("=== Discovery Test Complete ===")
  }

  private def printService(service: DiscoveryResponse): Unit = {
    val serviceNameStr = if (service.service_name.isEmpty) "<none>" else service.service_name
    logger.info(s"  - $serviceNameStr (${service.app_name})")
    logger.info(s"    Codebase: ${service.codebase_name}")
    logger.info(s"    Mailbox: ${service.mailbox_address}")
    logger.info(s"    PID: ${service.unixPid}")
    logger.info(s"    Location: ${service.capabilities.location.user}@${service.capabilities.location.server}")
    logger.info(s"    IP Addresses: ${service.capabilities.location.ip_addresses.mkString(", ")}")
    logger.info(s"    Implemented RPCs: ${service.capabilities.implements_rpc.mkString(", ")}")

    if (service.capabilities.rpc_schemas.nonEmpty) {
      logger.info(s"    RPC Schemas:")
      service.capabilities.rpc_schemas.foreach { case (schema, methods) =>
        logger.info(s"      - $schema: ${methods.mkString(", ")}")
      }
    }

    if (service.metadata.nonEmpty) {
      logger.info(s"    Metadata:")
      service.metadata.foreach { case (k, v) =>
        logger.info(s"      - $k: $v")
      }
    }

    if (service.service_discovery_mapping.nonEmpty) {
      logger.info(s"    Service Discovery Mapping:")
      service.service_discovery_mapping.foreach { case (k, v) =>
        logger.info(s"      - $k: $v")
      }
    }

    logger.info("")
  }
}
