package a8.shared.jdbcf

import org.scalatest.funsuite.AnyFunSuite
import a8.shared.SharedImports.canEqual.given

import scala.concurrent.duration._

/**
 * Empirical probe: does a FiniteDuration actually survive a REAL postgres
 * interval column, through the same RowWriter/scrub/RowReader path production
 * uses? Skipped unless PG_PROBE_URL is set, exactly like godev's
 * zoo/query/duration_pg_probe_test.go -- this is the other half of that probe,
 * and it exists for the same reason: the Go side's first encode compiled, typed,
 * and silently wrote 00:00:00.
 *
 *   PG_PROBE_URL=jdbc:postgresql://localhost:5432/glen?user=glen \
 *     sbt 'shared/testOnly a8.shared.jdbcf.PgIntervalPgProbeTest'
 */
class PgIntervalPgProbeTest extends AnyFunSuite {

  private val urlOpt = sys.env.get("PG_PROBE_URL").filter(_.trim.nonEmpty)

  def withProbeTable[A](fn: java.sql.Connection => A): Option[A] =
    urlOpt.map { url =>
      val conn = java.sql.DriverManager.getConnection(url)
      try {
        val stmt = conn.createStatement()
        try stmt.execute("create temp table probe (d interval not null)")
        finally stmt.close()
        fn(conn)
      } finally conn.close()
    }

  def readBack(conn: java.sql.Connection): (AnyRef, FiniteDuration) = {
    val stmt = conn.createStatement()
    try {
      val row = unsafe.resultSetToIterator(stmt.executeQuery("select d from probe")).next()
      row.value(0) -> RowReader[FiniteDuration].read(row)
    } finally stmt.close()
  }

  test("a bound parameter round trips through a real interval column") {
    assume(urlOpt.isDefined, "PG_PROBE_URL not set")
    withProbeTable { conn =>
      val expected = 15.minutes
      val ps = conn.prepareStatement("insert into probe (d) values (?)")
      try {
        RowWriter[FiniteDuration].applyParameters(ps, expected, 1)
        ps.executeUpdate()
      } finally ps.close()
      val (raw, actual) = readBack(conn)
      info(s"driver + scrub hand back ${raw.getClass.getName}: ${raw}")
      assertResult(expected)(actual)
    }
  }

  test("the microseconds literal needs no cast") {
    // the claim SqlString.finiteDuration rests on: postgres coerces an
    // unadorned string literal to interval from context
    assume(urlOpt.isDefined, "PG_PROBE_URL not set")
    withProbeTable { conn =>
      val expected = 48.hours + 123456.micros
      val stmt = conn.createStatement()
      try stmt.execute(s"insert into probe (d) values ('${PgInterval.format(expected)}')")
      finally stmt.close()
      assertResult(expected)(readBack(conn)._2)
    }
  }

}
