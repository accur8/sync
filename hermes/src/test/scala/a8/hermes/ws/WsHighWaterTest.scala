package a8.hermes.ws

import a8.hermes.proto.process.wsmessages as ws
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.net.URI

/**
 * Pins HIGH-WATER RESUME — invariant 2 of godev docs/mesh-client/client-invariants.md.
 *
 * The resume hello used to re-assert the bootstrap subscriptions VERBATIM (startSeq
 * "first"), replaying the entire stream history on every reconnect and handing the caller
 * every pre-outage message a second time. The mark that fixes it must be monotonic by
 * construction: delivery is at-least-once, so a redelivered LOWER sequence is legal input
 * that must neither reach the caller nor regress the mark (the reference client's defect,
 * BUG-20260801-react-playground-resume-off-by-one).
 *
 * See tracker FEATURE-20260801-hermes-ws-resume-from-high-water.
 */
class WsHighWaterTest extends AnyFunSuite with Matchers {

  private def freshConn: WsMeshConnection =
    new WsMeshConnection(URI.create("ws://localhost:1/api/ws/send_receive_proto"))

  private def mailboxSub(id: String, startSeq: String): ws.Subscription =
    ws.Subscription(
      ws.Subscription.Oneof.Mailbox(
        ws.MailboxSubscription(id = id, channel = id, startSeq = startSeq)
      )
    )

  // --- the mark ---------------------------------------------------------------

  test("mark is monotonic: a lower sequence after a higher one is dropped, not regressive") {
    val conn = freshConn
    conn.observeDelivery("rpc-inbox", 5) shouldBe true
    conn.observeDelivery("rpc-inbox", 3) shouldBe false // redelivery — drop
    conn.observeDelivery("rpc-inbox", 5) shouldBe false // replay at the mark — drop
    conn.observeDelivery("rpc-inbox", 7) shouldBe true
    conn.staleDrops shouldBe 2

    // The mark must have ended at 7, not 3: after the dropped 3, seq 4 is still stale.
    conn.observeDelivery("rpc-inbox", 4) shouldBe false
  }

  test("marks are per subscription id") {
    val conn = freshConn
    conn.observeDelivery("rpc-inbox", 100) shouldBe true
    conn.observeDelivery("other", 1) shouldBe true
  }

  test("frames without an attributable subscription or sequence pass through untracked") {
    val conn = freshConn
    conn.observeDelivery("", 9) shouldBe true
    conn.observeDelivery("rpc-inbox", 0) shouldBe true
    conn.staleDrops shouldBe 0
  }

  // --- the resume rewrite -----------------------------------------------------

  test("a marked subscription resumes at mark+1; an unmarked one keeps its bootstrap start") {
    val conn = freshConn
    val subs = Seq(mailboxSub("rpc-inbox", "first"), mailboxSub("other", "first"))

    // No marks yet: unchanged.
    conn.resumeSubscriptions(subs).map(_.getMailbox.startSeq) shouldBe Seq("first", "first")

    conn.observeDelivery("rpc-inbox", 41)
    val out = conn.resumeSubscriptions(subs)
    // +1 because a numeric startSeq is INCLUSIVE gateway-side (nats.StartSequence).
    out.head.getMailbox.startSeq shouldBe "42"
    out(1).getMailbox.startSeq shouldBe "first"

    // The retained bootstrap spec is untouched (ScalaPB messages are immutable, but the
    // SEQUENCE of specs handed back must not have replaced the unmarked one either).
    subs.head.getMailbox.startSeq shouldBe "first"
  }
}
