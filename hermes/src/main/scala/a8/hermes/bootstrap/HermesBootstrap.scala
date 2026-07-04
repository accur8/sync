package a8.hermes.bootstrap

import a8.hermes.core.{Mailbox, MailboxTransport, Uid}
import a8.hermes.{nats, auth}
import a8.hermes.continuum.ContinuumRunnerClient
import a8.hermes.nats.NatsTransport
import a8.hermes.proto.continuum.continuum_rpc.{ProcessCompletedRequest, ProcessStartedRequest}
import a8.hermes.rpc.{RpcServer, RpcClient, StandardHandlers}
import a8.hermes.proto.mailbox.mailbox.{BindIdentityRequest, BindIdentityResponse}
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

  // One processrun per JVM: a process that builds MULTIPLE HermesBootstrap
  // instances (e.g. checkpoint's `checkpoint` + `checkpoint-client` mailboxes)
  // shares one self-generated processUid and announces/pings it exactly once —
  // otherwise each bootstrap registers its own processrun and one worker shows
  // up as N running processes.
  private val jvmProcessUid = new java.util.concurrent.atomic.AtomicReference[String](null)
  private val lifecycleAnnounced = new java.util.concurrent.atomic.AtomicBoolean(false)

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

      // The process uid for this run: A8_PROCESS_UID when runner-spawned (that IS
      // the processrun uid, same as godev leaves); self-generated for a LONG-LIVED
      // server (its step-3c ProcessStarted makes the row real); EMPTY for a
      // short-lived CLI — never link a mailbox to a processrun that won't exist.
      longLived = appConfig.namedMailbox.isDefined ||
        appConfig.mailboxLifecycle == nats.NatsMailboxClient.LifecycleLongLivedDaemon
      envProcessUid = sys.env.getOrElse("A8_PROCESS_UID", sys.env.getOrElse("PROCESS_UID", ""))
      processUid =
        if (envProcessUid.nonEmpty) envProcessUid
        else if (longLived) jvmProcessUid.updateAndGet(u => if (u == null) Uid.uid32() else u)
        else ""

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
            // Link the ephemeral mailbox to the owning processrun when one exists
            // (runner-spawned or a long-lived daemon that announces its own — see
            // the hoisted processUid above). Empty for a bare CLI.
            logger.info(s"Creating non-durable mailbox for client (lifecycle=${appConfig.mailboxLifecycle})...")
            nats.NatsMailboxClient.createNonDurableMailbox(natsTransport, appConfig.mailboxLifecycle, processUid)(using ctx) match {
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

      // Step 3b: Start a mailbox pinger that keeps this mailbox's lastActivity fresh
      // in the KV store so the mesh purge does not reap it while this process is
      // alive-but-quiet. Owned by its own Resource: started here, stopped on release.
      _ <- Resource.acquireRelease {
        val purgeTimeoutMillis =
          java.time.Duration.between(mailbox.metadata.createdAt, mailbox.metadata.expiresAt).toMillis
        nats.NatsMailboxClient.getOrCreateKVForPinger(natsTransport) match {
          case scala.util.Success(adminKV) =>
            val pinger = nats.NatsMailboxClient.startMailboxPingLoop(
              mailbox.metadata.adminKey, adminKV, purgeTimeoutMillis,
            )
            logger.info(s"✓ Started mailbox pinger for ${mailbox.address.value}")
            pinger
          case scala.util.Failure(e) =>
            logger.warn(s"Could not start mailbox pinger for ${mailbox.address.value}: ${e.getMessage}", e)
            (() => ()): java.io.Closeable
        }
      } { pinger =>
        pinger.close()
      }

      // Step 3c: Worker-level continuum process lifecycle. A LONG-LIVED server
      // (named mailbox, or an anonymous mailbox declared long-lived-daemon)
      // announces a processrun (ProcessStarted) and keeps it live with the 30s
      // ping loop — mirroring godev's bootstrap — so an idle server is still
      // visible to continuum's processrun/AWOL view. Short-lived CLIs stay out:
      // their contract is the mailbox pinger + timeouts (step 3b), no processrun.
      // uid: reuse A8_PROCESS_UID when runner-spawned (that IS the processrun
      // uid, same as godev leaves); self-generate for e.g. a systemd-started
      // worker. Best-effort: a lifecycle failure must not stop the server.
      _ <- Resource.acquireRelease {
        if (!longLived) {
          logger.debug("short-lived CLI lifecycle: no processrun announced (mailbox pinger only)")
          None
        } else if (!lifecycleAnnounced.compareAndSet(false, true)) {
          logger.debug(s"processrun $processUid already announced by an earlier bootstrap in this JVM")
          None
        } else {
          try {
            val runnerClient = new ContinuumRunnerClient(natsTransport)
            runnerClient.processStarted(
              ProcessStartedRequest(
                processUid = processUid,
                processPid = ProcessHandle.current().pid().toInt,
                startedAt = Some(ContinuumRunnerClient.nowTimestamp()),
                command = Seq(appConfig.appName.getOrElse("hermes")),
                cwd = System.getProperty("user.dir", ""),
                kind = "bootstrap",
              )
            )
            val pingLoop = runnerClient.startPingLoop(processUid, () => Map.empty)
            logger.info(s"✓ Announced processrun $processUid + started 30s lifecycle ping")
            Some((runnerClient, processUid, pingLoop))
          } catch {
            case e: Exception =>
              logger.warn(s"Could not start continuum process lifecycle: ${e.getMessage}", e)
              None
          }
        }
      } {
        case Some((runnerClient, processUid, pingLoop)) =>
          pingLoop.close()
          try
            runnerClient.processCompleted(
              ProcessCompletedRequest(
                processUid = processUid,
                exitCode = 0,
                completedAt = Some(ContinuumRunnerClient.nowTimestamp()),
              )
            )
          catch {
            case e: Exception =>
              logger.warn(s"processCompleted publish failed for $processUid: ${e.getMessage}")
          }
        case None => ()
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

      // Step 5b: SSH-auth + BindIdentity so the mailbox carries an identity the mesh (and the SQL
      // firewall) can ACL. This belongs in the bootstrapper, not each app: godev's bootstrap.Start
      // binds identity for every worker, and the Scala side has all the pieces (SshAuth, AuthExtension,
      // mailbox.v1.BindIdentity) — it just never wired them in. Gated on config so offline/test runs
      // (no auth service, no ssh key) bootstrap unauthenticated as before. Mirrors WhoAmI.scala.
      authExtension <- bindIdentityResource(bootstrapConfig, mailbox, rpcClient, staticServiceDiscovery)(using ctx)

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
        authExtension = authExtension,
      )
    }
  }

  /**
   * SSH-authenticate and bind the mailbox identity, then start token auto-renewal. Returns the
   * running [[auth.AuthExtension]] (released on shutdown) or None when auth is disabled/unconfigured.
   *
   * Disabled (returns None) when `autoRenewAuth` is false, or `sshKeyPath`/`authServiceMailbox` is
   * unset — this keeps offline and unit-test bootstraps working without an auth service. The
   * `mailbox` service name must be resolvable via static service discovery for the BindIdentity call.
   */
  private def bindIdentityResource(
    bootstrapConfig: HermesBootstrapConfig,
    mailbox: Mailbox,
    rpcClient: RpcClient,
    staticServiceDiscovery: StaticServiceDiscovery,
  )(using ctx: Ctx): Resource[Option[auth.AuthExtension]] = {

    val enabled = bootstrapConfig.autoRenewAuth &&
      bootstrapConfig.sshKeyPath.isDefined &&
      bootstrapConfig.authServiceMailbox.isDefined

    if (!enabled) {
      logger.info(
        s"Mailbox auth disabled (autoRenewAuth=${bootstrapConfig.autoRenewAuth}, " +
          s"sshKeyPath=${bootstrapConfig.sshKeyPath.isDefined}, authServiceMailbox=${bootstrapConfig.authServiceMailbox.isDefined}) " +
          "— mailbox will be unauthenticated"
      )
      Resource.acquireRelease(Option.empty[auth.AuthExtension])(_ => ())
    } else {
      Resource.acquireRelease[Option[auth.AuthExtension]] {
        val authMailbox = Mailbox.MailboxAddress(bootstrapConfig.authServiceMailbox.get)
        val sshAuthConfig =
          auth.SshAuth.Config(
            sshPrivateKeyPath = bootstrapConfig.sshKeyPath.get,
            authServiceMailbox = authMailbox,
          )

        // 1. SSH auth -> auth token
        val authResult =
          auth.SshAuth.authenticate(sshAuthConfig, rpcClient) match {
            case scala.util.Success(r) =>
              logger.info("✓ SSH authentication successful")
              r
            case scala.util.Failure(e) =>
              throw new RuntimeException(s"SSH authentication failed: ${e.getMessage}", e)
          }

        // 2. Bind the token to this mailbox (mailbox.v1.BindIdentity)
        val mailboxServiceMailbox = staticServiceDiscovery.getMailbox("mailbox")
        val bindResp =
          rpcClient.callTyped[BindIdentityRequest, BindIdentityResponse](
            targetMailbox = mailboxServiceMailbox,
            endpoint = "mailbox.v1.BindIdentity",
            request = BindIdentityRequest(authToken = authResult.authToken),
            timeout = Some(scala.concurrent.duration.FiniteDuration(10, "seconds")),
          ).getOrElse {
            throw new RuntimeException("BindIdentity RPC failed: no response from mailbox service")
          }
        if (!bindResp.success) {
          throw new RuntimeException(s"BindIdentity failed: ${bindResp.message}")
        }
        logger.info(s"✓ Auth token bound to mailbox ${mailbox.address.value} (user_uid: ${bindResp.userUid})")

        // 3. Start background token renewal
        val authExt = new auth.AuthExtension(mailbox, sshAuthConfig, rpcClient, auth.AuthExtension.Config())
        authExt.start(authResult.expiresAt)
        Some(authExt)
      } { authExtOpt =>
        authExtOpt.foreach(_.stop())
      }
    }
  }

  /**
   * Resolve service->mailbox name mappings. Always queries the naming service
   * (naming.v1.GetEnvironment) over the already-connected NATS for the live mappings, matching
   * godev's `QueryNamingService`. When `namingEnvironment` is unset (the common case) an empty
   * environment name is sent, so the server returns its default name set and clients don't have
   * to get an environment name right; only clients needing a specific set configure
   * `namingEnvironment`. Static `namedMailboxes` from the config are merged as overrides, and any
   * query failure falls back to the static mappings so bootstrap still works offline.
   */
  private def resolveNameMappings(config: HermesBootstrapConfig, natsTransport: NatsTransport): Map[String, String] = {
    // None => empty environment name => server's default name set
    val env = config.namingEnvironment.getOrElse("")
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
