package a8.hermes.jdbcrpc

import a8.hermes.proto.db.db.{QueryColumnMetadata, QueryResponse}
import a8.shared.jdbcf.Row
import a8.shared.json.ast.JsDoc
import com.google.protobuf.struct.{ListValue, Struct, Value}
import com.google.protobuf.struct.Value.Kind

import java.time.LocalDateTime

/**
 * Standalone assertion harness for StructRowReader. The hermes module's "tests" are runnable mains
 * (no scalatest wired into its test config), so this follows that convention: a plain object whose
 * `main` exercises the REAL RowReader codecs (via Row.get[T]) and throws on mismatch.
 *
 * This is the top correctness target for the Mapper-over-RPC work: it proves the rich JVM types the
 * codecs expect are reconstructed from a protobuf Struct — especially timestamps (arrive as RFC3339
 * strings), int8 (arrive as lossy float64 OR lossless string), jsonb (json text), bool, and nulls.
 */
object StructRowReaderTest {

  private var failures = 0

  private def check(name: String)(body: => Boolean): Unit = {
    val ok =
      try body
      catch
        case e: Throwable =>
          println(s"  [ERROR] $name: ${e.getClass.getSimpleName}: ${e.getMessage}")
          false
    if ok then println(s"  [ok]    $name")
    else
      failures += 1
      println(s"  [FAIL]  $name")
  }

  private def strVal(s: String): Value = Value(Kind.StringValue(s))
  private def numVal(n: Double): Value = Value(Kind.NumberValue(n))
  private def boolVal(b: Boolean): Value = Value(Kind.BoolValue(b))
  private def nullVal: Value = Value(Kind.NullValue(com.google.protobuf.struct.NullValue.NULL_VALUE))

  private def col(name: String, sqlType: String): QueryColumnMetadata =
    QueryColumnMetadata(name = name, label = name, sqlType = sqlType, nullable = true)

  /** build a single-row QueryResponse from (columnName, sqlType, value) tuples */
  private def oneRow(cells: (String, String, Value)*): Row = {
    val columns = cells.map { case (n, t, _) => col(n, t) }
    val fields = cells.map { case (n, _, v) => n -> v }.toMap
    val resp = QueryResponse(
      rows = Seq(Struct(fields = fields)),
      rowCount = 1,
      columns = columns,
    )
    val rows = StructRowReader.toRows(resp)
    require(rows.size == 1, s"expected 1 row, got ${rows.size}")
    rows.head
  }

  def main(args: Array[String]): Unit = {
    println("StructRowReader assertions:")

    check("string column -> String") {
      val row = oneRow(("name", "varchar(255)", strVal("hello")))
      row.get[String]("name") == "hello"
    }

    check("timestamp (RFC3339 offset) -> LocalDateTime") {
      val row = oneRow(("startedat", "timestamp", strVal("2026-05-31T14:30:00Z")))
      val ldt = row.get[LocalDateTime]("startedat")
      // 14:30:00 UTC
      ldt.getYear == 2026 && ldt.getMonthValue == 5 && ldt.getDayOfMonth == 31 &&
        ldt.getHour == 14 && ldt.getMinute == 30
    }

    check("timestamp (no offset) -> LocalDateTime") {
      val row = oneRow(("startedat", "timestamp", strVal("2026-05-31T14:30:00")))
      val ldt = row.get[LocalDateTime]("startedat")
      ldt.getHour == 14 && ldt.getMinute == 30
    }

    check("int8 as lossless StringValue -> Long (exact past 2^53)") {
      val big = 9007199254740993L // 2^53 + 1, not representable as float64
      val row = oneRow(("version", "int8", strVal(big.toString)))
      row.get[Long]("version") == big
    }

    check("int8 as NumberValue -> Long (best effort)") {
      val row = oneRow(("version", "bigint", numVal(42.0)))
      row.get[Long]("version") == 42L
    }

    check("int4 NumberValue -> Int") {
      val row = oneRow(("linux_uid", "int4", numVal(1000.0)))
      row.get[Int]("linux_uid") == 1000
    }

    check("bool BoolValue -> Boolean") {
      val row = oneRow(("enabled", "bool", boolVal(true)))
      row.get[Boolean]("enabled")
    }

    check("jsonb as json-text StringValue -> JsDoc") {
      val row = oneRow(("extra_data", "jsonb", strVal("""{"a":1,"b":"x"}""")))
      val js = row.get[JsDoc]("extra_data")
      js.value.toString.contains("x")
    }

    check("jsonb as nested StructValue -> JsDoc") {
      val nested = Struct(fields = Map("a" -> numVal(1.0), "b" -> strVal("x")))
      val row = oneRow(("extra_data", "jsonb", Value(Kind.StructValue(nested))))
      val js = row.get[JsDoc]("extra_data")
      js.value.toString.contains("x")
    }

    check("null -> None sentinel (Option reads None)") {
      val row = oneRow(("category", "text", nullVal))
      row.opt[String]("category").isEmpty
    }

    check("absent field -> None sentinel") {
      // column declared but no struct field present
      val resp = QueryResponse(
        rows = Seq(Struct(fields = Map.empty)),
        rowCount = 1,
        columns = Seq(col("category", "text")),
      )
      StructRowReader.toRows(resp).head.opt[String]("category").isEmpty
    }

    check("multi-column row preserves order + names") {
      val row = oneRow(
        ("uid", "varchar(32)", strVal("abc")),
        ("startedat", "timestamp", strVal("2026-01-01T00:00:00Z")),
        ("category", "text", nullVal),
      )
      row.get[String]("uid") == "abc" &&
        row.get[LocalDateTime]("startedat").getYear == 2026 &&
        row.opt[String]("category").isEmpty
    }

    check("empty result set -> no rows") {
      val resp = QueryResponse(rows = Seq.empty, rowCount = 0, columns = Seq(col("uid", "varchar(32)")))
      StructRowReader.toRows(resp).isEmpty
    }

    println()
    if failures == 0 then println("ALL PASSED")
    else
      println(s"$failures FAILURE(S)")
      sys.exit(1)
  }

}
