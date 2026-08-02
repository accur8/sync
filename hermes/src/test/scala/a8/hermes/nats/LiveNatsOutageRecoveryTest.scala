package a8.hermes.nats

import a8.hermes.core.MailboxTransport.{AckPolicy, ConsumerConfig, DeliverPolicy, StreamRetention}
import a8.shared.app.{AppCtx, BootstrappedIOApp}
import io.nats.client.Nats

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.*

/**
 * LIVE proof of the outage-loss fix (BUG-20260801-hermes-nats-mailbox-outage-loss):
 * messages published while the reader's connection is DOWN are delivered after it comes
 * back, because the durable consumer's position lives server-side.
 *
 * Phases use SEPARATE connections, which is the honest outage model — phase 1's
 * connection is fully closed (not just unsubscribed) before the gap is published:
 *
 *   1. conn1: bind durable, receive m1, CLOSE THE CONNECTION.
 *   2. conn0 (publisher): publish m2, m3 — nobody is listening. Under the old
 *      core-NATS subscribe these were silently lost, permanently.
 *   3. conn2: bind the SAME durable — m2 and m3 must arrive, in order.
 *
 * Requires a local JetStream server:  nix run nixpkgs#nats-server -- -js
 * Run: sbt "hermes/Test/runMain a8.hermes.nats.LiveNatsOutageRecoveryTest"
 * (NATS_TEST_URL overrides nats://localhost:4222)
 */
object LiveNatsOutageRecoveryTest extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit = {
    val url = sys.env.getOrElse("NATS_TEST_URL", "nats://localhost:4222")
    val rand = scala.util.Random.alphanumeric.take(8).mkString.toLowerCase
    val streamName = s"outage-smoke-$rand"
    val subject = s"outage.smoke.$rand"
    val durableName = s"rdr-$rand"

    val conn0 = Nats.connect(url)
    val publisher = new NatsTransport(conn0)
    publisher.createStream(streamName, Seq(subject), StreamRetention.Limits, 1.hour)
    logger.info(s"stream $streamName created for $subject")

    def durable = ConsumerConfig.Durable(
      consumerName = durableName,
      deliverPolicy = DeliverPolicy.New,
      ackPolicy = AckPolicy.Explicit,
      inactiveThreshold = Some(5.minutes),
    )

    def publish(body: String): Unit =
      publisher.publish(subject, Map("smoke" -> body), body.getBytes("UTF-8"))

    // collectUntil: consume the durable on its own connection until `want` messages
    // arrive (or timeout), then close that CONNECTION — the outage — and return what
    // was seen. The reader thread ends when the closed connection kills the
    // subscription.
    def readPhase(want: Int, timeoutMs: Long, afterBind: () => Unit): Seq[String] = {
      val conn = Nats.connect(url)
      val transport = new NatsTransport(conn)
      val seen = new ConcurrentLinkedQueue[String]()
      val reader = new Thread(() => {
        try
          transport
            .createConsumer(subject, durable)
            .runForeach(env => { seen.add(new String(env.payload, "UTF-8")); () })
        catch { case _: Exception => () } // the connection close ends the phase
      })
      reader.setDaemon(true)
      reader.start()
      // The durable bind happens inside the reader thread; give it a beat, then act.
      Thread.sleep(1500)
      afterBind()
      val deadline = System.currentTimeMillis() + timeoutMs
      while (seen.size < want && System.currentTimeMillis() < deadline)
        Thread.sleep(100)
      conn.close() // THE OUTAGE: interest gone entirely, not merely unsubscribed
      reader.join(10000)
      seen.toArray(Array.empty[String]).toSeq
    }

    // Phase 1: durable exists, m1 flows.
    val got1 = readPhase(1, 15000, () => publish("m1"))
    logger.info(s"phase 1 received: $got1")

    // Phase 2: NOBODY is connected for this durable. The old core-NATS subscribe
    // loses these two forever.
    publish("m2")
    publish("m3")
    logger.info("phase 2: published m2, m3 into the gap (no reader connected)")

    // Phase 3: same durable, fresh connection — the gap must replay.
    val got2 = readPhase(2, 15000, () => ())
    logger.info(s"phase 3 received: $got2")

    // Cleanup before verdict so a failed assert still leaves a clean server.
    try conn0.jetStreamManagement().deleteStream(streamName)
    catch { case e: Exception => logger.warn(s"stream cleanup failed: ${e.getMessage}") }
    conn0.close()

    val pass = got1 == Seq("m1") && got2 == Seq("m2", "m3")
    if (pass) logger.info("PASS: outage gap (m2, m3) delivered after reconnect, in order")
    else {
      logger.error(s"FAIL: phase1=$got1 (want [m1])  phase3=$got2 (want [m2, m3])")
      throw new RuntimeException("outage recovery smoke FAILED")
    }
  }
}
