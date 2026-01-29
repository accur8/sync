package a8.examples.hermes

import a8.hermes.bootstrap.{HermesAppConfig, HermesBootstrap, HermesBootstrapConfig}
import a8.shared.app.{AppCtx, BootstrappedIOApp, Bootstrapper}
import a8.shared.zreplace.Resource

/**
 * Simple test app to validate Hermes bootstrap and NATS connection
 */
object HermesTestApp extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit = {
    logger.info("=== Hermes Test App Starting ===")

    // Load bootstrap config from ~/.config/hermes/bootstrap.conf
    logger.info("Loading bootstrap config...")
    val config = HermesBootstrapConfig.load()
    logger.info("Config loaded:")
    logger.info(s"  NATS URL: ${config.natsUrl}")
    logger.info(s"  SSH Key: ${config.sshKeyPath.getOrElse("not configured")}")
    logger.info(s"  Auth Service: ${config.authServiceMailbox.getOrElse("not configured")}")
    logger.info(s"  Named Mailboxes: ${config.namedMailboxes.size} configured")
    config.namedMailboxes.foreach { case (name, addr) =>
      logger.info(s"    $name -> $addr")
    }

    // Bootstrap Hermes
    logger.info("Bootstrapping Hermes...")
    val hermes = Resource.free.run(HermesBootstrap.resource(config, HermesAppConfig()))(using appCtx)

    logger.info("✓ Connected to NATS successfully!")
    logger.info(s"✓ NATS connection status: ${hermes.natsTransport.connection.getStatus}")

    // TODO: Test service discovery
    logger.info("Next steps:")
    logger.info("  - Implement service discovery")
    logger.info("  - Acquire mailbox from godev service")
    logger.info("  - Test RPC communication")

    logger.info("=== Press Ctrl+C to exit ===")
    Thread.sleep(Long.MaxValue)
  }

}
