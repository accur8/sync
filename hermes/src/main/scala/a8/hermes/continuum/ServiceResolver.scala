package a8.hermes.continuum

import a8.hermes.core.Mailbox
import a8.hermes.rpc.RpcClient
import a8.hermes.proto.continuum.continuum_rpc.{ResolveJobUidRequest, ResolveJobUidResponse}
import a8.shared.app.Ctx
import a8.common.logging.Logging

import scala.concurrent.duration.{FiniteDuration, *}

/**
 * Job resolution using the continuum.ResolveJobUid RPC.
 *
 * This resolves a job name + worker UID to a unique job UID.
 * The job UID is used for process lifecycle tracking in continuum.
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
   * @param rpcClient RPC client for making the ResolveJobUid call
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

    val request = ResolveJobUidRequest(
      jobName = serviceName,
      workerUid = workerUid,
    )

    rpcClient.callTyped[ResolveJobUidRequest, ResolveJobUidResponse](
      targetMailbox = config.continuumServiceMailbox,
      endpoint = "continuum.ResolveJobUid",
      request = request,
      timeout = timeout.orElse(Some(config.defaultTimeout)),
    )(using ctx, summon) match {
      case Some(response) =>
        logger.debug(s"Resolved job UID: ${response.jobUid}")
        Some(response.jobUid)

      case None =>
        logger.error(s"ResolveJobUid RPC failed for service=$serviceName, worker=$workerUid")
        None
    }
  }

}
