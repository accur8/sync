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
  StreamCreatedRequest,
  StreamRecord,
  StreamWrite,
  UpdateMailboxRequest,
}
import a8.common.logging.Logging
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import io.nats.client.api.{CompressionOption, Placement, RetentionPolicy, StreamConfiguration}

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

  /**
   * Number of tagged nodes to spread R=1 streams across. Mirrors godev
   * `a8nats.PlacementNodes()`: the HA replication factor on a multi-node cluster, else 1.
   */
  val HaReplicationFactor = 3

  /**
   * Port of godev `a8nats.PlacementTagForSeed` (a8nats/a8nats.go:895). Deterministically
   * picks the placement tag ("n1".."nN") for an R=1 stream from a stable seed (the
   * processrun uid) — stateless, coordination-free, uniform across the tagged nodes.
   * Returns "" (no placement) when nodes <= 1 or the seed is empty.
   *
   * MUST agree with the Go implementation for every seed: the server recomputes the same
   * tag from the same seed to store on the `processrun` row (godev
   * `pkg/registry/database.go:81`), so any divergence records a node the stream is not on.
   * Both sides are pinned to one golden corpus — [[PlacementTagForSeedTest]] here and
   * `placementGoldens` / `TestPlacementTagForSeedGoldens` in godev `a8nats/placement_test.go`.
   *
   * Go does `int(h.Sum32()) % nodes + 1` where Sum32 is a uint32 — on 64-bit Go, `int(...)`
   * is the UNSIGNED value, so the modulo is never negative. Scala's Int is signed 32-bit,
   * so widen to Long via `& 0xFFFFFFFFL` before the modulo or seeds hashing above
   * Int.MaxValue (e.g. "a" -> 3826002220) would go negative.
   */
  def placementTagForSeed(nodes: Int, seed: String): String =
    if (nodes <= 1 || seed.isEmpty) ""
    else {
      // FNV-1a, 32-bit. Int arithmetic wraps exactly as uint32 does bitwise.
      var hash = 0x811c9dc5 // 2166136261
      seed.getBytes(java.nio.charset.StandardCharsets.UTF_8).foreach { b =>
        hash = (hash ^ (b & 0xff)) * 0x01000193
      }
      val unsigned = hash.toLong & 0xffffffffL
      s"n${unsigned % nodes + 1}"
    }

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
    val streamName = s"processrun-$p-$c"

    // R=1 processrun streams get a placement tag (hash of the processrun uid) so they
    // spread across the tagged cluster nodes instead of piling onto one. Node count comes
    // from the live NATS connection; "" when single-node (nothing to spread across). The
    // chosen tag rides the StreamCreatedRequest below so the server records it.
    // Seed on the RAW processUid, not the scrubbed `p` — godev seeds the raw uid on both
    // the leaf (continuum-service-client.go:211) and the server (registry/database.go:81).
    val placementTag = placementTagForSeed(placementNodes, processUid)

    def configBuilder =
      StreamConfiguration.builder()
        .name(streamName)
        .subjects(s"processrun.$p.$c")
        .compressionOption(CompressionOption.S2)
        .retentionPolicy(RetentionPolicy.Limits)
        .maxAge(maxAge)

    def build(withPlacement: Boolean) = {
      val b = configBuilder
      if (withPlacement && placementTag.nonEmpty)
        b.placement(Placement.builder().tags(placementTag).build())
      b.build()
    }

    // Returns true when THIS call created the stream (so we owe a ledger record).
    def add(config: StreamConfiguration): Boolean =
      try { transport.jetStreamManagement.addStream(config); true }
      catch case e: io.nats.client.JetStreamApiException if e.getMessage.contains("already in use") => false

    val created =
      try add(build(withPlacement = true))
      catch {
        case e: Exception if placementTag.nonEmpty =>
          // Tagged node can't take it (down, or tags unconfigured) — availability beats
          // balance: retry unplaced, mirroring the godev runner.
          logger.warn(s"placement $placementTag failed for $streamName, retrying without placement", e)
          try add(build(withPlacement = false))
          catch {
            case e2: Exception =>
              logger.warn(s"ensureStream failed for $streamName", e2)
              false
          }
        case e: Exception =>
          logger.warn(s"ensureStream failed for $streamName", e)
          false
      }

    // We created a NATS stream, so create its ledger record. The leaf has no pg; notify
    // the server (the sole stream-ledger writer) which does the insert. Fire-and-forget:
    // the stream already exists and logging must not fail on a bookkeeping publish — a
    // miss is a leak the stream-hygiene audit catches.
    if (created) {
      try
        publish(
          MessageFromRunner(
            MessageFromRunner.Message.StreamCreatedRequest(
              StreamCreatedRequest(
                processUid = processUid,
                channelName = channelName,
                replicas = 1, // processrun log streams are LimitsPolicy R=1
                placementTag = placementTag,
              )
            )
          )
        )
      catch case e: Exception => logger.warn(s"ensureStream: failed to publish StreamCreated for $streamName", e)
    }
  }

  /** godev `a8nats.PlacementNodes()`: replication factor when multi-node, else 1. */
  private def placementNodes: Int =
    if (transport.connection.getServers.size > 1) HaReplicationFactor else 1

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
