package a8.examples.hermes

import a8.hermes.bootstrap.HermesBootstrap
import a8.hermes.auth.SshAuth
import a8.hermes.core.Mailbox
import a8.hermes.proto.auth.auth.{GetUserInfoForSelfRequest, GetUserInfoForSelfResponse}
import a8.shared.app.{BootstrappedIOApp, AppCtx}
import a8.shared.zreplace.Resource

/**
 * WhoAmI command - displays current authentication information.
 *
 * Equivalent to `a8 auth whoami` from godev.
 *
 * Flow:
 * 1. Bootstrap Hermes (NATS connection, mailbox, RPC client)
 * 2. Authenticate using SSH (LoginBegin → sign nonce → LoginComplete)
 * 3. Call GetUserInfoForSelf RPC (uses auth token from step 2)
 * 4. Display user information
 */
object WhoAmI extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit = {
    Thread.currentThread().setName("whoami-main")
    logger.info("Starting whoami command...")

    // Bootstrap Hermes components
    val hermes = Resource.free.run(HermesBootstrap.resource())(using appCtx)

    // Determine auth service mailbox
    val authServiceMailboxName = hermes.config.namedMailboxes.get("auth")
      .orElse(hermes.config.namedMailboxes.get("nefario"))
      .getOrElse {
        throw new RuntimeException("No auth service mailbox configured")
      }

    // Named mailboxes use their literal address (e.g., "nefario-rpc"), not "aaa_nefario-rpc"
    val authMailbox = a8.hermes.core.Mailbox.MailboxAddress(authServiceMailboxName)

    // Step 1: Authenticate using SSH
    logger.info("Authenticating with SSH...")
    val authResult = SshAuth.authenticate(
      config = SshAuth.Config(
        authServiceMailbox = authMailbox,
      ),
      rpcClient = hermes.rpcClient,
    )(using appCtx) match {
      case scala.util.Success(result) =>
        logger.info("✓ SSH authentication successful")
        result
      case scala.util.Failure(e) =>
        throw new RuntimeException(s"SSH authentication failed: ${e.getMessage}", e)
    }

    val authToken = authResult.authToken

    // Step 2: Bind auth token to our mailbox
    logger.info(s"Binding auth token to mailbox...")
    import a8.hermes.proto.mailbox.mailbox.{BindIdentityRequest, BindIdentityResponse}

    // Find mailbox service
    val mailboxServiceMailbox = Mailbox.MailboxAddress("nefario-rpc")  // TODO: Use service discovery

    val bindResp = hermes.rpcClient.callTyped[BindIdentityRequest, BindIdentityResponse](
      targetMailbox = mailboxServiceMailbox,
      endpoint = "mailbox.v1.BindIdentity",
      request = BindIdentityRequest(authToken = authToken),
      timeout = Some(scala.concurrent.duration.FiniteDuration(10, "seconds")),
    )(using appCtx, summon).getOrElse {
      throw new RuntimeException("BindIdentity RPC failed: no response")
    }

    if (!bindResp.success) {
      throw new RuntimeException(s"Failed to bind identity: ${bindResp.message}")
    }

    logger.info(s"✓ Auth token bound to mailbox (user_uid: ${bindResp.userUid})")

    // Step 3: Call GetUserInfoForSelf RPC (now authorized)
    logger.debug(s"Calling GetUserInfoForSelf on auth service")
    val userInfoResp = hermes.rpcClient.callTyped[GetUserInfoForSelfRequest, GetUserInfoForSelfResponse](
      targetMailbox = authMailbox,
      endpoint = "auth.v2.GetUserInfoForSelf",
      request = GetUserInfoForSelfRequest(),
      timeout = Some(scala.concurrent.duration.FiniteDuration(10, "seconds")),
    )(using appCtx, summon).getOrElse {
      throw new RuntimeException("GetUserInfoForSelf RPC failed: no response")
    }

    // Display user info
    displayUserInfo(userInfoResp, hermes.mailbox.metadata.address.value, authToken)

    // Exit cleanly (background RPC tasks will be interrupted by resource cleanup)
    System.exit(0)
  }

  /**
   * Display user information in human-readable format
   */
  private def displayUserInfo(userInfo: GetUserInfoForSelfResponse, mailboxAddress: String, authToken: String): Unit = {
    println()
    println("Current Authentication:")
    println()
    println(s"  User UID:    ${userInfo.userUid}")
    println(s"  Name:        ${userInfo.name}")
    println(s"  Mailbox:     $mailboxAddress")
    println(s"  Auth Token:  ${authToken.take(20)}...")

    if (userInfo.email.nonEmpty) {
      println(s"  Email:       ${userInfo.email}")
    }

    if (userInfo.description.nonEmpty) {
      println(s"  Description: ${userInfo.description}")
    }

    if (userInfo.externalMaintainer.nonEmpty) {
      println(s"  Maintainer:  ${userInfo.externalMaintainer}")
    }

    if (userInfo.groups.nonEmpty) {
      println()
      println("  Groups:")
      userInfo.groups.foreach { group =>
        println(s"    - ${group.name} (${group.uid})")
        if (group.description.nonEmpty) {
          println(s"      ${group.description}")
        }
      }
    }

    println()
  }

}
