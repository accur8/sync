package a8.hermes.ws

import a8.common.logging.Logging
import a8.hermes.auth.SshAuth

/**
 * A REAL hermes client that bootstraps over the WebSocket, run by godev's e2e harness
 * against a live mesh gateway.
 *
 * WHY A MAIN AND NOT A UNIT TEST: this drive has now been bitten three times by code that
 * compiled, looked right, and was never executed by an actual client — the req/reply op, the
 * class-3 handler, and the inline-login path each shipped green and did nothing. A unit test
 * with a fake socket would repeat that mistake, because every bug found so far lived in the
 * CONVERSATION between client and gateway (a subscription silently dropped for a missing
 * readerKey; a hello handled in the wrong dispatch loop), not in either side alone.
 *
 * So this speaks to a real gateway over a real socket, and asserts the thing the whole
 * processrun-first drive is about: by the time the client holds a mailbox, that mailbox's
 * owner is a committed processrun row. The harness verifies that in the database afterwards
 * (see e2e-testing/harness/test_scenarios_hermes_ws.go).
 *
 * Credential is an SSH key: possession of SSH keys is the process-class credential, so this
 * exercises the INLINE LOGIN variant — login-begin, sign the nonce, login-complete, session —
 * all on one socket, one TLS handshake instead of three.
 *
 * Contract with the harness (stdout is parsed, so these lines are load-bearing):
 *   WS-PROBE-OK address=<mailbox address> processUid=<processrun uid>
 *   WS-PROBE-FAIL <reason>
 */
object WsBootstrapProbeMain extends Logging {

  def main(args: Array[String]): Unit = {
    val meshRootUrl = required("MESH_ROOT_URL")
    val privateKey = required("SSH_PRIVATE_KEY")
    val processUid = required("PROCESS_UID")

    try {
      val publicKey = SshAuth.readPublicKey(privateKey + ".pub")

      val mailbox =
        WsMailbox.bootstrap(
          meshRootUrl = meshRootUrl,
          processUid = processUid,
          appName = "hermes-ws-probe",
          sshPublicKey = publicKey,
          sshOrigin = "hermes-e2e",
          signNonce = nonce => SshAuth.signNonce(nonce, privateKey),
        )

      val address = mailbox.metadata.address.value
      if (address.isEmpty) {
        fail("gateway returned a mailbox with no address")
      } else {
        println(s"WS-PROBE-OK address=$address processUid=$processUid")
        System.out.flush()
        // Leave the socket open briefly so the harness can observe a live, subscribed
        // connection rather than one that raced to exit.
        Thread.sleep(2000)
      }
    } catch {
      case e: Throwable =>
        fail(s"${e.getClass.getSimpleName}: ${e.getMessage}")
    }
    // The JDK HttpClient keeps non-daemon threads alive; exit explicitly.
    System.exit(0)
  }

  private def required(name: String): String =
    sys.env.getOrElse(name, {
      fail(s"$name is not set")
      throw new IllegalStateException("unreachable")
    })

  private def fail(reason: String): Unit = {
    println(s"WS-PROBE-FAIL $reason")
    System.out.flush()
    System.exit(1)
  }

}
