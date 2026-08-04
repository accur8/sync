package a8.shared.jdbcf


import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

/**
 * postgres `interval` (a DURATION -- "how long") <-> `FiniteDuration`.
 *
 * jdbcf had no duration mapping at all, which is why the continuum zoo model
 * could not be regenerated after godev moved its purge/close timeouts and llm
 * latency off the old `*_millis` bigints and onto real `interval` columns
 * (BUG-20260718). This is the Scala half of the same contract godev's
 * `zoo/query/duration.go` implements on the Go side, and it deliberately keeps
 * the same conventions:
 *
 *  - writes go out as an explicit "<n> microseconds" literal. Microseconds are
 *    the interval type's native resolution, and that input form is unambiguous
 *    under every IntervalStyle.
 *  - calendar units (mon/year) are rejected rather than guessed at, because a
 *    month has no fixed length. Nothing of ours writes them; a non-zero one
 *    means a hand-written row. A ZERO one is accepted -- pgjdbc's own canonical
 *    rendering always spells out "0 years 0 mons", so rejecting on the unit
 *    alone would reject every value we read back.
 *
 * Reads are text, not a structured type, on purpose: `unsafe.scrubResultSetValue`
 * flattens every PGobject (PGInterval included) to its String value before a
 * RowReader ever sees it, so both forms below have to parse --
 *   "48:00:00", "1 day 02:00:00"                    postgres's own output
 *   "0 years 0 mons 2 days 0 hours 0 mins 0.0 secs" PGInterval.getValue
 */
object PgInterval {

  private val Micro = 1000L
  private val Milli = 1000L * Micro
  private val Second = 1000L * Milli
  private val Minute = 60L * Second
  private val Hour = 60L * Minute
  private val Day = 24L * Hour
  private val Week = 7L * Day

  /** The literal we hand postgres. See the "<n> microseconds" note above. */
  def format(duration: FiniteDuration): String =
    s"${duration.toMicros} microseconds"

  /**
   * The bound-parameter form. A literal cannot be used here: `setString` binds
   * with a varchar oid and postgres will not coerce that to interval, so the
   * parameter has to carry the type itself. Calendar fields stay zero -- hours
   * and seconds hold the whole value, which is what keeps a read back exact and
   * free of the mon/year units `parse` refuses.
   */
  def toPgInterval(duration: FiniteDuration): org.postgresql.util.PGInterval = {
    val micros = duration.toMicros
    val hours = micros / (Hour / Micro)
    val seconds = (micros % (Hour / Micro)) / 1000000d
    new org.postgresql.util.PGInterval(0, 0, 0, hours.toInt, 0, seconds)
  }

  def parse(value: String): FiniteDuration = {
    // the verbose IntervalStyle prefixes with '@'; it carries no information
    val trimmed = value.trim.stripPrefix("@").trim
    if (trimmed.isEmpty) {
      FiniteDuration(0L, TimeUnit.NANOSECONDS)
    } else {
      val fields = trimmed.split("\\s+").toVector
      var nanos = 0L
      var i = 0
      while (i < fields.size) {
        val field = fields(i)
        if (field.contains(":")) {
          // the clock part, which postgres always emits last
          if (i != fields.size - 1)
            sys.error(s"unparseable interval '${value}' (clock part is not last)")
          nanos += parseClock(value, field)
          i = fields.size
        } else {
          if (i + 1 >= fields.size)
            sys.error(s"unparseable interval '${value}' (number '${field}' with no unit)")
          val quantity =
            field.toDoubleOption
              .getOrElse(sys.error(s"unparseable interval '${value}' (bad number '${field}')"))
          nanos += (quantity * unitNanos(value, fields(i + 1), quantity)).toLong
          i += 2
        }
      }
      FiniteDuration(nanos, TimeUnit.NANOSECONDS)
    }
  }

  private def parseClock(value: String, field0: String): Long = {
    val (negative, field) =
      if (field0.startsWith("-")) true -> field0.drop(1)
      else if (field0.startsWith("+")) false -> field0.drop(1)
      else false -> field0
    field.split(":", -1) match {
      case Array(h, m, s) =>
        val nanos =
          (h.toLongOption, m.toLongOption, s.toDoubleOption) match {
            case (Some(hours), Some(minutes), Some(seconds)) =>
              hours * Hour + minutes * Minute + (seconds * Second.toDouble).toLong
            case _ =>
              sys.error(s"unparseable interval '${value}' (clock part '${field0}' has a bad component)")
          }
        if (negative) -nanos else nanos
      case _ =>
        sys.error(s"unparseable interval '${value}' (clock part '${field0}' is not H:MM:SS)")
    }
  }

  private def unitNanos(value: String, unit: String, quantity: Double): Long = {
    val singular = unit.toLowerCase match {
      // singularize, but leave the two letter abbreviations (us, ms) alone
      case u if u.length > 2 => u.stripSuffix("s")
      case u => u
    }
    singular match {
      case "microsecond" | "usec" | "us" => Micro
      case "millisecond" | "msec" | "ms" => Milli
      case "second" | "sec" => Second
      case "minute" | "min" => Minute
      case "hour" | "hr" => Hour
      case "day" => Day
      case "week" => Week
      case "mon" | "month" | "year" | "decade" | "century" | "millennium" | "centurie" | "millennia" =>
        // zero is how pgjdbc spells "no calendar component"; only a real one is a problem
        if (quantity == 0d) 0L
        else sys.error(s"unparseable interval '${value}' (calendar unit '${unit}' has no fixed length)")
      case _ =>
        sys.error(s"unparseable interval '${value}' (unknown unit '${unit}')")
    }
  }

}
