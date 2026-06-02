package a8.hermes.bootstrap

import a8.hermes.core.{Mailbox, MailboxTransport}
import a8.hermes.{nats, auth}
import a8.hermes.nats.NatsTransport
import a8.hermes.rpc.{RpcServer, RpcClient, StandardHandlers}
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
    bootstrapConfig: HermesBootstrapConfig,
    appConfig: HermesAppConfig,
    natsTransport: NatsTransport,
    mailbox: Mailbox,
    rpcServer: RpcServer,
    rpcClient: RpcClient,
    staticServiceDiscovery: StaticServiceDiscovery,
    dynamicServiceDiscovery: ServiceDiscovery,
    processUid: String = "",
    authExtension: Option[auth.AuthExtension] = None,
  )

  /**
   * Create a HermesBootstrap resource using default bootstrap config and no app config
   */
  def resource()(using ctx: Ctx): Resource[Components] = {
    val bootstrapConfig = HermesBootstrapConfig.load()
    resource(bootstrapConfig, HermesAppConfig())(using ctx)
  }

  /**
   * Create a HermesBootstrap resource with explicit bootstrap config and app config
   */
  def resource(bootstrapConfig: HermesBootstrapConfig, appConfig: HermesAppConfig)(using ctx: Ctx): Resource[Components] = {
    for {
      // Step 1: Connect to NATS
      natsTransport <- NatsTransport.resource(
        NatsTransport.Config(
          natsUrl = bootstrapConfig.natsUrl,
          appName = appConfig.appName,
        )
      )

      // Step 2: Service Discovery (serviceName → mailbox address).
      // When the config sets `namingEnvironment`, resolve mappings dynamically via the naming
      // service over NATS (naming.v1.GetEnvironment), matching godev's bootstrap. Static
      // `namedMailboxes` from the config are the fallback / overrides.
      resolvedMappings = resolveNameMappings(bootstrapConfig, natsTransport)
      staticServiceDiscovery = new StaticServiceDiscovery(resolvedMappings)
      _ = logger.info(s"Service discovery initialized with ${resolvedMappings.size} mappings: ${resolvedMappings.keys.mkString(", ")}")

      // Step 3: Create/Acquire Mailbox
      // Use named mailbox if configured, otherwise create ephemeral
      mailbox <- Resource.acquireRelease {
        appConfig.namedMailbox match {
          case Some(name) =>
            logger.info(s"Creating named mailbox: $name")
            nats.NatsMailboxClient.fetchOrCreateNamedMailbox(
              address = Mailbox.MailboxAddress(name),
              natsTransport = natsTransport
            )(using ctx) match {
              case scala.util.Success(mbox) =>
                logger.info(s"✓ Created named mailbox: ${mbox.address.value}")
                mbox
              case scala.util.Failure(e) =>
                logger.error(s"Failed to create named mailbox: ${e.getMessage}", e)
                throw new RuntimeException(s"Failed to create named mailbox: ${e.getMessage}", e)
            }
          case None =>
            logger.info("Creating non-durable mailbox for client...")
            nats.NatsMailboxClient.createNonDurableMailbox(natsTransport)(using ctx) match {
              case scala.util.Success(mbox) =>
                logger.info(s"✓ Created mailbox: ${mbox.address.value}")
                mbox
              case scala.util.Failure(e) =>
                logger.error(s"Failed to create mailbox: ${e.getMessage}", e)
                throw new RuntimeException(s"Failed to create mailbox: ${e.getMessage}", e)
            }
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

        // Register standard handlers (process.v1.Ping, discovery.v1.*)
        StandardHandlers.registerAll(server, mailbox.address.value)
        logger.info("✓ Standard RPC handlers registered (process.v1, discovery.v1)")

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

      // Resolve stable processUid once at bootstrap time (before service discovery)
      processUid = sys.env.getOrElse("A8_PROCESS_UID",
        sys.env.getOrElse("PROCESS_UID", java.util.UUID.randomUUID().toString.replace("-", "").take(20)))

      // Step 6: Start dynamic service discovery (always enabled)
      dynamicServiceDiscovery <- {
        logger.info("Starting dynamic service discovery...")
        Resource.acquireRelease {
          val discoveryConfig = ServiceDiscovery.defaultConfig(
            mailbox = mailbox,
            transport = natsTransport,
            rpcServer = Some(rpcServer),
            appName = appConfig.appName.getOrElse("hermes-scala"),
            serviceName = appConfig.appName,
            staticServiceDiscovery = Some(staticServiceDiscovery),
          )

          // Collect A8_* environment variables for metadata
          val a8Metadata = ServiceDiscovery.readA8EnvironmentMetadata()

          // Build extended metadata with process info
          val extendedMetadata = Map(
            "programming_language" -> "scala",
            "cwd"                  -> sys.props.getOrElse("user.dir", ""),
            "cli"                  -> (java.lang.ProcessHandle.current().info().command().orElse("") +
                                       " " + sys.props.getOrElse("sun.java.command", "")).trim,
          ) ++ a8Metadata

          val configWithMetadata = discoveryConfig.copy(
            metadata = discoveryConfig.metadata ++ a8Metadata,
            extendedMetadata = extendedMetadata,
            processUid = processUid,
          )

          val discovery = new ServiceDiscovery(configWithMetadata)
          discovery.start()(using ctx)
          discovery.register()(using ctx)  // Auto-register this process
          logger.info(s"✓ Dynamic service discovery started and registered")
          logger.info(s"  Process UID: $processUid")
          if (a8Metadata.nonEmpty) {
            logger.info(s"  Metadata: ${a8Metadata.keys.mkString(", ")}")
          }
          discovery
        } { discovery =>
          discovery.stop()
        }
      }

    } yield {
      logger.info("=== Hermes Bootstrap Complete ===")
      logger.info(s"  NATS: Connected")
      logger.info(s"  Mailbox: ${mailbox.address.value}")
      logger.info(s"  RPC Server: Running")
      logger.info(s"  RPC Client: Running")
      logger.info(s"  Static Service Discovery: ${bootstrapConfig.namedMailboxes.size} named mailboxes")
      logger.info(s"  Dynamic Service Discovery: Enabled (always)")
      // Note: Auth extension not started here - applications should start it after SSH authentication

      Components(
        bootstrapConfig = bootstrapConfig,
        appConfig = appConfig,
        natsTransport = natsTransport,
        mailbox = mailbox,
        rpcServer = rpcServer,
        rpcClient = rpcClient,
        staticServiceDiscovery = staticServiceDiscovery,
        dynamicServiceDiscovery = dynamicServiceDiscovery,
        processUid = processUid,
        authExtension = None,  // Will be set by application if needed
      )
    }
  }

  /**
   * Resolve service->mailbox name mappings. If the config sets `namingEnvironment`, query the naming
   * service (naming.v1.GetEnvironment) over the already-connected NATS for the live mappings, matching
   * godev's `QueryNamingService`; static `namedMailboxes` from the config are merged as fallback/overrides.
   * Any query failure falls back to the static mappings so bootstrap still works offline.
   */
  private def resolveNameMappings(config: HermesBootstrapConfig, natsTransport: NatsTransport): Map[String, String] = {
    config.namingEnvironment match {
      case None =>
        config.namedMailboxes
      case Some(env) =>
        val dynamic =
          try queryNamingService(natsTransport, env)
          catch {
            case e: Throwable =>
              logger.warn(s"naming service query failed for env '$env'; falling back to static nameMappings", e)
              Map.empty[String, String]
          }
        // static config entries win as explicit overrides
        dynamic ++ config.namedMailboxes
    }
  }

  /** Raw naming.v1.GetEnvironment request over NATS, returning service->mailbox mappings. */
  private def queryNamingService(natsTransport: NatsTransport, environment: String): Map[String, String] = {
    val reqJson = s"""{"environment_name":${quoteJson(environment)}}"""
    val replyOpt =
      Option(
        natsTransport.connection.request(
          "naming.v1.GetEnvironment",
          reqJson.getBytes(java.nio.charset.StandardCharsets.UTF_8),
          java.time.Duration.ofSeconds(5),
        )
      )
    import a8.shared.SharedImports.json
    import a8.shared.json.ast.{JsObj, JsStr, JsBool}
    replyOpt match {
      case None =>
        logger.warn(s"naming service: no response for env '$environment'")
        Map.empty
      case Some(msg) =>
        val body = new String(msg.getData, java.nio.charset.StandardCharsets.UTF_8)
        json.unsafeParse(body) match {
          case obj: JsObj =>
            val found = obj.values.get("found").collect { case JsBool(b) => b }.getOrElse(false)
            if (!found) {
              val errMsg =
                obj.values.get("error_message").collect { case JsStr(s) => s }.getOrElse(s"environment '$environment' not found")
              logger.warn(s"naming service: $errMsg")
              Map.empty
            } else {
              obj.values.get("name_mappings") match {
                case Some(JsObj(mappings)) =>
                  mappings.collect { case (k, JsStr(v)) => k -> v }.toMap
                case _ =>
                  Map.empty
              }
            }
          case _ =>
            logger.warn(s"naming service: unexpected response shape: $body")
            Map.empty
        }
    }
  }

  private def quoteJson(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

}
