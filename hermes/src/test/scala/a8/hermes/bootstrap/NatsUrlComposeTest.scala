package a8.hermes.bootstrap

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for the NATS URL composition in HermesBootstrapConfig, in particular
 * the hostname -> A-record expansion that mirrors godev's NatsBlock.composeURL.
 * Driven through the package-private fromConfig so we exercise the real loader.
 */
class NatsUrlComposeTest extends AnyFunSuite with Matchers {

  private def natsUrlFor(hocon: String): String =
    HermesBootstrapConfig.fromConfig(ConfigFactory.parseString(hocon).resolve()).natsUrl

  test("literal IPs pass through unchanged (regression guard)") {
    val url = natsUrlFor(
      """
        |nats {
        |  servers: [ "172.26.5.71", "172.26.5.72:5222", "172.26.5.73" ]
        |  user: prod
        |  password: secret
        |}
        |""".stripMargin
    )
    url shouldEqual
      "nats://prod:secret@172.26.5.71:4222,nats://prod:secret@172.26.5.72:5222,nats://prod:secret@172.26.5.73:4222"
  }

  test("a hostname is resolved and expanded to its A-record IP(s)") {
    // localhost reliably resolves to 127.0.0.1 (and optionally ::1). Assert the
    // host was replaced by its resolved address, tolerant of any extra IPv6 entry.
    val url = natsUrlFor(
      """
        |nats {
        |  servers: [ "localhost" ]
        |  user: u
        |  password: p
        |}
        |""".stripMargin
    )
    url should include("nats://u:p@127.0.0.1:4222")
    url should not include "localhost"
  }

  test("an unresolvable hostname falls back to itself (fail-soft)") {
    // `.invalid` is the RFC 6761 reserved TLD guaranteed not to resolve.
    val url = natsUrlFor(
      """
        |nats {
        |  servers: [ "nats.invalid" ]
        |  user: u
        |  password: p
        |}
        |""".stripMargin
    )
    url shouldEqual "nats://u:p@nats.invalid:4222"
  }

}
