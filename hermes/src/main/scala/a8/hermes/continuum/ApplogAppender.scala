package a8.hermes.continuum

import a8.common.logging.Logging
import a8.hermes.proto.continuum.continuum_rpc.{Buffer, BufferSource, LogError, LogLevel, LogRecord, StreamRecord, StreamWrite}
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.{ILoggingEvent, IThrowableProxy, ThrowableProxyUtil}
import ch.qos.logback.core.AppenderBase
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp

import java.time.Instant
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import java.util.concurrent.{Executors, TimeUnit}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/**
 * Ships this JVM process's OWN log to NATS on the `applog` channel, as structured
 * `LogRecord`s — the Scala twin of godev's `applog` package
 * (`godev/applog/spool.go`), and the reason a Scala process's logs are readable
 * with the same `a8 logs <domain> --channel applog` as a Go one. One wire format,
 * one reader, both languages.
 *
 * This is a SPOOL, not a direct publisher, and that is the whole design:
 *
 * {{{
 *   logger.info(...) -> append to a bounded in-memory spool   (never touches the network)
 *                                  |
 *                           shipper drains -> NATS
 * }}}
 *
 * Two things fall out of it. First, the pre-connection window is covered by
 * construction: a process logs from its first line, long before NATS is dialed, and
 * those records sit in the spool and ship the moment the connection exists. Second,
 * there is NO backpressure coupling — `append()` only ever touches memory, so NATS
 * being down or slow degrades to "the spool grows", never to "the process stalls"
 * or "the logs vanish". A logging appender that can block the application it logs
 * for is a worse failure than the one it is trying to report, and the process most
 * likely to be logging hard is precisely the one whose NATS connection is unhealthy.
 *
 * SCOPE: the spool is a staging buffer, NOT a crash-forensics store. A process that
 * dies before it can ship is a job for the console/file appenders, which are
 * untouched. This assumes a well-behaved program that reaches its shutdown path.
 *
 * Install it at bootstrap (see [[ApplogAppender.install]]) rather than in
 * logback.xml: it needs a live NATS connection and this process's continuum
 * processUid, neither of which exists when logback configures itself. That is also
 * why it lives in `hermes` and not in `logging_logback` — that module is a leaf with
 * no NATS or protobuf dependency, exactly as godev keeps its `log` package a leaf
 * and lets `applog` do the wiring.
 */
object ApplogAppender extends Logging {

  /** The channel name. Must match godev's `applog.Channel`. */
  val Channel = "applog"

  /** Matches godev's applog.DefaultFlushInterval. */
  val DefaultFlushIntervalMillis = 5_000L

  /**
   * Bounds the spool. A spool that NATS never drains must not grow without limit; on
   * overflow we drop the OLDEST records and COUNT the drop, which rides the next
   * shipped record so the loss is REPORTED rather than silent. Dropping oldest keeps
   * the tail — the records nearest the failure. Matches godev's
   * DefaultMaxSpoolRecords.
   */
  val DefaultMaxSpoolRecords = 10_000

  /**
   * Bounds the final flush. A process whose NATS connection is wedged must still
   * exit promptly: we try, we give up, and the records stay unshipped. "Flush on
   * exit" silently becoming "hangs on shutdown" would be a worse bug than the lost
   * logs. Matches godev's 2s logShutdownTimeout.
   */
  val DefaultShutdownTimeoutMillis = 2_000L

  /**
   * Create the applog stream, install the appender on the root logger, and register
   * a shutdown hook that drains it. Returns the appender so a caller can stop it
   * explicitly.
   *
   * `ensureStream` gives us the placement tag AND the continuum.stream ledger row
   * for free — a NATS stream created without its ledger row is what the census
   * reports as `leaked` (live, no owning row) and what the reconciler cannot reap.
   */
  def install(
    client: ContinuumRunnerClient,
    processUid: String,
    maxSpoolRecords: Int = DefaultMaxSpoolRecords,
  ): ApplogAppender = {
    client.ensureStream(processUid, Channel)

    val appender = new ApplogAppender(client, processUid, maxSpoolRecords)
    val rootLogger =
      org.slf4j.LoggerFactory
        .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        .asInstanceOf[ch.qos.logback.classic.Logger]

    appender.setContext(rootLogger.getLoggerContext)
    appender.setName("applog")
    appender.start()
    rootLogger.addAppender(appender)

    Runtime.getRuntime.addShutdownHook(new Thread(
      () => appender.stop(),
      s"applog-shutdown-${processUid.take(8)}",
    ))

    logger.info(s"applog: shipping this process's log to NATS (processUid=$processUid, channel=$Channel)")
    appender
  }

  /** logback Level -> the wire LogLevel. */
  def levelOf(level: Level): LogLevel =
    level.toInt match {
      case Level.TRACE_INT => LogLevel.trace
      case Level.DEBUG_INT => LogLevel.debug
      case Level.INFO_INT  => LogLevel.info
      case Level.WARN_INT  => LogLevel.warn
      case Level.ERROR_INT => LogLevel.error
      case _               => LogLevel.level_unspecified
    }

  /** Guards against a cyclic getCause chain; no real exception is 32 deep. */
  private val MaxErrorChain = 32

  /**
   * A logback throwable -> the structured LogError, following `getCause`.
   *
   * The CHAIN is the point: a wrapped exception is a chain and the root cause — the
   * interesting one — sits at the far end. Flattening it to text is what forces a
   * human to read a wall of stack trace to find the bottom. Each link keeps its own
   * message, its concrete TYPE (so "every IOException in the fleet" is a filter
   * rather than a substring guess), and its frames.
   */
  def errorOf(proxy: IThrowableProxy): Option[LogError] = {
    def convert(p: IThrowableProxy, depth: Int): Option[LogError] =
      if (p == null || depth >= MaxErrorChain) None
      else
        Some(
          LogError(
            message = Option(p.getMessage).getOrElse(""),
            `type` = Option(p.getClassName).getOrElse(""),
            stack = ThrowableProxyUtil.asString(p),
            cause = convert(p.getCause, depth + 1),
          )
        )
    convert(proxy, 0)
  }

}

/**
 * The appender itself. See [[ApplogAppender]] for the design; install it via
 * `ApplogAppender.install`.
 */
class ApplogAppender(
  client: ContinuumRunnerClient,
  processUid: String,
  maxSpoolRecords: Int = ApplogAppender.DefaultMaxSpoolRecords,
) extends AppenderBase[ILoggingEvent] {

  import ApplogAppender.*

  /**
   * Identifies THIS appender instance on every record it emits. `sequence` is
   * contiguous only within one writer's life and restarts at 1 for a fresh writer, so
   * a reader needs the epoch to tell a legitimate reset (a restart) from real loss.
   * Same contract as godev's ProcessStreamWriter.WriterEpoch — and `a8 logs` already
   * reads it for gap detection.
   */
  private val writerEpoch: Long = System.nanoTime()
  private val sequence = new AtomicLong(0L)

  /** Records the spool had to drop; rides the next shipped record. */
  private val dropped = new AtomicLong(0L)

  /** The spool: (record, event time). Guarded by `lock`. */
  private val spool = mutable.ArrayBuffer.empty[(LogRecord, Instant)]
  private val lock = new Object

  private val stopped = new java.util.concurrent.atomic.AtomicBoolean(false)

  private val shipper =
    Executors.newSingleThreadScheduledExecutor { r =>
      val t = new Thread(r, s"applog-${processUid.take(8)}")
      t.setDaemon(true)
      t
    }

  override def start(): Unit = {
    super.start()
    shipper.scheduleAtFixedRate(
      () =>
        try ship()
        catch { case e: Throwable => () }, // never let the shipper thread die
      DefaultFlushIntervalMillis,
      DefaultFlushIntervalMillis,
      TimeUnit.MILLISECONDS,
    )
  }

  /**
   * The logging path. Called on the caller's thread, from anywhere, and MUST NOT
   * block on the network — it only appends to the in-memory spool.
   */
  override def append(event: ILoggingEvent): Unit = {
    if (stopped.get()) return

    val rec =
      LogRecord(
        level = levelOf(event.getLevel),
        // The JVM's answer to godev's `file.go:42`. Same question, different
        // vocabulary — one field, each language's own idiom.
        caller = Option(event.getLoggerName).getOrElse(""),
        // goid stays 0: it is Go's. The JVM's concurrent-context identity is the
        // thread NAME, which has its own field precisely so neither language has to
        // fake or stringify the other's.
        thread = Option(event.getThreadName).getOrElse(""),
        message = Option(event.getFormattedMessage).getOrElse(""),
        // The MDC is exactly what the k/v bag is for.
        extraData = Option(event.getMDCPropertyMap).map(_.asScala.toMap).getOrElse(Map.empty),
        // The exception, kept STRUCTURED with its chain intact instead of flattened
        // into the message — the highest-value field on the record, and the one the
        // JVM previously had nowhere to put.
        error = Option(event.getThrowableProxy).flatMap(errorOf),
      )

    lock.synchronized {
      // Overflow: drop the OLDEST and count it. Blocking the writer instead would
      // reintroduce exactly the coupling this design exists to avoid.
      if (spool.size >= maxSpoolRecords) {
        val drop = spool.size - maxSpoolRecords + 1
        spool.remove(0, drop)
        dropped.addAndGet(drop.toLong)
      }
      spool += ((rec, event.getInstant))
    }
  }

  /**
   * Drain the spool into ONE StreamRecord and publish it. Batching the spooled
   * records into a single envelope is what makes the per-record cost a buffer append
   * rather than a NATS round trip.
   */
  private def ship(): Unit = {
    val batch =
      lock.synchronized {
        if (spool.isEmpty) Seq.empty
        else {
          val b = spool.toSeq
          spool.clear()
          b
        }
      }

    if (batch.isEmpty) return

    var pendingDrops = dropped.getAndSet(0L)

    val buffers =
      batch.map { case (rec, at) =>
        val withDrops =
          if (pendingDrops > 0) {
            // Report the loss on the first record that FOLLOWS it, so a reader sees the
            // gap in place. A truncated log that admits what it lost beats one that
            // quietly shows a hole.
            val r = rec.withDroppedRecords(pendingDrops)
            pendingDrops = 0
            r
          } else rec
        Buffer(
          // The record's EVENT time — when the log call happened, not when the batch
          // shipped. A spool that sat through a NATS outage must not stamp every record
          // with the moment the outage ended.
          timestamp = Some(Timestamp(at.getEpochSecond, at.getNano)),
          source = BufferSource.applog,
          record = Some(withDrops),
        )
      }

    publish(
      StreamRecord(
        timestamp = Some(nowTs()),
        sequence = sequence.incrementAndGet(),
        writerEpoch = writerEpoch,
        buffers = buffers,
      )
    )
  }

  /**
   * Drain, then write the terminal EOF record. The EOF carries `finalSequence`: a
   * reader holding a contiguous 1..finalSequence for this epoch has provably lost
   * nothing. Without it a dropped FINAL record is undetectable, because nothing
   * follows it to reveal the skip.
   *
   * So a stream that ends WITHOUT an EOF is a real signal: that process did not shut
   * down cleanly.
   *
   * Bounded: a wedged NATS must not hang the JVM's exit.
   */
  override def stop(): Unit = {
    if (stopped.getAndSet(true)) return
    super.stop()

    val flusher = Executors.newSingleThreadExecutor { r =>
      val t = new Thread(r, s"applog-flush-${processUid.take(8)}")
      t.setDaemon(true)
      t
    }
    val task: Runnable = () => {
      ship()
      val finalSeq = sequence.get()
      publish(
        StreamRecord(
          timestamp = Some(nowTs()),
          sequence = sequence.incrementAndGet(),
          writerEpoch = writerEpoch,
          eof = true,
          finalSequence = finalSeq,
        )
      )
    }

    val f = flusher.submit(task)
    try f.get(DefaultShutdownTimeoutMillis, TimeUnit.MILLISECONDS)
    catch {
      case _: Throwable =>
        // Try, give up, exit. The logs are worth less than a hung shutdown.
        ()
    } finally {
      flusher.shutdownNow()
      shipper.shutdownNow()
    }
  }

  /**
   * Publish must never recurse back into logging: this appender is ON the root
   * logger, so a `logger.warn` here would re-enter append() and, on a wedged NATS,
   * spin. Failures are therefore silent BY CONSTRUCTION — the spool bound and the
   * EOF marker are what make loss visible instead.
   */
  private def publish(sr: StreamRecord): Unit =
    try
      client.streamWrite(
        StreamWrite(processUid = processUid, channelName = Channel, record = Some(sr))
      )
    catch { case _: Throwable => () }

  private def nowTs(): Timestamp = {
    val now = Instant.now()
    Timestamp(now.getEpochSecond, now.getNano)
  }

}
