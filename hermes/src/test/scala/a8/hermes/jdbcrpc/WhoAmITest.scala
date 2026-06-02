package a8.hermes.jdbcrpc

import a8.hermes.bootstrap.{HermesAppConfig, HermesBootstrap, HermesBootstrapConfig}
import a8.hermes.proto.auth.auth.{GetUserInfoForSelfRequest, GetUserInfoForSelfResponse}
import a8.shared.app.{AppCtx, BootstrappedIOApp}

import scala.concurrent.duration.DurationInt

/**
 * Test-only equivalent of godev's `a8 auth whoami`. Bootstraps onto the mesh from
 * ~/.config/a8/bootstrap.conf and calls `auth.v2.GetUserInfoForSelf` on the `auth` mailbox.
 *
 * Like the Go whoami, this does NOT perform an SSH login — it relies on the identity already bound on
 * the box / mailbox. It's a probe to confirm (a) sync's upgraded bootstrap loader + naming resolution
 * work against a real env, and (b) whether the bootstrapped mailbox carries a usable identity.
 *
 * Run: sbt "hermes/Test/runMain a8.hermes.jdbcrpc.WhoAmITest"
 */
object WhoAmITest extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit = {
    logger.info("=" * 60)
    logger.info("WhoAmITest — full self-contained flow (SSH login + bind + whoami)")
    logger.info("=" * 60)

    val bootstrapConfig = HermesBootstrapConfig.load()
    val hermes = HermesBootstrap.resource(bootstrapConfig, HermesAppConfig(appName = Some("whoami-test"))).unwrap
    logger.info(s"✓ bootstrapped; our (anonymous) mailbox = ${hermes.mailbox.metadata.address.value}")

    val authMailbox = hermes.staticServiceDiscovery.getMailbox("auth")
    logger.info(s"✓ resolved auth mailbox = ${authMailbox.value}")

    // Full flow, like `a8 auth whoami`: SSH-key login -> get auth token -> bind identity to our mailbox.
    val authResult =
      a8.hermes.auth.SshAuth.authenticate(
        a8.hermes.auth.SshAuth.Config(authServiceMailbox = authMailbox),
        hermes.rpcClient,
      ) match
        case scala.util.Success(r) => logger.info(s"✓ SSH login ok (token len=${r.authToken.length})"); r
        case scala.util.Failure(e) => throw new RuntimeException(s"SSH login failed: ${e.getMessage}", e)

    import a8.hermes.proto.mailbox.mailbox.{BindIdentityRequest, BindIdentityResponse}
    val mailboxServiceMailbox = hermes.staticServiceDiscovery.getMailbox("mailbox")
    val bindResp =
      hermes.rpcClient.callTyped[BindIdentityRequest, BindIdentityResponse](
        targetMailbox = mailboxServiceMailbox,
        endpoint = "mailbox.v1.BindIdentity",
        request = BindIdentityRequest(authToken = authResult.authToken),
        timeout = Some(10.seconds),
      )(using appCtx, summon).getOrElse(throw new RuntimeException("BindIdentity: no response"))
    if !bindResp.success then throw new RuntimeException(s"BindIdentity failed: ${bindResp.message}")
    logger.info(s"✓ identity bound (user_uid=${bindResp.userUid})")

    hermes.rpcClient.callTyped[GetUserInfoForSelfRequest, GetUserInfoForSelfResponse](
      targetMailbox = authMailbox,
      endpoint = "auth.GetUserInfoForSelf",  // godev registers this WITHOUT a .v2 prefix (auth_generator.go)
      request = GetUserInfoForSelfRequest(),
      timeout = Some(10.seconds),
    )(using appCtx, summon) match {
      case Some(info) =>
        logger.info("\n--- whoami ---")
        logger.info(s"  userUid     = ${info.userUid}")
        logger.info(s"  name        = ${info.name}")
        logger.info(s"  email       = ${info.email}")
        logger.info(s"  workerUid   = ${info.workerUid} (${info.workerLabel})")
        logger.info(s"  serverUid   = ${info.serverUid} (${info.serverLabel})")
        logger.info(s"  groups      = ${info.groups.map(_.name).mkString(", ")}")
        logger.info(s"  expiresAt   = ${info.expiresAt}")
        logger.info("\n✓ WhoAmITest succeeded — full SSH-login + bind + whoami flow works end to end")
      case None =>
        logger.error("✗ GetUserInfoForSelf returned no response / error (see RpcClient warning above)")
        System.exit(1)
    }

    System.exit(0)
  }

}
