package a8.hermes.nefario

import a8.hermes.core.Mailbox
import a8.hermes.rpc.RpcClient
import a8.hermes.proto.nefario.nefario_rpc.{ResolveServiceUidRequest, ResolveServiceUidResponse}
import a8.shared.app.Ctx
import a8.common.logging.Logging

import scala.concurrent.duration.{FiniteDuration, *}

/**
 * Service resolution using ResolveServiceUid RPC.
 *
 * This resolves a service name + minion UID to a unique service UID.
 * The service UID is used for process lifecycle tracking in nefario.
 */
object ServiceResolver extends Logging {

  case class Config(
    nefarioServiceMailbox: Mailbox.MailboxAddress,
    defaultTimeout: FiniteDuration = 10.seconds,
  )

  /**
   * Resolve a service UID for a given service name and minion UID.
   *
   * @param serviceName The service name (e.g., "nefario", "scheduler")
   * @param minionUid The minion UID (unique identifier for this user@host)
   * @param config Service resolver config (includes nefario service mailbox)
   * @param rpcClient RPC client for making the ResolveServiceUid call
   * @param timeout Optional timeout for the RPC call
   * @return Service UID if successful, None if failed
   */
  def resolveServiceUid(
    serviceName: String,
    minionUid: String,
    config: Config,
    rpcClient: RpcClient,
    timeout: Option[FiniteDuration] = None,
  )(using ctx: Ctx): Option[String] = {
    logger.debug(s"Resolving service UID for service=$serviceName, minion=$minionUid")

    val request = ResolveServiceUidRequest(
      serviceName = serviceName,
      minionUid = minionUid,
    )

    rpcClient.callTyped[ResolveServiceUidRequest, ResolveServiceUidResponse](
      targetMailbox = config.nefarioServiceMailbox,
      endpoint = "nefario.v1.ResolveServiceUid",
      request = request,
      timeout = timeout.orElse(Some(config.defaultTimeout)),
    )(using ctx, summon) match {
      case Some(response) =>
        logger.debug(s"Resolved service UID: ${response.serviceUid}")
        Some(response.serviceUid)

      case None =>
        logger.error(s"ResolveServiceUid RPC failed for service=$serviceName, minion=$minionUid")
        None
    }
  }

}
