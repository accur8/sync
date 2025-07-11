package a8.shared

import a8.common.logging.Logger

import java.time.{Duration, Instant}
import java.util.concurrent.atomic.AtomicLong

class ProgressCounter(ctx: String, total: Long, reportEvery: Long = 1000)(using logger: Logger) {
  private val count: AtomicLong = new AtomicLong(0)
  private val start: Instant = Instant.now()

  def increment(): Unit = synchronized {
    val ct = count.incrementAndGet()
    if (ct % reportEvery == 0 || ct == total) {
      report(ct)
    }
  }

  def incrementBy(n: Long): Unit = synchronized {
    val before = count.get()
    val ct = count.addAndGet(n)
    val diff = ct - before
    if (diff >= reportEvery || ct >= total) {
      report(ct)
    }
  }

  private def report(ct: Long): Unit = {
    val now = Instant.now()
    val elapsed = Duration.between(start, now)
    val ctDouble = ct.toDouble
    val percent = (ctDouble / total * 100).formatted("%.2f")
    val rate = if (elapsed.getSeconds > 0) ctDouble / elapsed.getSeconds else 0.0
    val remaining = total - ct
    val etaSeconds = if (rate > 0) (remaining / rate).toInt else 0
    val eta = Duration.ofSeconds(etaSeconds)

    val wallClockEta = now.plus(eta)

    val msg = f"${ctx} progress: $ct%,d of $total%,d ($percent%%) | Elapsed: ${formatDuration(elapsed)} | ETA: ${formatDuration(eta)} | Wall Clock ETA: ${wallClockEta.toString} | Rate: $rate%.2f/sec"

    logger.debug(msg)

  }

  private def formatDuration(d: Duration): String = {
    val mins = d.toMinutes
    val secs = d.getSeconds % 60
    f"$mins%02d:$secs%02d"
  }
}
