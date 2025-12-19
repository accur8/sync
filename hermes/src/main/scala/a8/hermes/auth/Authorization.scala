package a8.hermes.auth

import a8.hermes.core.Mailbox
import a8.hermes.rpc.RpcClient
import a8.hermes.proto.auth.auth.{HasRoleRequest, HasRoleResponse}
import a8.shared.app.Ctx
import a8.common.logging.Logging

import scala.concurrent.duration.{FiniteDuration, *}

/**
 * Authorization checking using mailbox-based credentials.
 *
 * After authentication and binding, the mailbox address becomes the credential.
 * Authorization is checked by calling the auth service's HasRole RPC.
 */
object Authorization extends Logging {

  case class Config(
    authServiceMailbox: Mailbox.MailboxAddress,
    defaultTimeout: FiniteDuration = 10.seconds,
  )

  /**
   * Result of an authorization check.
   */
  sealed trait AuthzResult
  object AuthzResult {
    case class Allowed(rolesMatched: Seq[String]) extends AuthzResult
    case class Denied(reason: String) extends AuthzResult
    case class Error(message: String) extends AuthzResult
  }

  /**
   * Check if a mailbox has any of the specified roles.
   *
   * @param mailboxAddress The mailbox address to check (this is the credential after binding)
   * @param roles List of roles to check (endpoint paths or database roles)
   * @param config Authorization config (includes auth service mailbox)
   * @param rpcClient RPC client for making the HasRole call
   * @param timeout Optional timeout for the RPC call
   * @return AuthzResult indicating whether access is allowed
   */
  def hasRole(
    mailboxAddress: Mailbox.MailboxAddress,
    roles: Seq[String],
    config: Config,
    rpcClient: RpcClient,
    timeout: Option[FiniteDuration] = None,
  )(using ctx: Ctx): AuthzResult = {
    logger.debug(s"Checking roles for mailbox ${mailboxAddress.value}: ${roles.mkString(", ")}")

    val request = HasRoleRequest(
      mailboxAddress = mailboxAddress.value,
      roles = roles,
    )

    rpcClient.callTyped[HasRoleRequest, HasRoleResponse](
      targetMailbox = config.authServiceMailbox,
      endpoint = "auth.v2.HasRole",
      request = request,
      timeout = timeout.orElse(Some(config.defaultTimeout)),
    )(using ctx, summon) match {
      case Some(response) =>
        if (response.allowed) {
          logger.debug(s"Authorization ALLOWED for ${mailboxAddress.value}, matched roles: ${response.rolesMatched.mkString(", ")}")
          AuthzResult.Allowed(response.rolesMatched)
        } else {
          logger.debug(s"Authorization DENIED for ${mailboxAddress.value}: ${response.reason}")
          AuthzResult.Denied(response.reason)
        }

      case None =>
        logger.error(s"HasRole RPC failed for ${mailboxAddress.value}")
        AuthzResult.Error("HasRole RPC call failed")
    }
  }

  /**
   * Check if a mailbox has a single role.
   * Convenience method for single-role checks.
   *
   * @param mailboxAddress The mailbox address to check
   * @param role The role to check
   * @param config Authorization config
   * @param rpcClient RPC client
   * @param timeout Optional timeout
   * @return AuthzResult indicating whether access is allowed
   */
  def hasRole(
    mailboxAddress: Mailbox.MailboxAddress,
    role: String,
    config: Config,
    rpcClient: RpcClient,
    timeout: Option[FiniteDuration],
  )(using ctx: Ctx): AuthzResult = {
    hasRole(mailboxAddress, Seq(role), config, rpcClient, timeout)(using ctx)
  }

  /**
   * Check if a mailbox is allowed access, throwing an exception if denied.
   * This is a convenience method for enforcing authorization.
   *
   * @param mailboxAddress The mailbox address to check
   * @param roles List of roles to check
   * @param config Authorization config
   * @param rpcClient RPC client
   * @param timeout Optional timeout
   * @throws UnauthorizedException if access is denied or check fails
   */
  def requireRole(
    mailboxAddress: Mailbox.MailboxAddress,
    roles: Seq[String],
    config: Config,
    rpcClient: RpcClient,
    timeout: Option[FiniteDuration] = None,
  )(using ctx: Ctx): Unit = {
    hasRole(mailboxAddress, roles, config, rpcClient, timeout)(using ctx) match {
      case AuthzResult.Allowed(_) =>
        // Access granted
        ()

      case AuthzResult.Denied(reason) =>
        throw new UnauthorizedException(
          s"Access denied for mailbox ${mailboxAddress.value}: $reason"
        )

      case AuthzResult.Error(message) =>
        throw new UnauthorizedException(
          s"Authorization check failed for mailbox ${mailboxAddress.value}: $message"
        )
    }
  }

}

/**
 * Exception thrown when authorization is denied or fails.
 */
class UnauthorizedException(message: String) extends RuntimeException(message)
