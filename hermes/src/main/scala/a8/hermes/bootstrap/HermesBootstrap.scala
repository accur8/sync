package a8.hermes.bootstrap

import a8.hermes.core.{Mailbox, MailboxTransport}
import a8.hermes.{nats, auth}
import a8.hermes.nats.NatsTransport
import a8.hermes.rpc.{RpcServer, RpcClient}
import a8.hermes.discovery.ServiceDiscovery
import a8.shared.app.Ctx
import a8.common.logging.Logging
import a8.shared.zreplace.Resource

/**
 * Main bootstrap class for Hermes/NATS integration.
 * Follows the godev bootstrap pattern with Scala idioms.
 *
 * Bootstrap Order (from godev specs):
 * 1. Connect to NATS
 * 2. Service discovery (static {env}.{service} pattern)
 * 3. Create/acquire mailbox (named mailbox from config for now)
 * 4. Start RPC server and client
 * 5. Optionally register with dynamic service discovery
 *
 * Usage:
 * {{{
 *   val bootstrap = HermesBootstrap.resource()
 *   Resource.free.run(bootstrap) { hermes =>
 *     // Use hermes components
 *     hermes.rpcServer.register(myHandler)
 *   }
 * }}}
 */
object HermesBootstrap extends Logging {

  /**
   * Components created during bootstrap
   */
  case class Components(
    config: HermesBootstrapConfig,
    natsTransport: NatsTransport,
    mailbox: Mailbox,
    rpcServer: RpcServer,
    rpcClient: RpcClient,
    staticServiceDiscovery: StaticServiceDiscovery,
    dynamicServiceDiscovery: Option[ServiceDiscovery] = None,
    authExtension: Option[auth.AuthExtension] = None,
  )

  /**
   * Create a HermesBootstrap resource using default config location
   */
  def resource()(using ctx: Ctx): Resource[Components] = {
    val config = HermesBootstrapConfig.load()
    resource(config)(using ctx)
  }

  /**
   * Create a HermesBootstrap resource with explicit config
   */
  def resource(config: HermesBootstrapConfig)(using ctx: Ctx): Resource[Components] = {
    for {
      // Step 1: Connect to NATS
      natsTransport <- NatsTransport.resource(
        NatsTransport.Config(
          natsUrl = config.natsUrl,
          connectionName = Some("hermes-scala-client"),
        )
      )

      // Step 2: Service Discovery (static resolution using namedMailboxes)
      // Simple map lookup: serviceName → mailbox address
      // Example: config.namedMailboxes = {"nefario": "nefario-rpc"}
      staticServiceDiscovery = new StaticServiceDiscovery(config.namedMailboxes)
      _ = logger.info(s"Static service discovery initialized with ${config.namedMailboxes.size} named mailboxes")

      // Step 3: Create/Acquire Mailbox
      // For direct NATS transport, create a non-durable mailbox
      mailbox <- Resource.acquireRelease {
        logger.info("Creating non-durable mailbox for client...")
        nats.NatsMailboxClient.createNonDurableMailbox(natsTransport)(using ctx) match {
          case scala.util.Success(mbox) =>
            logger.info(s"✓ Created mailbox: ${mbox.address.value}")
            mbox
          case scala.util.Failure(e) =>
            logger.error(s"Failed to create mailbox: ${e.getMessage}", e)
            throw new RuntimeException(s"Failed to create mailbox: ${e.getMessage}", e)
        }
      } { mbox =>
        logger.debug(s"Releasing mailbox: ${mbox.address.value}")
        // Mailbox cleanup happens automatically via NATS TTL
      }

      // Step 4: Start RPC Server
      rpcServer <- Resource.acquireRelease {
        logger.info("Starting RPC server...")
        val server = new RpcServer(
          RpcServer.Config(
            mailbox = mailbox,
            transport = natsTransport,
            parallelism = 10,
          )
        )
        server.start()(using ctx)
        logger.info(s"✓ RPC server started on mailbox: ${mailbox.address.value}")
        server
      } { server =>
        logger.info("Stopping RPC server...")
        server.stop()
      }

      // Step 5: Start RPC Client
      rpcClient <- RpcClient.resource(
        RpcClient.Config(
          mailbox = mailbox,
          transport = natsTransport,
          defaultTimeout = scala.concurrent.duration.FiniteDuration(30, "seconds"),
        )
      )(using ctx)

      // Step 6: Optionally start dynamic service discovery
      dynamicServiceDiscovery <- if (config.enableDynamicDiscovery.getOrElse(false)) {
        logger.info("Starting dynamic service discovery...")
        Resource.acquireRelease {
          val discoveryConfig = ServiceDiscovery.defaultConfig(
            mailbox = mailbox,
            transport = natsTransport,
            rpcServer = Some(rpcServer),
            appName = config.appName.getOrElse("hermes-scala"),
            serviceName = config.appName,
            staticServiceDiscovery = Some(staticServiceDiscovery),
          )

          // Collect A8_* environment variables for metadata
          val a8Metadata = ServiceDiscovery.readA8EnvironmentMetadata()

          // Merge with any existing metadata
          val configWithMetadata = discoveryConfig.copy(
            metadata = discoveryConfig.metadata ++ a8Metadata
          )

          val discovery = new ServiceDiscovery(configWithMetadata)
          discovery.start()(using ctx)
          discovery.register()(using ctx)  // Auto-register this process
          logger.info(s"✓ Dynamic service discovery started and registered")
          if (a8Metadata.nonEmpty) {
            logger.info(s"  Metadata: ${a8Metadata.keys.mkString(", ")}")
          }
          Some(discovery)
        } { discovery =>
          discovery.foreach(_.stop())
        }
      } else {
        Resource.acquireRelease(None: Option[ServiceDiscovery])(_ => ())
      }

    } yield {
      logger.info("=== Hermes Bootstrap Complete ===")
      logger.info(s"  NATS: Connected")
      logger.info(s"  Mailbox: ${mailbox.address.value}")
      logger.info(s"  RPC Server: Running")
      logger.info(s"  RPC Client: Running")
      logger.info(s"  Static Service Discovery: ${config.namedMailboxes.size} named mailboxes")
      logger.info(s"  Dynamic Service Discovery: ${if (dynamicServiceDiscovery.isDefined) "Enabled" else "Disabled"}")
      // Note: Auth extension not started here - applications should start it after SSH authentication

      Components(
        config = config,
        natsTransport = natsTransport,
        mailbox = mailbox,
        rpcServer = rpcServer,
        rpcClient = rpcClient,
        staticServiceDiscovery = staticServiceDiscovery,
        dynamicServiceDiscovery = dynamicServiceDiscovery,
        authExtension = None,  // Will be set by application if needed
      )
    }
  }

}
