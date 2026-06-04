package a8.hermes.continuum

import a8.hermes.nats.NatsTransport
import a8.hermes.proto.continuum.continuum_rpc.{
  Buffer,
  BufferSource,
  MessageFromRunner,
  ProcessCompletedRequest,
  ProcessPing,
  ProcessPingRequest,
  ProcessStartedRequest,
  StreamRecord,
  StreamWrite,
  UpdateMailboxRequest,
}
import a8.common.logging.Logging
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import io.nats.client.api.{CompressionOption, RetentionPolicy, StreamConfiguration}

import java.time.Duration as JavaDuration

import java.io.Closeable
import java.time.Instant
import java.util.concurrent.{Executors, TimeUnit}

/**
 * Publishes continuum process-lifecycle messages onto the NATS bus, mirroring the godev runner
 * (`godev/runner/continuum-service-client.go`). Each call wraps a request in the `MessageFromRunner`
 * oneof, marshals it to protobuf bytes, and CORE-publishes (not JetStream) to `continuum.central`; the
 * continuum service consumes the `continuum-central` stream and persists to `processrun`.
 *
 * This is what lets checkpoint report lifecycle WITHOUT writing the DB — it is a fire-and-forget bus
 * publisher, distinct from the request/response mailbox RPC used for queries ([[a8.hermes.jdbcrpc.DbRpcClient]]).
 */
object ContinuumRunnerClient extends Logging {

  /** godev `hermes/model/model.go`: NATS subject (dots) for the central lifecycle bus. */
  val ContinuumCentralSubject = "continuum.central"

  /** Default ping cadence — matches the Go runner's 30s ticker. */
  val DefaultPingIntervalMillis = 30_000L

  def nowTimestamp(): Timestamp = {
    val i = Instant.now()
    Timestamp(seconds = i.getEpochSecond, nanos = i.getNano)
  }

}

class ContinuumRunnerClient(transport: NatsTransport) extends Logging {

  import ContinuumRunnerClient.*

  private def publish(msg: MessageFromRunner): Unit = {
    val bytes = msg.toByteArray
    // core publish (fire-and-forget) to the central subject, matching godev runner
    transport.connection.publish(ContinuumCentralSubject, bytes)
  }

  // Mirror godev continuum.ScrubSubjectPart: keep only [A-Za-z0-9_-] in subject/stream name parts.
  private def scrubSubjectPart(s: String): String =
    s.filter(c => c.isLetterOrDigit || c == '-' || c == '_')

  def processStarted(req: ProcessStartedRequest): Unit =
    publish(MessageFromRunner(MessageFromRunner.Message.ProcessStartedRequest(req)))

  def processPing(req: ProcessPingRequest): Unit =
    publish(MessageFromRunner(MessageFromRunner.Message.ProcessPingRequest(req)))

  def processCompleted(req: ProcessCompletedRequest): Unit =
    publish(MessageFromRunner(MessageFromRunner.Message.ProcessCompletedRequest(req)))

  def updateMailbox(req: UpdateMailboxRequest): Unit =
    publish(MessageFromRunner(MessageFromRunner.Message.UpdateMailboxRequest(req)))

  def streamWrite(req: StreamWrite): Unit =
    publish(MessageFromRunner(MessageFromRunner.Message.StreamWrite(req)))

  /**
   * Ensure the per-process JetStream exists for a channel: stream `processrun-{uid}-{channel}` over subject
   * `processrun.{uid}.{channel}`. A worker must create this itself at process start (the godev runner does
   * the same locally) — the continuum.central bus message does NOT create it. Idempotent.
   */
  def ensureStream(processUid: String, channelName: String, maxAge: JavaDuration = JavaDuration.ofHours(24)): Unit = {
    val p = scrubSubjectPart(processUid)
    val c = scrubSubjectPart(channelName)
    val config =
      StreamConfiguration.builder()
        .name(s"processrun-$p-$c")
        .subjects(s"processrun.$p.$c")
        .compressionOption(CompressionOption.S2)
        .retentionPolicy(RetentionPolicy.Limits)
        .maxAge(maxAge)
        .build()
    try transport.jetStreamManagement.addStream(config)
    catch
      case e: io.nats.client.JetStreamApiException if e.getMessage.contains("already in use") => ()
      case e: Exception => logger.warn(s"ensureStream failed for processrun-$p-$c", e)
  }

  /**
   * Convenience: publish one chunk of per-process I/O onto `processrun.{uid}.{channel}`. Call ensureStream
   * first (at process start) so a JetStream is bound to the subject; otherwise the publish is dropped.
   */
  def streamWrite(processUid: String, channelName: String, data: Array[Byte], source: BufferSource): Unit = {
    val ts = nowTimestamp()
    streamWrite(
      StreamWrite(
        processUid = processUid,
        channelName = channelName,
        record = Some(
          StreamRecord(
            timestamp = Some(ts),
            sequence = 0L,
            buffers = Seq(Buffer(timestamp = Some(ts), data = ByteString.copyFrom(data), source = source)),
          )
        ),
      )
    )
  }

  /**
   * Convenience: ping a single process with the current channel byte-counts and a liveness status.
   * status "" / "running" = actively working; "paused" = alive but yielded/checkpointed (the process keeps
   * pinging so it is not AWOL-swept, but is reported as paused rather than running).
   */
  def ping(processUid: String, channelSizes: Map[String, Long], status: String = ""): Unit =
    processPing(
      ProcessPingRequest(
        pings = Seq(ProcessPing(processUid = processUid, channelSizes = channelSizes, status = status)),
        timestamp = Some(nowTimestamp()),
      )
    )

  /**
   * Start a background 30s ping loop for a process. `channelSizes` is re-evaluated each tick so callers
   * can report live byte-counts. Returns a Closeable that stops the loop.
   */
  def startPingLoop(processUid: String, channelSizes: () => Map[String, Long], intervalMillis: Long = DefaultPingIntervalMillis): Closeable = {
    val exec = Executors.newSingleThreadScheduledExecutor { r =>
      val t = new Thread(r, s"continuum-ping-${processUid.take(8)}")
      t.setDaemon(true)
      t
    }
    val task = new Runnable {
      override def run(): Unit =
        try ping(processUid, channelSizes())
        catch case e: Throwable => logger.warn(s"continuum ping failed for $processUid", e)
    }
    exec.scheduleAtFixedRate(task, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS)
    () => exec.shutdownNow()
  }

}
