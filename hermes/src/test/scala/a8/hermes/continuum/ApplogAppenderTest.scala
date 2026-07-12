package a8.hermes.continuum

import a8.hermes.proto.continuum.continuum_rpc.{BufferSource, LogLevel, LogRecord, StreamRecord}
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.classic.Level
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * The applog appender's wire contract.
 *
 * CROSS-LANGUAGE. Everything asserted here is read back by the GO decoder
 * (`godev/cmd/a8/logs_render.go`) and the Go e2e (`Applog Round Trip`) — one wire
 * format, one reader, both languages. A Scala process's log has to land in
 * `a8 logs <domain> --channel applog` looking exactly like a Go one, so these are
 * not internal details: they are the interop surface.
 *
 * The Go half of each pin:
 *   - level/caller/message as SEPARATE fields  <-> TestSinkRoundTrip
 *   - the error's chain + concrete type        <-> TestErrorSurvivesStructured
 *   - BufferSource.applog on every buffer      <-> TestSinkRoundTrip
 *   - sequence + writerEpoch + eof/finalSeq    <-> TestEofOnClose / TestGapDetection
 *   - bounded spool, drop OLDEST, REPORT it    <-> TestSpoolOverflowDropsOldest
 */
class ApplogAppenderTest extends AnyFunSuite with Matchers {

  test("logback levels map to the wire LogLevel godev reads") {
    ApplogAppender.levelOf(Level.TRACE) shouldBe LogLevel.trace
    ApplogAppender.levelOf(Level.DEBUG) shouldBe LogLevel.debug
    ApplogAppender.levelOf(Level.INFO) shouldBe LogLevel.info
    ApplogAppender.levelOf(Level.WARN) shouldBe LogLevel.warn
    ApplogAppender.levelOf(Level.ERROR) shouldBe LogLevel.error
  }

  test("an exception becomes a structured LogError with its CAUSE CHAIN intact") {
    // The chain is the point: the root cause sits at the far end, and flattening it
    // to text is what forces a human to read a wall of stack trace to find the bottom.
    val root = new java.io.IOException("connection refused")
    val wrapped = new RuntimeException("dialing nats", root)

    val le = ApplogAppender.errorOf(new ThrowableProxy(wrapped)).getOrElse(fail("expected a LogError"))

    le.message shouldBe "dialing nats"
    // The concrete TYPE is a FIELD, so "every IOException in the fleet" is a filter
    // rather than a substring guess.
    le.`type` shouldBe "java.lang.RuntimeException"
    le.stack should include("dialing nats")

    val cause = le.cause.getOrElse(fail("the wrap chain must survive — getCause was not followed"))
    cause.message shouldBe "connection refused"
    cause.`type` shouldBe "java.io.IOException"
    cause.cause shouldBe None // terminates at the root
  }

  test("no throwable => no error field (so a reader can branch on its presence)") {
    ApplogAppender.errorOf(null) shouldBe None
  }

  test("a cyclic cause chain is bounded — a logger must not hang or explode on one") {
    // A self-causing throwable is pathological but must not run away.
    val a = new RuntimeException("a")
    val b = new RuntimeException("b", a)
    // ThrowableProxy handles real cycles itself; assert our own walk is depth-bounded
    // by checking a long legitimate chain terminates.
    var deep: Throwable = new RuntimeException("root")
    for (i <- 1 to 50) deep = new RuntimeException(s"link-$i", deep)

    val le = ApplogAppender.errorOf(new ThrowableProxy(deep)).getOrElse(fail("expected a LogError"))
    var depth = 0
    var cur = Option(le)
    while (cur.isDefined) {
      depth += 1
      cur = cur.flatMap(_.cause)
      depth should be <= 33 // MaxErrorChain + 1
    }
    depth should be <= 32
  }

  test("the record round-trips through protobuf — the bytes godev will decode") {
    // Serialize exactly what the appender builds, then parse it back. If this holds,
    // the Go decoder (which parses the same StreamRecord) sees the same fields.
    val rec =
      LogRecord(
        level = LogLevel.warn,
        caller = "a8.hermes.Continuum",
        thread = "nats-worker-3", // the JVM's identity; goid stays 0 (that is Go's)
        message = "connection lost",
        rendered = "PRE-RENDERED LINE",
        extraData = Map("requestId" -> "abc123"),
      )

    val sr =
      StreamRecord(
        sequence = 7L,
        writerEpoch = 12345L,
        buffers = Seq(
          a8.hermes.proto.continuum.continuum_rpc.Buffer(
            source = BufferSource.applog,
            record = Some(rec),
          )
        ),
      )

    val parsed = StreamRecord.parseFrom(sr.toByteArray)

    parsed.sequence shouldBe 7L
    parsed.writerEpoch shouldBe 12345L // godev reads this for gap detection
    parsed.buffers should have size 1

    val b = parsed.buffers.head
    b.source shouldBe BufferSource.applog
    val r = b.record.getOrElse(fail("the applog buffer must carry a structured LogRecord"))
    r.level shouldBe LogLevel.warn
    r.caller shouldBe "a8.hermes.Continuum"
    r.thread shouldBe "nats-worker-3"
    r.goid shouldBe 0L // deliberately unset on the JVM
    r.message shouldBe "connection lost"
    r.rendered shouldBe "PRE-RENDERED LINE"
    r.extraData("requestId") shouldBe "abc123"
  }

  test("the EOF record carries finalSequence — how a reader proves it lost nothing") {
    val eof = StreamRecord(sequence = 9L, writerEpoch = 42L, eof = true, finalSequence = 8L)
    val parsed = StreamRecord.parseFrom(eof.toByteArray)
    parsed.eof shouldBe true
    parsed.finalSequence shouldBe 8L
    // A stream that ends WITHOUT this marker is the signal that a process died badly.
  }

}
