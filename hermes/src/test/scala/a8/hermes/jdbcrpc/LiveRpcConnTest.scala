package a8.hermes.jdbcrpc

import a8.hermes.bootstrap.{HermesAppConfig, HermesBootstrap, HermesBootstrapConfig}
import a8.shared.app.{AppCtx, BootstrappedIOApp}
import a8.shared.jdbcf.SqlString.sqlStringContextImplicit

import java.time.LocalDateTime

/**
 * LIVE end-to-end test of the credential-free Mapper-over-RPC path against a real continuum `dbaccess`
 * service. Requires ~/.config/a8/bootstrap.conf pointing at a reachable env (e.g. continuumstaging) and
 * VPN access to its NATS cluster.
 *
 * Flow: HermesBootstrap (mesh identity, no DB creds) → resolve the `db` service mailbox from the
 * bootstrap nameMappings → DbRpcClient(dbaccess.v1.Query) → RpcConn → run a real SELECT against
 * continuum.processrun → StructRowReader → typed rows. This exercises the entire stack end to end.
 *
 * Run: sbt "hermes/Test/runMain a8.hermes.jdbcrpc.LiveRpcConnTest"
 */
object LiveRpcConnTest extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit = {
    logger.info("=" * 70)
    logger.info("LiveRpcConnTest — Mapper-over-RPC against live continuum dbaccess")
    logger.info("=" * 70)

    val bootstrapConfig = HermesBootstrapConfig.load()
    logger.info(s"natsUrl=${bootstrapConfig.natsUrl}")
    logger.info(s"named mailboxes: ${bootstrapConfig.namedMailboxes}")

    val hermes = HermesBootstrap.resource(bootstrapConfig, HermesAppConfig(appName = Some("live-rpcconn-test"))).unwrap
    logger.info(s"✓ bootstrapped; our mailbox = ${hermes.mailbox.metadata.address.value}")

    val dbMailbox = hermes.staticServiceDiscovery.getMailbox("db")
    logger.info(s"✓ resolved db service mailbox = ${dbMailbox.value}")

    // --- authenticate (SSH) + bind identity to our mailbox, like the WhoAmI example.
    // The dbaccess firewall checks the sender mailbox's bound identity for ACLs.
    val authMailbox = hermes.staticServiceDiscovery.getMailbox("auth")
    val authResult =
      a8.hermes.auth.SshAuth.authenticate(
        a8.hermes.auth.SshAuth.Config(authServiceMailbox = authMailbox),
        hermes.rpcClient,
      ) match
        case scala.util.Success(r) => logger.info("✓ SSH auth ok"); r
        case scala.util.Failure(e) => throw new RuntimeException(s"SSH auth failed: ${e.getMessage}", e)

    import a8.hermes.proto.mailbox.mailbox.{BindIdentityRequest, BindIdentityResponse}
    val mailboxServiceMailbox = hermes.staticServiceDiscovery.getMailbox("mailbox")
    val bindResp =
      hermes.rpcClient.callTyped[BindIdentityRequest, BindIdentityResponse](
        targetMailbox = mailboxServiceMailbox,
        endpoint = "mailbox.v1.BindIdentity",
        request = BindIdentityRequest(authToken = authResult.authToken),
        timeout = Some(scala.concurrent.duration.FiniteDuration(10, "seconds")),
      )(using appCtx, summon).getOrElse(throw new RuntimeException("BindIdentity failed: no response"))
    if !bindResp.success then throw new RuntimeException(s"BindIdentity failed: ${bindResp.message}")
    logger.info(s"✓ identity bound (user_uid=${bindResp.userUid})")

    val dbClient = DbRpcClient(hermes.rpcClient, dbMailbox)
    val conn = RpcConn(a8.shared.jdbcf.DatabaseConfig.DatabaseId("continuum"), dbClient)

    // 1) raw query — confirm the RPC + StructRowReader produce rows at all
    logger.info("\n--- raw query: select count(*) from continuum.processrun ---")
    val countRows =
      conn.query[Long](sql"select count(*) as n from continuum.processrun").select.toList
    logger.info(s"processrun row count = ${countRows.headOption.getOrElse("<none>")}")

    // 2) typed read exercising rich-type coercion (timestamp -> LocalDateTime, text -> String, null -> None)
    logger.info("\n--- typed read: uid, startedat, mailbox from a few processrun rows ---")
    val rows =
      conn
        .query[(String, LocalDateTime, Option[String])](
          sql"select uid, startedat, mailbox from continuum.processrun order by startedat desc limit 5"
        )
        .select
        .toList
    if rows.isEmpty then
      logger.warn("no processrun rows returned (empty table or ACL filtered everything)")
    else
      rows.foreach { case (uid, startedAt, mailbox) =>
        logger.info(s"  uid=$uid startedAt=$startedAt mailbox=${mailbox.getOrElse("-")}")
      }

    logger.info("\n" + "=" * 70)
    logger.info("✓ LiveRpcConnTest completed — Mapper-over-RPC round-trip worked against live dbaccess")
    logger.info("=" * 70)
    System.exit(0)
  }

}
