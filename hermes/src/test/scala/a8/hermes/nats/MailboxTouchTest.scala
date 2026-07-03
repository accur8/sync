package a8.hermes.nats

import a8.shared.json.parse
import a8.shared.json.ast.{JsObj, JsNum, JsStr}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for NatsMailboxClient.patchLastActivity — the raw-JSON-patch that the
 * mailbox pinger uses to refresh lastActivity in the KV store.
 *
 * The load-bearing invariant: the patch replaces ONLY lastActivity and preserves every
 * other key — CRITICALLY privateMetadata, which carries the bound SSH identity the SQL
 * firewall depends on. A naive MailboxKVData re-marshal would drop it (the case class is
 * a lossy subset); this guards against that regression.
 */
class MailboxTouchTest extends AnyFunSuite with Matchers {

  // A stored mailbox record as godev writes it, including publicMetadata / privateMetadata
  // which the Scala MailboxKVData case class does NOT model.
  private val storedJson =
    """{
      |  "adminKey": "zzADMIN",
      |  "readerKey": "rrREADER",
      |  "address": "aaADDRESS",
      |  "created": 1000,
      |  "lastActivity": 1000,
      |  "purgeTimeoutInMillis": 900000,
      |  "closeTimeoutInMillis": 900000,
      |  "publicMetadata": {"env": "prod"},
      |  "privateMetadata": {"authToken": "SECRET-IDENTITY-TOKEN", "boundAt": 1234},
      |  "channels": ["rpc-inbox"],
      |  "isNamed": false,
      |  "mailboxKind": "short-lived-cli"
      |}""".stripMargin

  private def objOf(json: String): JsObj =
    parse(json).toOption.get.asInstanceOf[JsObj]

  test("patch replaces lastActivity when outside the debounce window") {
    val now = 1000L + (6L * 60 * 1000) // 6 min later, > 5 min debounce
    val patched = NatsMailboxClient.patchLastActivity(storedJson, now)
    patched should not be empty
    val obj = objOf(patched.get)
    obj.values("lastActivity") shouldBe JsNum(BigDecimal(now))
  }

  test("patch is debounced (no write) inside the 5-min window") {
    val now = 1000L + (2L * 60 * 1000) // 2 min later, < 5 min debounce
    NatsMailboxClient.patchLastActivity(storedJson, now) shouldBe None
  }

  test("patch PRESERVES privateMetadata (the bound identity) and every other key") {
    val now = 1000L + (10L * 60 * 1000)
    val obj = objOf(NatsMailboxClient.patchLastActivity(storedJson, now).get)

    // privateMetadata (identity) survives byte-for-byte
    val priv = obj.values("privateMetadata").asInstanceOf[JsObj]
    priv.values("authToken") shouldBe JsStr("SECRET-IDENTITY-TOKEN")
    priv.values("boundAt") shouldBe JsNum(BigDecimal(1234))

    // every other key is unchanged
    obj.values("publicMetadata").asInstanceOf[JsObj].values("env") shouldBe JsStr("prod")
    obj.values("adminKey") shouldBe JsStr("zzADMIN")
    obj.values("readerKey") shouldBe JsStr("rrREADER")
    obj.values("address") shouldBe JsStr("aaADDRESS")
    obj.values("created") shouldBe JsNum(BigDecimal(1000))
    obj.values("purgeTimeoutInMillis") shouldBe JsNum(BigDecimal(900000))
    obj.values("isNamed") shouldBe a8.shared.json.ast.JsBool(false)
    obj.values("mailboxKind") shouldBe JsStr("short-lived-cli")

    // exactly the same set of keys as the original — nothing added or dropped
    obj.values.keySet shouldBe objOf(storedJson).values.keySet
  }

  test("derived ping interval is shorter than the purge timeout") {
    // short-lived: min(5min, 15min/3=5min) = 5min
    NatsMailboxClient.pingIntervalMillis(15L * 60 * 1000) shouldBe (5L * 60 * 1000)
    // long-lived (90d): min(5min, 30d) = 5min
    NatsMailboxClient.pingIntervalMillis(90L * 24 * 60 * 60 * 1000) shouldBe (5L * 60 * 1000)
    // very short timeout floors at 30s rather than busy-looping
    NatsMailboxClient.pingIntervalMillis(30L * 1000) shouldBe (30L * 1000)
  }
}
