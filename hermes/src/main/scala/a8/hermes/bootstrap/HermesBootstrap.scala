package a8.hermes.bootstrap

import a8.hermes.core.{Mailbox, Uid}
import a8.hermes.{nats, auth, ws}
import a8.hermes.continuum.ContinuumRunnerClient
import a8.hermes.nats.NatsTransport
import a8.hermes.proto.continuum.continuum_rpc.{ProcessCompletedRequest, ProcessStartedRequest}
import a8.hermes.proto.discovery.discovery.DiscoveryResponse
import a8.hermes.rpc.{RpcServer, RpcClient, StandardHandlers}
import a8.hermes.proto.mailbox.mailbox.{BindIdentityRequest, BindIdentityResponse}
import a8.hermes.proto.auth.auth.{GetUserInfoForSelfRequest, GetUserInfoForSelfResponse}
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
   * Create a HermesBootstrap resource with explicit bootstrap config and app config.
   *
   * discoveryBuildInfo is the APP's own build identity for the processrun announce.
   * Absent, the announce falls back to BuildInfoReader — a single classpath lookup of
   * META-INF/version-details.properties, which with multiple stamped jars on the
   * classpath is decided by CLASSPATH ORDER: checkpoint's live announce carried
   * a8-hermes-proto's stamp (the library it links, built on a laptop) instead of its own
   * build (BUG-20260802-checkpoint-does-not-self-report). An app that knows its identity
   * should say so.
   */
  def resource(
    bootstrapConfig: HermesBootstrapConfig,
    appConfig: HermesAppConfig,
    discoveryBuildInfo: Option[a8.hermes.proto.discovery.discovery.BuildInfo] = None,
    // The CODEBASE marker, matching the Go convention (godev fills codebaseName="godev"
    // on every announce): which repo built this binary, distinct from appName (a8-cli,
    // mesh and worker are three apps of ONE codebase). Empty when the app does not say —
    // never guessed from the classpath, which is how buildInfo lied (see above).
    discoveryCodebaseName: Option[String] = None,
    // jobName/jobKind declare that this process IS a long-lived service that should own a
    // continuum JOB row, for a process no scheduler hands one to. Both or neither: the server's
    // ResolveJobUid passes kind through VERBATIM into job.kind, so a name without a kind mints
    // jobs of kind "bootstrap" — a process-axis label landing on the job axis.
    //
    // The name must be SPECIFIC and STABLE per worker. ResolveJobUid CREATES what it cannot
    // resolve, and a generic announce name has already caused an outage once (godev's own doc
    // records one minting a monitor-less supervisor job and freezing monitor checkers).
    // BUG-20260802-checkpoint-job-row-still-shows-the-watcher-run.
    jobName: Option[String] = None,
    jobKind: Option[String] = None,
  )(using ctx: Ctx): Resource[Components] = {
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

      // Step 3: Acquire the mailbox — THREE ways, in priority order, all ending with a
      // NATS-serving Mailbox (run over direct NATS regardless of how it was acquired):
      //   1. DURABLE ATTACH (config carries namedMailboxKeys): attach to a pre-provisioned
      //      named mailbox by its keys — no create, no mesh.mailbox.v1.fetch. The config file
      //      is the gate. (FEATURE-20260725-durable-named-mailbox-out-of-band-config)
      //   2. WS-MINT (httpUrl set): bootstrap an ephemeral mailbox over the WS gateway
      //      (ClientHello inline login), take its keys, attach a NATS mailbox to them, drop the
      //      WS. No mesh.mailbox.v1.create. (FEATURE-20260724-remove-mesh-control-req-reply-...)
      //   3. LEGACY CREATE (neither): the old fetchOrCreate/createNonDurable over
      //      mesh.mailbox.v1.* — the fallback until httpUrl / namedMailboxKeys are deployed.
      mailbox <- Resource.acquireRelease {
        acquireMailbox(bootstrapConfig, appConfig, processUid, natsTransport)(using ctx)
      } { mbox =>
        logger.debug(s"Releasing mailbox: ${mbox.address.value}")
        // Mailbox cleanup happens automatically via NATS TTL
      }

      // Step 3b: Start a mailbox pinger that keeps this mailbox's lastActivity fresh via the
      // mesh mailbox-records endpoints (mesh.mailbox.v1.update). ONLY on the LEGACY create path:
      // an ATTACHED mailbox (durable-attach or WS-mint) does not touch records at all — its
      // aliveness lives on the PROCESSRUN (step 3c's ping), so the mailbox-record pinger is both
      // unnecessary and would call the surface this drive is retiring. Owned by its own Resource.
      _ <- Resource.acquireRelease {
        if (mailboxWasAttached(bootstrapConfig)) {
          logger.debug("mailbox was attached (records-free) — skipping the mesh.mailbox.v1 pinger; aliveness is on the processrun")
          (() => ()): java.io.Closeable
        } else {
          val purgeTimeoutMillis =
            java.time.Duration.between(mailbox.metadata.createdAt, mailbox.metadata.expiresAt).toMillis
          val pinger = nats.NatsMailboxClient.startMailboxPingLoop(
            mailbox.metadata.adminKey, natsTransport, purgeTimeoutMillis,
          )
          logger.info(s"✓ Started mailbox pinger for ${mailbox.address.value}")
          pinger
        }
      } { pinger =>
        pinger.close()
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

      // Step 5c: Worker-level continuum process lifecycle. A LONG-LIVED server
      // (named mailbox, or an anonymous mailbox declared long-lived-daemon)
      // announces a processrun (ProcessStarted) and keeps it live with the 30s
      // ping loop — mirroring godev's bootstrap — so an idle server is still
      // visible to continuum's processrun/AWOL view. Short-lived CLIs stay out:
      // their contract is the mailbox pinger + timeouts (step 3b), no processrun.
      // uid: reuse A8_PROCESS_UID when runner-spawned (that IS the processrun
      // uid, same as godev leaves); self-generate for e.g. a systemd-started
      // worker. Best-effort: a lifecycle failure must not stop the server.
      //
      // THIS RUNS AFTER AUTH (step 5b), AND THAT ORDER IS THE POINT. It used to be step 3c,
      // before bindIdentity — so the process announced itself before it knew who it was, and
      // could not name its worker. Every checkpoint processrun landed with an empty workerUid,
      // which left ResolveJobUid unable to resolve a job (it keys on (worker, name), and
      // job.workeruid is NOT NULL) and a8-checkpoint.service reading "does not self-report" in
      // the build report while its own build-stamped announce sat in the table.
      //
      // The contract is AUTH FIRST, THEN ANNOUNCE. Do not move this back above step 5b.
      // Note this is independent of how the mailbox was acquired: minting vs durable-attach is
      // an ADDRESSABILITY choice and has nothing to do with identifying the run.
      // BUG-20260802-checkpoint-job-row-still-shows-the-watcher-run.
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
            // The worker this run belongs to, resolved from the identity we just
            // authenticated as (step 5b). Empty when auth is disabled/unconfigured — the
            // offline and unit-test bootstraps — which announces exactly as before.
            //
            // Asked rather than asserted: this is the SAME auth.GetUserInfoForSelf the Go
            // leaves use in workerUid(), so the value is the server's view of who we are, not a
            // claim we invented. Best-effort by design — a lifecycle detail must never stop a
            // server from starting, and a run with no worker is still better than no run.
            val resolvedWorkerUid: String =
              if (authExtension.isEmpty) ""
              else
                try {
                  rpcClient
                    .callTyped[GetUserInfoForSelfRequest, GetUserInfoForSelfResponse](
                      targetMailbox = staticServiceDiscovery.getMailbox("auth"),
                      // "auth.GetUserInfoForSelf" — the v-less canonical path from godev's
                      // pkg/rpc/auth registry. This said "auth.v2..." until 2026-08-02, which
                      // NO handler serves: every announce resolved no worker and the error was
                      // invisible below the 10s timeout ("no handler" returns fast, the catch
                      // swallowed it into the empty-workerUid path).
                      endpoint = "auth.GetUserInfoForSelf",
                      request = GetUserInfoForSelfRequest(),
                      timeout = Some(scala.concurrent.duration.FiniteDuration(10, "seconds")),
                    )(using ctx, summon)
                    .map(_.workerUid)
                    .getOrElse("")
                } catch {
                  case e: Exception =>
                    logger.warn(s"could not resolve workerUid for the processrun announce: ${e.getMessage}")
                    ""
                }
            if (resolvedWorkerUid.nonEmpty)
              logger.debug(s"processrun announce carries workerUid $resolvedWorkerUid")
            else
              logger.warn("processrun announce has NO workerUid — the run will not link to a job")

            // Self-detect the process manager (systemd unit / supervisord program)
            // so this processrun auto-correlates with its control handle. Empty when
            // unmanaged (bare CLI / macOS / non-systemd) — normal, not an error.
            val pm = ProcManager.detect()
            if (pm.managed)
              logger.info(s"process manager: ${pm.manager} unit=${pm.unit}${if (pm.scope.nonEmpty) s" scope=${pm.scope}" else ""}")
            else
              logger.debug("process manager: none detected (unmanaged / non-systemd)")
            runnerClient.processStarted(
              ProcessStartedRequest(
                processUid = processUid,
                processPid = ProcessHandle.current().pid().toInt,
                startedAt = Some(ContinuumRunnerClient.nowTimestamp()),
                command = Seq(appConfig.appName.getOrElse("hermes")),
                cwd = System.getProperty("user.dir", ""),
                // kind is the JOB-axis value when this process declares itself a service, and
                // the process-axis "bootstrap" otherwise. ResolveJobUid passes it VERBATIM into
                // job.kind, so declaring a jobName without a jobKind would mint jobs of kind
                // "bootstrap" — which is why the two params travel together.
                kind = jobKind.filter(_.nonEmpty).getOrElse("bootstrap"),
                // jobName + workerUid are what let the server resolve-or-create the job and
                // link this run to it. Both must be present: ResolveJobUid keys on
                // (worker, name). Absent, the run is announced unlinked exactly as before.
                jobName = jobName.getOrElse(""),
                workerUid = resolvedWorkerUid,
                processManager = pm.manager,
                processManagerUnit = pm.unit,
                processManagerScope = pm.scope,
                // Structured build identity (FEATURE-20260709). The Go side rides it on
                // the embedded discovery response; we do the same. appName is FILLED —
                // the census keys on discovery.appName, and leaving it empty put every
                // Scala service's announce in the blank bucket where nothing could see
                // it (checkpoint read as "does not self-report" while its live announce
                // sat right there). buildInfo prefers the app's own identity over the
                // classpath-order lottery — see resource()'s doc.
                discovery = Some(DiscoveryResponse(
                  appName = appConfig.appName.getOrElse("hermes"),
                  codebaseName = discoveryCodebaseName.getOrElse(""),
                  buildInfo = Some(discoveryBuildInfo.getOrElse(BuildInfoReader.buildInfo)),
                )),
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

          // Build extended metadata with process info. The process_manager* keys
          // mirror the first-class ProcessStartedRequest fields for map-based
          // consumers (empty map when unmanaged, so a bare CLI adds nothing).
          val extendedMetadata = Map(
            "programming_language" -> "scala",
            "cwd"                  -> sys.props.getOrElse("user.dir", ""),
            "cli"                  -> (java.lang.ProcessHandle.current().info().command().orElse("") +
                                       " " + sys.props.getOrElse("sun.java.command", "")).trim,
          ) ++ ProcManager.detect().metadata ++ a8Metadata

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

    // Convention over configuration: the config keys are OVERRIDES, not requirements.
    // Unset, the key defaults to the user's standard identity (~/.ssh/id_ed25519, only
    // when the file exists) and the auth mailbox to the "auth" discovery mapping this
    // bootstrap already resolved — the same resolution every external login uses.
    //
    // Before these defaults, auth here was opt-in — and apps that authenticate OUTSIDE
    // hermes (checkpoint's ContinuumClient logs in after this resource completes) ran
    // the once-per-JVM processrun announce with authExtension empty: no workerUid, no
    // job link, and a ProcessStart published unauthenticated, which the auth-first
    // contract forbids. Auth-first is now the default, not an opt-in.
    val keyPath: Option[String] = bootstrapConfig.sshKeyPath.orElse(defaultSshKeyPath)
    val authMailboxOpt: Option[Mailbox.MailboxAddress] =
      bootstrapConfig.authServiceMailbox
        .orElse(staticServiceDiscovery.getAllMailboxes.get("auth"))
        .map(Mailbox.MailboxAddress(_))
    // Both keys explicitly configured = the operator ASKED for auth, so a failure is
    // fatal exactly as before. Defaulted = best-effort: a missing enrollment or a dead
    // auth service degrades to the old unauthenticated bootstrap with a warning,
    // instead of bricking every hermes app that happens to have a key on disk.
    val explicitlyConfigured =
      bootstrapConfig.sshKeyPath.isDefined && bootstrapConfig.authServiceMailbox.isDefined

    val enabled = bootstrapConfig.autoRenewAuth && keyPath.isDefined && authMailboxOpt.isDefined

    if (!enabled) {
      logger.info(
        s"Mailbox auth disabled (autoRenewAuth=${bootstrapConfig.autoRenewAuth}, " +
          s"sshKey=${keyPath.isDefined}, authServiceMailbox=${authMailboxOpt.isDefined}; " +
          "no config and no defaultable key/mapping) — mailbox will be unauthenticated"
      )
      Resource.acquireRelease(Option.empty[auth.AuthExtension])(_ => ())
    } else {
      if (!explicitlyConfigured)
        logger.info(s"Mailbox auth enabled by convention (key ${keyPath.get}, auth mailbox ${authMailboxOpt.get.value})")
      Resource.acquireRelease[Option[auth.AuthExtension]] {
        try {
          val authMailbox = authMailboxOpt.get
          val sshAuthConfig =
            auth.SshAuth.Config(
              sshPrivateKeyPath = keyPath.get,
              authServiceMailbox = authMailbox,
            )

          // 1. SSH auth -> auth token. RETRIED: the first RPC on a freshly-started
          // client can time out while a later attempt succeeds in milliseconds
          // (measured on checkpoint 2026-08-02: LoginBegin at 30.283 timed out at
          // 40.397, the retry at 40.674 completed at 40.924). The Go auth extension
          // retries 5 times for the same reason; one 10s shot here made bootstrap
          // auth flaky in exactly the place the processrun announce depends on it.
          val authResult = {
            val maxAttempts = 3
            @scala.annotation.tailrec
            def attempt(n: Int): auth.SshAuth.AuthResult =
              auth.SshAuth.authenticate(sshAuthConfig, rpcClient) match {
                case scala.util.Success(r) =>
                  logger.info("✓ SSH authentication successful")
                  r
                case scala.util.Failure(e) if n < maxAttempts =>
                  logger.warn(s"SSH authentication attempt $n/$maxAttempts failed, retrying: ${e.getMessage}")
                  Thread.sleep(2000L * n)
                  attempt(n + 1)
                case scala.util.Failure(e) =>
                  throw new RuntimeException(s"SSH authentication failed after $maxAttempts attempts: ${e.getMessage}", e)
              }
            attempt(1)
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
        } catch {
          case e: Exception if !explicitlyConfigured =>
            logger.warn(
              "convention-default mailbox auth failed — continuing UNAUTHENTICATED " +
                s"(set sshKeyPath + authServiceMailbox in bootstrap.conf to make this fatal): ${e.getMessage}"
            )
            None
        }
      } { authExtOpt =>
        authExtOpt.foreach(_.stop())
      }
    }
  }

  /** The user's standard SSH identity, when present — the convention half of the
    * bindIdentityResource defaults. Existence-checked so a keyless host (unit tests,
    * containers) stays on the unauthenticated path instead of failing auth. */
  private def defaultSshKeyPath: Option[String] = {
    val p = java.nio.file.Paths.get(System.getProperty("user.home"), ".ssh", "id_ed25519")
    Option.when(java.nio.file.Files.exists(p))(p.toString)
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

  /**
   * Acquire the mailbox — the three-way branch (durable-attach / WS-mint / legacy-create).
   * All three return a NATS-serving Mailbox. When the mailbox is ATTACHED (durable or WS-mint)
   * it touches NO mesh.mailbox.v1.* records; only the legacy branch does. The caller uses
   * `wasAttached` to skip the records-op mailbox pinger (step 3b) — an attached mailbox's
   * aliveness lives on its processrun, not on a mailbox-record touch.
   */
  private def acquireMailbox(
    bootstrapConfig: HermesBootstrapConfig,
    appConfig: HermesAppConfig,
    processUid: String,
    natsTransport: NatsTransport,
  )(using ctx: Ctx): Mailbox = {
    bootstrapConfig.namedMailboxKeys match {
      // 1. DURABLE ATTACH from config keys — no create, no fetch.
      case Some(keys) =>
        logger.info(s"Attaching to durable named mailbox from config keys: ${keys.address}")
        nats.NatsMailboxClient.attachFromKeys(
          address = Mailbox.MailboxAddress(keys.address),
          adminKey = Mailbox.AdminKey(keys.adminKey),
          readerKey = Mailbox.ReaderKey(keys.readerKey),
          isNamed = true,
          natsTransport = natsTransport,
        )

      case None =>
        bootstrapConfig.httpUrl match {
          // 2. WS-MINT then attach a NATS mailbox to the minted keys; drop the WS.
          case Some(httpUrl) =>
            logger.info(s"Bootstrapping ephemeral mailbox over WS gateway $httpUrl, then attaching over NATS")
            val sshKeyPath = bootstrapConfig.sshKeyPath.getOrElse("~/.ssh/id_ed25519")
            val sshPublicKey = auth.SshAuth.readPublicKey(sshKeyPath + ".pub")
            val wsMbox =
              ws.WsMailbox.bootstrap(
                meshRootUrl = httpUrl,
                processUid = processUid,
                sshPublicKey = sshPublicKey,
                sshOrigin = appConfig.appName.getOrElse("hermes"),
                signNonce = nonce => auth.SshAuth.signNonce(nonce, sshKeyPath),
              )
            try {
              val md = wsMbox.metadata
              nats.NatsMailboxClient.attachFromKeys(
                address = md.address,
                adminKey = md.adminKey,
                readerKey = md.readerKey,
                isNamed = false, // WS-minted ephemeral
                natsTransport = natsTransport,
              )
            } finally {
              wsMbox.close() // the WS was only used to mint; the mailbox runs over NATS now
            }

          // 3. LEGACY CREATE over mesh.mailbox.v1.* — the fallback until httpUrl/keys are deployed.
          case None =>
            appConfig.namedMailbox match {
              case Some(name) =>
                logger.info(s"[legacy mesh.mailbox.v1] Creating named mailbox: $name")
                nats.NatsMailboxClient.fetchOrCreateNamedMailbox(
                  address = Mailbox.MailboxAddress(name), natsTransport = natsTransport,
                )(using ctx).get
              case None =>
                logger.info(s"[legacy mesh.mailbox.v1] Creating non-durable mailbox (lifecycle=${appConfig.mailboxLifecycle})")
                nats.NatsMailboxClient.createNonDurableMailbox(natsTransport, appConfig.mailboxLifecycle, processUid)(using ctx).get
            }
        }
    }
  }

  /** True when the config drives the ATTACH path (durable keys or WS-mint) rather than the
   *  legacy mesh.mailbox.v1 create — used to skip the records-op mailbox pinger. */
  private def mailboxWasAttached(bootstrapConfig: HermesBootstrapConfig): Boolean =
    bootstrapConfig.namedMailboxKeys.isDefined || bootstrapConfig.httpUrl.isDefined

}
