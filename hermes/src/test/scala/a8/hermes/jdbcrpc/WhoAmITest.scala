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
        // Render a single multiline report mirroring `a8 auth whoami`'s output
        // (cmd/a8/auth-client.go) so this proves the Scala client surfaces the same data.
        val sb = new StringBuilder
        sb.append("\nCurrent Authentication:\n\n")
        sb.append(s"  User UID:    ${info.userUid}\n")
        sb.append(s"  Name:        ${info.name}\n")
        sb.append(s"  Mailbox:     ${hermes.mailbox.metadata.address.value}\n")
        if info.email.nonEmpty then sb.append(s"  Email:       ${info.email}\n")
        if info.description.nonEmpty then sb.append(s"  Description: ${info.description}\n")
        if info.externalMaintainer.nonEmpty then sb.append(s"  Maintainer:  ${info.externalMaintainer}\n")
        // worker/server identity — empty for plain user logins; shown only when present (the Go CLI
        // omits empty fields too).
        if info.workerUid.nonEmpty then sb.append(s"  Worker:      ${info.workerUid} (${info.workerLabel})\n")
        if info.serverUid.nonEmpty then sb.append(s"  Server:      ${info.serverUid}\n")
        if info.groups.nonEmpty then
          sb.append("\n  Groups:\n")
          info.groups.foreach { group =>
            sb.append(s"    - ${group.name} (${group.uid})\n")
            if group.description.nonEmpty then sb.append(s"      ${group.description}\n")
          }
        logger.info(sb.toString)
        logger.info("✓ WhoAmITest succeeded — full SSH-login + bind + whoami flow works end to end")
      case None =>
        logger.error("✗ GetUserInfoForSelf returned no response / error (see RpcClient warning above)")
        System.exit(1)
    }

    System.exit(0)
  }

}
