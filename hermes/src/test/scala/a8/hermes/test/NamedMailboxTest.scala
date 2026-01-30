package a8.hermes.test

import a8.hermes.bootstrap.HermesBootstrap
import a8.hermes.core.Mailbox.MailboxAddress
import a8.shared.app.{AppCtx, BootstrappedIOApp}

object NamedMailboxTest extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit = {
    // Use a FIXED name so we can test fetching the same mailbox on subsequent runs
    val mailboxName = "test-named-mailbox-fixed"

    logger.info("=" * 70)
    logger.info(s"Testing fetchOrCreateNamedMailbox: $mailboxName")
    logger.info("This will CREATE on first run, then FETCH on subsequent runs")
    logger.info("=" * 70)

    // Load Hermes config
    val hermesBootstrapConfig = a8.hermes.bootstrap.HermesBootstrapConfig.load()
    val hermesAppConfig = a8.hermes.bootstrap.HermesAppConfig(
      namedMailbox = Some(mailboxName),
      appName = Some("named-mailbox-test")
    )

    // First run - should CREATE the mailbox
    logger.info("\n--- First run: Creating named mailbox ---")
    val hermes1 = HermesBootstrap.resource(hermesBootstrapConfig, hermesAppConfig).unwrap
    logger.info(s"✓ Created mailbox: ${hermes1.mailbox.address.value}")
    logger.info(s"  AdminKey: ${hermes1.mailbox.metadata.adminKey.value}")
    val originalAdminKey = hermes1.mailbox.metadata.adminKey.value

    // Second run - should FETCH the existing mailbox
    logger.info("\n--- Second run: Fetching existing named mailbox ---")
    val hermes2 = HermesBootstrap.resource(hermesBootstrapConfig, hermesAppConfig).unwrap
    logger.info(s"✓ Fetched mailbox: ${hermes2.mailbox.address.value}")
    logger.info(s"  AdminKey: ${hermes2.mailbox.metadata.adminKey.value}")
    val fetchedAdminKey = hermes2.mailbox.metadata.adminKey.value

    // Verify it's the same mailbox
    if (originalAdminKey == fetchedAdminKey) {
      logger.info("\n" + "=" * 70)
      logger.info("✓✓✓ SUCCESS: Fetched the SAME mailbox!")
      logger.info("=" * 70)
    } else {
      logger.error("\n" + "=" * 70)
      logger.error("✗✗✗ FAILED: Got DIFFERENT mailbox!")
      logger.error(s"Original: $originalAdminKey")
      logger.error(s"Fetched:  $fetchedAdminKey")
      logger.error("=" * 70)
      System.exit(1)
    }

    logger.info("\nTest completed successfully!")
  }

}
