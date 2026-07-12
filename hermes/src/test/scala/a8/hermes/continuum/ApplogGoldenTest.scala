package a8.hermes.continuum

import a8.hermes.proto.continuum.continuum_rpc.{Buffer, BufferSource, LogLevel, LogRecord, StreamRecord}
import ch.qos.logback.classic.spi.ThrowableProxy
import com.google.protobuf.timestamp.Timestamp
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path, Paths}

/**
 * CROSS-LANGUAGE GOLDEN — the Scala half.
 *
 * Writes a StreamRecord, built exactly as [[ApplogAppender]] builds one, to
 * `hermes/src/test/resources/applog-golden.bin`. The GO half
 * (`godev/applog/golden_xlang_test.go`, `TestScalaGoldenDecodes`) reads that FILE and
 * asserts every field — so "a Scala process's log is readable by `a8 logs`" stops
 * being a claim and becomes a test.
 *
 * This is the same two-sided pin as [[PlacementTagForSeedTest]], and it exists for the
 * same reason: the two languages agree only where something forces them to. A
 * unit test that serializes and deserializes within ONE language proves the language is
 * self-consistent, which is not the property we need.
 *
 * The bytes are committed. Regenerating them is a deliberate act (run this test, commit
 * the file) and MUST be accompanied by the Go side still passing — if it does not, the
 * wire format just broke and one of the two emitters is wrong.
 */
class ApplogGoldenTest extends AnyFunSuite with Matchers {

  /** Where the golden lands. Relative to the hermes module root when sbt runs. */
  private val goldenPath: Path = Paths.get("src/test/resources/applog-golden.bin")

  test("write the cross-language golden the Go decoder reads") {
    val root = new java.io.IOException("connection refused")
    val boom = new RuntimeException("dialing nats", root)

    // Fixed values — the Go side asserts these exact ones. No clock, no randomness:
    // a golden must be reproducible or it is just a snapshot of one run.
    val ts = Timestamp(1_752_000_000L, 123_000_000)

    val info =
      LogRecord(
        level = LogLevel.info,
        caller = "a8.hermes.Continuum",
        thread = "main",
        goid = 0L, // the JVM leaves this to Go
        message = "hello from scala",
        rendered = "12:00:00.123 | INFO | main | a8.hermes.Continuum | hello from scala",
        extraData = Map("requestId" -> "abc123"),
      )

    val err =
      LogRecord(
        level = LogLevel.error,
        caller = "a8.hermes.Nats",
        thread = "nats-worker-3",
        message = "dialing nats",
        error = ApplogAppender.errorOf(new ThrowableProxy(boom)),
        droppedRecords = 15L, // the writer admitting its spool overflowed
      )

    val sr =
      StreamRecord(
        timestamp = Some(ts),
        sequence = 7L,
        writerEpoch = 12_345L,
        buffers = Seq(
          Buffer(timestamp = Some(ts), source = BufferSource.applog, record = Some(info)),
          Buffer(timestamp = Some(ts), source = BufferSource.applog, record = Some(err)),
        ),
      )

    Files.createDirectories(goldenPath.getParent)
    Files.write(goldenPath, sr.toByteArray)

    // Sanity: it round-trips here too.
    val parsed = StreamRecord.parseFrom(Files.readAllBytes(goldenPath))
    parsed.buffers should have size 2
    parsed.buffers(1).record.flatMap(_.error).flatMap(_.cause).map(_.message) shouldBe Some("connection refused")

    println(s"applog golden: wrote ${Files.size(goldenPath)} bytes to ${goldenPath.toAbsolutePath}")
  }

}
