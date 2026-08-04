package a8.hermes.nats

import a8.hermes.core.MailboxTransport.Envelope
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Pins the ACK-BEFORE-DELIVER rule at the only level a unit test can reach it: the
 * Envelope's ack contract.
 *
 * The rule is one sentence — the transport never acks on the consumer's behalf — and it has
 * now been broken twice in the same file. `createConsumer` acked at the pull, which let
 * stream prefetch ack whole chunks the app had not seen; a connection death then dropped
 * them acked-but-unseen and the server would not redeliver, measured as contiguous holes
 * with the consumer reporting ackPending=0 redelivered=0
 * (BUG-20260802-sync-nats-kill-loses-inflight-publishes). That one was fixed. Its sibling
 * `subscribe` kept acking inside the message handler, before anything read the queue, and
 * survived the fix untouched because nothing tested the rule
 * (BUG-20260804-nats-transport-subscribe-still-acks-before-deliver).
 *
 * A full test of either path needs a live JetStream server — LiveNatsOutageRecoveryTest is
 * where that belongs. What is cheap and worth pinning here is the invariant those paths
 * depend on: the default is ack-free, and an attached ack fires ONLY when the consumer
 * fires it.
 */
class EnvelopeAckContractTest extends AnyFunSuite with Matchers {

  private def envelope(ack: () => Unit = () => ()): Envelope =
    Envelope(subject = "mesh.aaSomeMailbox.rpc-inbox", headers = Map.empty, payload = Array.empty[Byte], ack = ack)

  test("the default ack is a no-op, so a core-NATS path acks nothing") {
    // subscribe() hands core-NATS messages straight through with this default. If the
    // default ever becomes anything else, every non-JetStream subscriber starts acking
    // deliveries it has no business acking.
    noException should be thrownBy envelope().ack()
  }

  test("an attached ack does not fire on construction — only when the consumer calls it") {
    // The whole point: building the envelope is the DELIVERY, calling ack is the
    // ACKNOWLEDGEMENT, and they are separate events. Acking at construction is precisely
    // the bug this pins against.
    var acked = 0
    val e = envelope(() => acked += 1)
    acked shouldBe 0

    e.ack()
    acked shouldBe 1
  }

  test("ack is per-delivery: two envelopes ack independently") {
    // A shared or collapsed ack would let one consumer's progress ack another's unseen
    // message — the same class of silent loss, arrived at from the other direction.
    var a, b = 0
    envelope(() => a += 1).ack()
    a shouldBe 1
    b shouldBe 0
    envelope(() => b += 1).ack()
    a shouldBe 1
    b shouldBe 1
  }
}
