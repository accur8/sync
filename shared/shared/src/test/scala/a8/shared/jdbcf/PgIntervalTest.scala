package a8.shared.jdbcf

import org.scalatest.funsuite.AnyFunSuite
import a8.shared.SharedImports.canEqual.given

import scala.concurrent.duration._

/**
 * Mirrors godev's zoo/query/duration_test.go -- the two halves of the same
 * interval contract have to agree on what they read and write. See PgInterval.
 */
class PgIntervalTest extends AnyFunSuite {

  test("parse -- postgres's own output form") {
    // what our own writes round trip to: micros stay in the time field, so
    // hours never spill into days
    assertResult(15.minutes)(PgInterval.parse("00:15:00"))
    assertResult(8.hours)(PgInterval.parse("08:00:00"))
    assertResult(24.hours)(PgInterval.parse("24:00:00"))
    assertResult(48.hours)(PgInterval.parse("48:00:00"))
    assertResult(123456.micros)(PgInterval.parse("00:00:00.123456"))
    assertResult(500.millis)(PgInterval.parse("00:00:00.5"))
    // hand written rows may carry day/week units
    assertResult(24.hours)(PgInterval.parse("1 day"))
    assertResult(26.hours)(PgInterval.parse("1 day 02:00:00"))
    assertResult(7.days)(PgInterval.parse("1 week"))
    assertResult(90.seconds)(PgInterval.parse("90 seconds"))
    assertResult(15.minutes)(PgInterval.parse("15 mins"))
    assertResult(250.millis)(PgInterval.parse("250 milliseconds"))
  }

  test("parse -- pgjdbc's PGInterval.getValue form") {
    // this is the form a RowReader actually sees, because scrubResultSetValue
    // flattens the driver's PGInterval to its String value first
    assertResult(15.minutes)(
      PgInterval.parse("0 years 0 mons 0 days 0 hours 15 mins 0.00 secs")
    )
    assertResult(48.hours + 90.seconds)(
      PgInterval.parse("0 years 0 mons 0 days 48 hours 1 mins 30.00 secs")
    )
    assertResult(2.days)(
      PgInterval.parse("0 years 0 mons 2 days 0 hours 0 mins 0.00 secs")
    )
  }

  test("parse -- signs, the verbose prefix, and empty") {
    assertResult(-15.minutes)(PgInterval.parse("-00:15:00"))
    assertResult(24.hours)(PgInterval.parse("@ 1 day"))
    assertResult(Duration.Zero)(PgInterval.parse(""))
  }

  test("parse rejects a non-zero calendar unit") {
    // a month has no fixed length; nothing of ours writes one
    for (input <- Seq("1 mon", "2 mons", "1 year", "1 year 2 mons 3 days")) {
      withClue(input) {
        assertThrows[RuntimeException](PgInterval.parse(input))
      }
    }
  }

  test("format round trips through parse") {
    for (
      expected <- Seq(
        Duration.Zero,
        15.minutes,
        8.hours,
        24.hours,
        48.hours,
        123456.micros,
      )
    )
      withClue(expected) {
        assertResult(expected)(PgInterval.parse(PgInterval.format(expected)))
      }
  }

  test("toPgInterval carries the whole value in hours and seconds") {
    // calendar fields must stay zero -- that is what makes a read back exact
    for (
      expected <- Seq(
        Duration.Zero,
        15.minutes,
        8.hours,
        48.hours,
        123456.micros,
        -15.minutes,
      )
    )
      withClue(expected) {
        val pgi = PgInterval.toPgInterval(expected)
        assertResult(0)(pgi.getYears)
        assertResult(0)(pgi.getMonths)
        assertResult(0)(pgi.getDays)
        assertResult(expected)(PgInterval.parse(pgi.getValue))
      }
  }

}
