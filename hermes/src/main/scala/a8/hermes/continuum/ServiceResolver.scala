package a8.hermes.continuum

import a8.hermes.core.Mailbox
import a8.hermes.rpc.RpcClient
import a8.hermes.proto.continuum.continuum_rpc.{ResolveServiceUidRequest, ResolveServiceUidResponse}
import a8.shared.app.Ctx
import a8.common.logging.Logging

import scala.concurrent.duration.{FiniteDuration, *}

/**
 * Service resolution using ResolveServiceUid RPC.
 *
 * This resolves a service name + minion UID to a unique service UID.
 * The service UID is used for process lifecycle tracking in continuum.
 */
object ServiceResolver extends Logging {

  case class Config(
    continuumServiceMailbox: Mailbox.MailboxAddress,
    defaultTimeout: FiniteDuration = 10.seconds,
  )

  /**
   * Resolve a service UID for a given service name and worker UID.
   *
   * @param serviceName The service name (e.g., "continuum", "scheduler")
   * @param workerUid The worker UID (unique identifier for this user@host)
   * @param config Service resolver config (includes continuum service mailbox)
   * @param rpcClient RPC client for making the ResolveServiceUid call
   * @param timeout Optional timeout for the RPC call
   * @return Service UID if successful, None if failed
   */
  def resolveServiceUid(
    serviceName: String,
    workerUid: String,
    config: Config,
    rpcClient: RpcClient,
    timeout: Option[FiniteDuration] = None,
  )(using ctx: Ctx): Option[String] = {
    logger.debug(s"Resolving service UID for service=$serviceName, worker=$workerUid")

    val request = ResolveServiceUidRequest(
      serviceName = serviceName,
      workerUid = workerUid,
    )

    rpcClient.callTyped[ResolveServiceUidRequest, ResolveServiceUidResponse](
      targetMailbox = config.continuumServiceMailbox,
      endpoint = "continuum.ResolveServiceUid",
      request = request,
      timeout = timeout.orElse(Some(config.defaultTimeout)),
    )(using ctx, summon) match {
      case Some(response) =>
        logger.debug(s"Resolved service UID: ${response.serviceUid}")
        Some(response.serviceUid)

      case None =>
        logger.error(s"ResolveServiceUid RPC failed for service=$serviceName, worker=$workerUid")
        None
    }
  }

}
