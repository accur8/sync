package a8.hermes.jdbcrpc

import a8.hermes.bootstrap.{HermesAppConfig, HermesBootstrap, HermesBootstrapConfig}
import a8.hermes.proto.db.db.{ExplainRequest, ExplainResponse}
import a8.shared.app.{AppCtx, BootstrappedIOApp}

import scala.concurrent.duration.DurationInt

/**
 * Probe the RUNNING dbaccess firewall directly: send the exact ProcessRun query through
 * `dbaccess.v1.Explain` (which rewrites but does NOT execute) and print `transformed_sql`. This shows,
 * with no inference, what the live firewall does with `workerUid` — whether it resolves to `workeruid`
 * (column-case fix is live) or re-quotes it as `"workerUid"` (fix not in the running binary).
 *
 * Run: sbt "hermes/Test/runMain a8.hermes.jdbcrpc.ExplainTest"
 */
object ExplainTest extends BootstrappedIOApp {

  private val Sql =
    """select uid, workerUid, parentProcessRunUid, category, startedAt, completedAt, mailbox, """ +
      """processStart, processComplete, serviceUid, logArchive, created_at, updated_at, created_by, """ +
      """updated_by, status, version, extra_data from ProcessRun where uid = 'probe'"""

  override def run()(using appCtx: AppCtx): Unit = {
    val bootstrapConfig = HermesBootstrapConfig.load()
    val hermes = HermesBootstrap.resource(bootstrapConfig, HermesAppConfig(appName = Some("explain-test"))).unwrap

    // auth + bind, identical to ContinuumClient.bootstrap / WhoAmITest
    val authMailbox = hermes.staticServiceDiscovery.getMailbox("auth")
    val authResult =
      a8.hermes.auth.SshAuth.authenticate(
        a8.hermes.auth.SshAuth.Config(authServiceMailbox = authMailbox),
        hermes.rpcClient,
      ).get
    import a8.hermes.proto.mailbox.mailbox.{BindIdentityRequest, BindIdentityResponse}
    val mailboxServiceMailbox = hermes.staticServiceDiscovery.getMailbox("mailbox")
    hermes.rpcClient.callTyped[BindIdentityRequest, BindIdentityResponse](
      targetMailbox = mailboxServiceMailbox,
      endpoint = "mailbox.v1.BindIdentity",
      request = BindIdentityRequest(authToken = authResult.authToken),
      timeout = Some(10.seconds),
    )(using appCtx, summon).getOrElse(throw new RuntimeException("BindIdentity: no response"))

    val dbMailbox = hermes.staticServiceDiscovery.getMailbox("db")
    hermes.rpcClient.callTyped[ExplainRequest, ExplainResponse](
      targetMailbox = dbMailbox,
      endpoint = "dbaccess.v1.Explain",
      request = ExplainRequest(sql = Sql),
      timeout = Some(10.seconds),
    )(using appCtx, summon) match {
      case Some(resp) =>
        logger.info("\n=== Explain ===")
        logger.info(s"  error           = ${if resp.error.isEmpty then "(none)" else resp.error}")
        logger.info(s"  original_sql    = ${resp.originalSql}")
        logger.info(s"  transformed_sql = ${resp.transformedSql}")
        logger.info(s"  resolved_user   = ${resp.resolvedUserName} (${resp.resolvedUserUid})")
        resp.tables.foreach { t =>
          logger.info(s"  table ${t.inputName} -> ${t.resolvedSchema}.${t.resolvedTable}  primary_keys=${t.tableMetadata.map(_.primaryKeys.mkString(",")).getOrElse("(no metadata)")}")
        }
      case None =>
        logger.error("✗ Explain returned no response / error (see RpcClient warning above)")
        System.exit(1)
    }
    System.exit(0)
  }

}
