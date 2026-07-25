package a8.hermes.ws

import a8.common.logging.Logging
import a8.hermes.bootstrap.{HermesAppConfig, HermesBootstrap, HermesBootstrapConfig}
import a8.shared.app.{AppCtx, BootstrappedIOApp}
import a8.shared.zreplace.Resource

/**
 * Drives the REAL HermesBootstrap through its WS-MINT path (httpUrl set): the mailbox is minted
 * over the WS gateway (ClientHello inline login), then hermes RUNS over direct NATS — with NO
 * mesh.mailbox.v1.* create/fetch. This is the Scala half of retiring that surface.
 *
 * Unlike WsBootstrapProbeMain (which drives WsMailbox.bootstrap directly — the WS→WS transport),
 * this exercises HermesBootstrap.acquireMailbox's new WS-mint→NATS branch end to end: the exact
 * code the fleet daemons/CLI will run. A compile is not evidence; the godev durable-attach work
 * hit FOUR silent bugs that only a live run surfaced, and this path has the same class of them.
 *
 * Contract line on stdout (the godev harness parses it):
 *   HERMES-BS-PROBE-OK address=<mailbox address> processUid=<processrun uid>
 *   HERMES-BS-PROBE-FAIL <reason>
 *
 * Env (set by the harness):
 *   MESH_ROOT_URL     the mesh WS/HTTP gateway (httpUrl)
 *   NATS_URL          the NATS url hermes runs over after minting
 *   SSH_PRIVATE_KEY   the key whose .pub authenticates the inline WS login
 *   PROCESS_UID       the processrun uid to link
 */
object HermesBootstrapProbeMain extends BootstrappedIOApp with Logging {

  override def run()(using appCtx: AppCtx): Unit = {
    val meshRootUrl = required("MESH_ROOT_URL")
    val natsUrl = required("NATS_URL")
    val sshKey = required("SSH_PRIVATE_KEY")
    val processUid = required("PROCESS_UID")

    try {
      // A config with BOTH httpUrl (WS bootstrap) and natsUrl (Path #2 runtime transport) — the
      // shape that drives acquireMailbox's WS-mint branch.
      val bootstrapConfig = HermesBootstrapConfig(
        natsUrl = natsUrl,
        httpUrl = Some(meshRootUrl),
        sshKeyPath = Some(sshKey),
      )
      val appConfig = HermesAppConfig(appName = Some("hermes-bs-probe"), mailboxLifecycle = "long-lived-daemon")

      val hermes = Resource.free.run(HermesBootstrap.resource(bootstrapConfig, appConfig))
      val addr = hermes.mailbox.address.value
      if (addr.isEmpty) fail("HermesBootstrap yielded a mailbox with no address")
      else {
        // The mailbox serves over NATS now (its inbox is subscribed on NATS subjects). We proved
        // the mint + attach happened by holding a NATS mailbox with a real address; the godev
        // harness confirms the processrun+mailbox linkage in the shared DB.
        println(s"HERMES-BS-PROBE-OK address=$addr processUid=$processUid")
        System.out.flush()
        Thread.sleep(1500) // let a live, subscribed connection be observed
      }
    } catch {
      case e: Throwable => fail(s"${e.getClass.getSimpleName}: ${e.getMessage}")
    }
    System.exit(0)
  }

  private def required(name: String): String =
    sys.env.getOrElse(name, { fail(s"$name is not set"); throw new IllegalStateException("unreachable") })

  private def fail(reason: String): Unit = {
    println(s"HERMES-BS-PROBE-FAIL $reason")
    System.out.flush()
    System.exit(1)
  }

}
