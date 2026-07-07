package a8.hermes.nats

import a8.shared.json.parse
import a8.shared.json.ast.{JsObj, JsNum, JsStr}
import a8.shared.SharedImports.jsonCodecOps
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for the mailbox aliveness path (NatsMailboxClient).
 *
 * With mailbox records in pg behind the mesh mailbox-records endpoints, the touch is
 * fetch-current -> debounce -> update-with-bumped-lastActivity. The load-bearing
 * invariants here:
 *   - the DEBOUNCE rule (shouldTouch) keys off the STORED lastActivity;
 *   - the MailboxKVData codec ROUND-TRIPS publicMetadata/privateMetadata, so the
 *     fetch->update cycle can never drop them (the server additionally preserves
 *     the identity columns when an update omits them — godev Mailbox.ApplyDto).
 */
class MailboxTouchTest extends AnyFunSuite with Matchers {

  // A stored mailbox record as the mesh server serves it from pg.
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
      |  "privateMetadata": {"note": "freeform client data"},
      |  "channels": ["rpc-inbox"],
      |  "isNamed": false,
      |  "mailboxKind": "short-lived-cli",
      |  "processRunUid": "prtestprocessrun00000000000000ab"
      |}""".stripMargin

  test("touch fires when the stored lastActivity is outside the debounce window") {
    val now = 1000L + (6L * 60 * 1000) // 6 min later, > 5 min debounce
    NatsMailboxClient.shouldTouch(1000L, now) shouldBe true
  }

  test("touch is debounced inside the 5-min window") {
    val now = 1000L + (2L * 60 * 1000) // 2 min later, < 5 min debounce
    NatsMailboxClient.shouldTouch(1000L, now) shouldBe false
  }

  test("derived ping interval is shorter than the purge timeout") {
    // short-lived: min(5min, 15min/3=5min) = 5min
    NatsMailboxClient.pingIntervalMillis(15L * 60 * 1000) shouldBe (5L * 60 * 1000)
    // long-lived (90d): min(5min, 30d) = 5min
    NatsMailboxClient.pingIntervalMillis(90L * 24 * 60 * 60 * 1000) shouldBe (5L * 60 * 1000)
    // very short timeout floors at 30s rather than busy-looping
    NatsMailboxClient.pingIntervalMillis(30L * 1000) shouldBe (30L * 1000)
  }

  test("MailboxKVData codec round-trips metadata (fetch->update can never drop it)") {
    import a8.shared.json.ast.JsDoc
    val jsdoc = JsDoc.jsDocRoot(parse(storedJson).toOption.get)
    val data = NatsMailboxClient.MailboxKVData.jsonCodec.read(jsdoc) match {
      case Right(d)  => d
      case Left(err) => fail(s"codec read failed: $err")
    }
    data.privateMetadata.values.get("note") shouldBe Some(JsStr("freeform client data"))
    data.publicMetadata.values.get("env") shouldBe Some(JsStr("prod"))
    data.mailboxKind shouldBe "short-lived-cli"
    data.processRunUid shouldBe "prtestprocessrun00000000000000ab"

    // The exact shape a touch sends: same record, only lastActivity bumped.
    val bumped = data.copy(lastActivity = 999999L)
    val reread = NatsMailboxClient.MailboxKVData.jsonCodec.read(
      JsDoc.jsDocRoot(parse(bumped.compactJson).toOption.get)
    ).toOption.get
    reread.lastActivity shouldBe 999999L
    reread.privateMetadata shouldBe data.privateMetadata
    reread.publicMetadata shouldBe data.publicMetadata
    reread.adminKey shouldBe data.adminKey
    reread.channels shouldBe data.channels
  }
}
