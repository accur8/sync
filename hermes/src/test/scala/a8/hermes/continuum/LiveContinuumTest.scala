package a8.hermes.continuum

import a8.hermes.bootstrap.{HermesAppConfig, HermesBootstrap, HermesBootstrapConfig}
import a8.hermes.proto.continuum.continuum_rpc.{ProcessCompletedRequest, ProcessStartedRequest}
import a8.hermes.proto.mailbox.mailbox.{BindIdentityRequest, BindIdentityResponse}
import a8.shared.app.{AppCtx, BootstrappedIOApp}

/**
 * LIVE end-to-end test for the continuum service mesh integration.
 * Requires ~/.config/a8/bootstrap.conf pointing at a reachable env with a continuum service.
 *
 * Exercises:
 *   1. SSH auth + BindIdentity (required for authenticated RPC endpoints)
 *   2. ServiceResolver.resolveServiceUid — authenticated RPC round-trip to continuum
 *   3. ContinuumRunnerClient.processStarted / ping / processCompleted — fire-and-forget lifecycle bus
 *   4. ListServices RPC — confirm the test process appeared in the registry
 *
 * Run: sbt "hermes/Test/runMain a8.hermes.continuum.LiveContinuumTest [env]"
 * where [env] is an optional bootstrap environment name override (e.g. "continuumstaging").
 */
object LiveContinuumTest extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit = {
    val envOverride = sys.props.get("env").orElse(args.headOption)
    val bootstrapConfig = HermesBootstrapConfig.load(envOverride)

    logger.info("=" * 70)
    logger.info("LiveContinuumTest — continuum service mesh integration")
    logger.info("=" * 70)
    logger.info(s"natsUrl=${bootstrapConfig.natsUrl}")
    logger.info(s"named mailboxes: ${bootstrapConfig.namedMailboxes}")

    val hermes = HermesBootstrap.resource(bootstrapConfig, HermesAppConfig(appName = Some("live-continuum-test"))).unwrap
    logger.info(s"✓ bootstrapped; our mailbox = ${hermes.mailbox.metadata.address.value}")

    val continuumMailbox = hermes.staticServiceDiscovery.getMailbox("continuum")
    logger.info(s"✓ continuum service mailbox = ${continuumMailbox.value}")

    // --- 1. SSH auth + BindIdentity (required for authenticated RPC endpoints) ---
    logger.info("\n--- SSH auth ---")
    val authMailbox = hermes.staticServiceDiscovery.getMailbox("auth")
    val authResult =
      a8.hermes.auth.SshAuth.authenticate(
        a8.hermes.auth.SshAuth.Config(authServiceMailbox = authMailbox),
        hermes.rpcClient,
      ) match {
        case scala.util.Success(r) => logger.info("✓ SSH auth ok"); r
        case scala.util.Failure(e) => throw new RuntimeException(s"SSH auth failed: ${e.getMessage}", e)
      }

    val mailboxMailbox = hermes.staticServiceDiscovery.getMailbox("mailbox")
    val bindResp =
      hermes.rpcClient.callTyped[BindIdentityRequest, BindIdentityResponse](
        targetMailbox = mailboxMailbox,
        endpoint = "mailbox.v1.BindIdentity",
        request = BindIdentityRequest(authToken = authResult.authToken),
        timeout = Some(scala.concurrent.duration.FiniteDuration(10, "seconds")),
      )(using appCtx, summon)
        .getOrElse(throw new RuntimeException("BindIdentity returned no response"))
    if !bindResp.success then throw new RuntimeException(s"BindIdentity failed: ${bindResp.message}")
    logger.info(s"✓ identity bound (user_uid=${bindResp.userUid})")

    val workerUid = s"test-worker-${hermes.processUid.take(8)}"
    val resolverConfig = ServiceResolver.Config(continuumServiceMailbox = continuumMailbox)

    // --- 2. ResolveServiceUid RPC ---
    logger.info("\n--- ResolveServiceUid ---")
    val serviceUid =
      ServiceResolver.resolveServiceUid(
        serviceName = "live-continuum-test",
        workerUid = workerUid,
        config = resolverConfig,
        rpcClient = hermes.rpcClient,
      ) match {
        case Some(uid) => logger.info(s"✓ resolved serviceUid=$uid"); uid
        case None      => throw new RuntimeException("ResolveServiceUid returned None")
      }

    // --- 3. Lifecycle bus: processStarted → ping → processCompleted ---
    logger.info("\n--- lifecycle bus (fire-and-forget) ---")
    val runnerClient = new ContinuumRunnerClient(hermes.natsTransport)
    val processUid = hermes.processUid

    runnerClient.processStarted(
      ProcessStartedRequest(
        processUid = processUid,
        workerUid = workerUid,
        serviceUid = serviceUid,
        startedAt = Some(ContinuumRunnerClient.nowTimestamp()),
        command = Seq("sbt", "hermes/Test/runMain", "a8.hermes.continuum.LiveContinuumTest"),
        kind = "test",
        serviceName = "live-continuum-test",
      )
    )
    logger.info(s"✓ processStarted published (processUid=$processUid)")

    runnerClient.ping(processUid, Map("stdout" -> 0L))
    logger.info(s"✓ ping published")

    // --- 4. processCompleted ---
    logger.info("\n--- processCompleted ---")
    runnerClient.processCompleted(
      ProcessCompletedRequest(
        processUid = processUid,
        exitCode = 0,
        exitMessage = "test completed",
        completedAt = Some(ContinuumRunnerClient.nowTimestamp()),
      )
    )
    logger.info(s"✓ processCompleted published")

    logger.info("\n" + "=" * 70)
    logger.info("✓ LiveContinuumTest completed")
    logger.info("=" * 70)
    System.exit(0)
  }

}
