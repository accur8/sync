package a8.hermes.auth

import a8.hermes.core.Mailbox
import a8.hermes.rpc.RpcClient
import a8.hermes.proto.auth.auth.{GetUserInfoForSelfRequest, GetUserInfoForSelfResponse, LoginBeginRequest, LoginBeginResponse, LoginCompleteRequest, LoginCompleteResponse}
import a8.shared.app.Ctx
import a8.common.logging.Logging

import java.time.{Duration, Instant}
import scala.concurrent.duration.{FiniteDuration, *}
import scala.util.{Try, Success, Failure}

/**
 * Auth token auto-renewal (extension) following godev's pattern.
 *
 * Automatically renews auth tokens at the 50% point of their remaining lifetime
 * by re-authenticating using SSH. Matches godev's auth_extension.go logic.
 */
object AuthExtension extends Logging {

  /**
   * Configuration for auth extension (matching godev's DefaultAuthExtensionConfig)
   */
  case class Config(
    initialDelay: FiniteDuration = 5.seconds,      // Wait before first expiration check
    maxRetries: Int = 5,                            // Max retry attempts
    retryMultiplier: Double = 2.0,                  // Exponential backoff multiplier
    maxRetryDelay: FiniteDuration = 5.minutes,      // Max delay between retries
  )

  object Config {
    def default: Config = Config()
  }

}

class AuthExtension(
  mailbox: Mailbox,
  sshAuthConfig: SshAuth.Config,
  rpcClient: RpcClient,
  config: AuthExtension.Config,
) extends Logging {

  import AuthExtension.*

  @volatile private var running = false

  /**
   * Start the auth extension loop in background.
   * Forks a background task using Ox that renews the token at 50% of lifetime.
   *
   * @param initialExpiration Initial token expiration time
   * @param ctx Context (must have Ox scope)
   */
  def start(initialExpiration: Instant)(using ctx: Ctx): Unit = {
    if (running) {
      logger.warn("Auth extension already running")
      return
    }

    running = true
    logger.info(s"Starting auth token extension (initial expiration: $initialExpiration)")

    // Get the Ox instance from the context
    val ox0 = ctx match {
      case appCtx: a8.shared.app.AppCtx => appCtx.ox0
      case childCtx: a8.shared.app.Ctx.ChildCtx => childCtx.ox0
      case _ => throw new RuntimeException("Unable to get Ox instance from Ctx")
    }

    // Fork the extension loop to run in background
    // Use forkUser so that failures in this fork will propagate and stop the app
    ox.forkUser {
      val threadName = s"auth-extension-${mailbox.address.value.take(8)}"
      Thread.currentThread().setName(threadName)
      logger.info(s"Auth extension thread started: $threadName")

      try {
        // Wait initial delay before starting loop
        Thread.sleep(config.initialDelay.toMillis)

        runExtensionLoop(initialExpiration)(using ctx)
      } catch {
        case th: Throwable =>
          logger.error(s"Auth extension failed - this will stop the application", th)
          throw th
      } finally {
        logger.info(s"Auth extension stopped")
      }
    }(using ox0)

    logger.info("✓ Auth token extension started in background")
  }

  /**
   * The main extension loop - calculates 50% point and renews token.
   * Runs continuously, updating expiration after each renewal.
   *
   * @param currentExpires Current token expiration time
   */
  private def runExtensionLoop(currentExpires: Instant)(using ctx: Ctx): Unit = {
    if (!running) {
      logger.info("Auth extension loop stopped")
      return
    }

    // Calculate when to extend: at 50% of remaining time (matching godev)
    val now = Instant.now()
    val timeUntilExpiration = Duration.between(now, currentExpires)

    if (timeUntilExpiration.isNegative || timeUntilExpiration.isZero) {
      logger.error("Auth token already expired, attempting immediate extension")
      // Try immediate extension
      extendAuthWithRetry()(using ctx) match {
        case Some(newExpiration) =>
          logger.info(s"✓ Token extended after expiration, new expiration: $newExpiration")
          runExtensionLoop(newExpiration)(using ctx)
        case None =>
          logger.error("Failed to extend expired token, stopping extension loop")
          running = false
      }
      return
    }

    val timeUntilExtension = timeUntilExpiration.dividedBy(2)  // 50% point
    logger.info(s"Token will be extended in ${timeUntilExtension.toMinutes} minutes (at 50% of remaining ${timeUntilExpiration.toMinutes} minutes)")

    // Sleep until extension time
    Thread.sleep(timeUntilExtension.toMillis)

    if (!running) {
      logger.info("Auth extension loop stopped during sleep")
      return
    }

    // Extend token with retry logic
    logger.info("Extending auth token...")
    extendAuthWithRetry()(using ctx) match {
      case Some(newExpiration) =>
        logger.info(s"✓ Token extended successfully, new expiration: $newExpiration")
        // Continue loop with new expiration
        runExtensionLoop(newExpiration)(using ctx)

      case None =>
        logger.error("Failed to extend auth token after retries, stopping extension loop")
        running = false
    }
  }

  /**
   * Extend auth token with exponential backoff retry logic.
   * Matches godev's retry pattern with exponential backoff.
   *
   * @return New expiration time if successful, None if all retries failed
   */
  private def extendAuthWithRetry()(using ctx: Ctx): Option[Instant] = {
    var retryCount = 0
    var currentDelay = 1.second

    while (retryCount < config.maxRetries && running) {
      extendAuthToken()(using ctx) match {
        case Success(newExpiration) =>
          logger.info(s"Auth token extended on attempt ${retryCount + 1}")
          return Some(newExpiration)

        case Failure(e) =>
          retryCount += 1
          val isRetryable = isRetryableError(e)

          if (!isRetryable) {
            logger.error(s"Non-retryable error extending auth token: ${e.getMessage}", e)
            return None
          }

          if (retryCount < config.maxRetries) {
            logger.warn(s"Failed to extend auth token (attempt $retryCount/${config.maxRetries}): ${e.getMessage}, retrying in $currentDelay")
            Thread.sleep(currentDelay.toMillis)

            // Exponential backoff with max delay
            val nextDelay = (currentDelay * config.retryMultiplier)
            currentDelay = FiniteDuration(nextDelay.min(config.maxRetryDelay).toMillis, scala.concurrent.duration.MILLISECONDS)
          } else {
            logger.error(s"Failed to extend auth token after $retryCount attempts: ${e.getMessage}", e)
          }
      }
    }

    None
  }

  /**
   * Extend auth token by re-authenticating with SSH.
   * Uses the same flow as initial authentication but with extend_existing_auth flag.
   *
   * @return New expiration time
   */
  private def extendAuthToken()(using ctx: Ctx): Try[Instant] = {
    Try {
      logger.debug("Extending auth token via SSH re-authentication")

      // Re-authenticate using SSH (this will get a new token)
      val authResult = SshAuth.authenticate(
        config = sshAuthConfig,
        rpcClient = rpcClient,
      )(using ctx).get

      // Update mailbox with new token (simulating BindIdentity)
      // Note: In godev this is done via UpdateMailbox RPC, but for now we just have the token
      logger.debug(s"Token extended, new expiration: ${authResult.expiresAt}")

      authResult.expiresAt
    }
  }

  /**
   * Determine if an error is retryable based on godev's classification.
   *
   * Retryable errors:
   * - Connection failures, timeouts
   * - Temporary/unavailable errors
   *
   * Non-retryable errors:
   * - SSH key mismatch (need new authentication)
   * - Invalid signature (SSH key changed)
   * - Not authorized
   */
  private def isRetryableError(e: Throwable): Boolean = {
    val message = e.getMessage
    if (message == null) return true  // Conservative: retry by default

    val lowerMessage = message.toLowerCase

    // Non-retryable errors (matching godev)
    if (lowerMessage.contains("ssh key") ||
        lowerMessage.contains("signature") ||
        lowerMessage.contains("not authorized") ||
        lowerMessage.contains("invalid") ||
        lowerMessage.contains("mismatch")) {
      return false
    }

    // Everything else is retryable (connection failures, timeouts, etc.)
    true
  }

  /**
   * Stop the auth extension loop.
   */
  def stop(): Unit = {
    if (running) {
      logger.info("Stopping auth token extension")
      running = false
    }
  }

}
