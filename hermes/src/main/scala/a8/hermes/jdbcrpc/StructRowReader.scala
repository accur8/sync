package a8.hermes.jdbcrpc

import a8.hermes.proto.db.db.{QueryColumnMetadata, QueryResponse}
import a8.shared.SharedImports.*
import a8.shared.jdbcf.Row
import a8.shared.zreplace.Chunk
import com.google.protobuf.struct.{Struct, Value}
import com.google.protobuf.struct.Value.Kind

/**
 * Converts a db-RPC `QueryResponse` (rows as `google.protobuf.Struct`) into the `Row` values that
 * sync's `RowReader` codecs expect.
 *
 * This is the RPC-backend mirror of `a8.shared.jdbcf.unsafe.resultSetToIterator`. Where the JDBC path
 * gets richly-typed values straight from the driver (`java.sql.Timestamp`, `java.lang.Long`, ...), the
 * RPC path receives a protobuf `Struct` whose value kinds are only string/number/bool/null/struct/list.
 * The codecs in `RowReader` match on concrete JVM classes, so this layer MUST reconstruct those classes
 * or every non-string column would fail to coerce.
 *
 * Encoding contract (godev `pkg/rpc/db/utils.go` `goValueToProto`) — the producer switches on the pgx Go
 * runtime type, NOT on the column sql_type:
 *   - time.Time      -> StringValue(RFC3339)
 *   - int64/int/int32 -> NumberValue(float64)   (LOSSY past 2^53 — see note below)
 *   - float32/64     -> NumberValue
 *   - pgtype.Numeric -> NumberValue if float-convertible else StringValue
 *   - bool           -> BoolValue
 *   - []byte / jsonb -> StringValue (raw text)
 *   - map            -> StructValue (nested)
 *   - string / other -> StringValue
 *
 * So coercion is driven primarily by the `Value.Kind` that actually arrived, using
 * `QueryColumnMetadata.sqlType` only to disambiguate (e.g. a StringValue in a timestamp column must
 * become a `java.sql.Timestamp`; a StringValue in a json column stays JSON text).
 *
 * Nulls become the `None` sentinel that `RowReader.optionReader` checks for (NOT java null), matching
 * `unsafe.resultSetToIterator`.
 *
 * NOTE on bigint precision: godev currently sends int64 as a float64 NumberValue, which loses precision
 * above 2^53. We handle both StringValue (lossless) and NumberValue (best-effort) for integral columns;
 * the lossless fix is a godev-side change to emit int64/numeric as StringValue.
 */
object StructRowReader {

  /** the `None` sentinel used for nulls, identical to `unsafe.noneAnyRef` */
  private val NoneSentinel: AnyRef = None

  def toRows(resp: QueryResponse): Vector[Row] = {
    val columns = resp.columns.toVector
    val metadata = Row.Metadata(columns.map(_.name))
    resp.rows.iterator.map(struct => structToRow(struct, columns, metadata)).toVector
  }

  private def structToRow(struct: Struct, columns: Vector[QueryColumnMetadata], metadata: Row.Metadata): Row = {
    val values: Array[AnyRef] =
      columns.iterator.map { col =>
        val protoValue = struct.fields.getOrElse(col.name, Value(Kind.NullValue(com.google.protobuf.struct.NullValue.NULL_VALUE)))
        coerce(protoValue, col.sqlType)
      }.toArray
    Row(Chunk.fromArray(values), metadata)
  }

  /**
   * Coerce a single protobuf `Value` into the JVM value the `RowReader` codecs expect, given the
   * column's pg sql_type. Returns the `None` sentinel for nulls.
   */
  private[jdbcrpc] def coerce(value: Value, sqlType: String): AnyRef =
    value.kind match {
      case Kind.NullValue(_) | Kind.Empty =>
        NoneSentinel

      case Kind.StringValue(s) =>
        coerceString(s, sqlType)

      case Kind.NumberValue(n) =>
        coerceNumber(n, sqlType)

      case Kind.BoolValue(b) =>
        java.lang.Boolean.valueOf(b)

      case Kind.StructValue(s) =>
        // nested json object arriving as a Struct -> render to JSON text so the JsDoc/JsVal codecs parse it
        structToJsonText(s)

      case Kind.ListValue(l) =>
        listToJsonText(l)
    }

  /** A StringValue can be a real string, a timestamp/date/time, an integral/numeric, or json text. */
  private def coerceString(s: String, sqlType: String): AnyRef =
    normalizedType(sqlType) match {
      case "timestamp" | "timestamptz" =>
        parseTimestamp(s)
      case "date" =>
        java.sql.Date.valueOf(java.time.LocalDate.parse(s))
      case "time" | "timetz" =>
        java.sql.Time.valueOf(java.time.LocalTime.parse(s))
      case "int8" | "bigint" | "int" | "int4" | "integer" | "int2" | "smallint" =>
        // integral column delivered as a string (lossless) -> java.lang.Long; Number codecs narrow it
        java.lang.Long.valueOf(s.trim)
      case "numeric" | "decimal" =>
        new java.math.BigDecimal(s.trim)
      case _ =>
        // text/varchar/uuid/enum/json/jsonb and everything else: leave as String.
        // json/jsonb text is exactly what jsdocReader (Option[String].map(json.unsafeParse)) wants.
        s
    }

  /**
   * A NumberValue (float64) arriving for an integral column. We round-trip through the smallest
   * representation that preserves the value where possible. For int8 this is best-effort (lossy past
   * 2^53); for int4/int2/float it is exact.
   */
  private def coerceNumber(n: Double, sqlType: String): AnyRef =
    normalizedType(sqlType) match {
      case "int8" | "bigint" =>
        java.lang.Long.valueOf(n.toLong)
      case "int" | "int4" | "integer" =>
        java.lang.Integer.valueOf(n.toInt)
      case "int2" | "smallint" =>
        java.lang.Short.valueOf(n.toShort)
      case "float4" | "real" =>
        java.lang.Float.valueOf(n.toFloat)
      case "float8" | "double" | "double precision" =>
        java.lang.Double.valueOf(n)
      case "numeric" | "decimal" =>
        java.math.BigDecimal.valueOf(n)
      case "bool" | "boolean" =>
        java.lang.Boolean.valueOf(n != 0.0)
      case _ =>
        // unknown numeric column: hand back a Double; the Number-based codecs narrow as needed
        java.lang.Double.valueOf(n)
    }

  /**
   * Parse the RFC3339 string godev emits for timestamps into a `java.sql.Timestamp`.
   *
   * continuum stores timestamps as UTC wall-clock values (see CommonFields / managed_fields), and the
   * codecs do `Timestamp.toLocalDateTime`, which reads back the Timestamp's wall-clock fields in the
   * JVM default zone. To keep the round-trip JVM-timezone-independent we normalize an offset timestamp
   * to its UTC wall-clock LocalDateTime and build the Timestamp from THAT (via `valueOf`), rather than
   * `Timestamp.from(instant)` which would re-localize on read.
   */
  private def parseTimestamp(s: String): java.sql.Timestamp = {
    val localDateTime =
      try
        java.time.OffsetDateTime
          .parse(s)
          .atZoneSameInstant(java.time.ZoneOffset.UTC)
          .toLocalDateTime
      catch
        case _: java.time.format.DateTimeParseException =>
          // no offset present -> already a plain wall-clock value
          java.time.LocalDateTime.parse(s)
    java.sql.Timestamp.valueOf(localDateTime)
  }

  /** lowercase + strip array markers / size modifiers so `varchar(255)` matches `varchar`, etc. */
  private def normalizedType(sqlType: String): String = {
    val lower = sqlType.toLowerCase.trim
    val noParen = lower.indexOf('(') match {
      case -1 => lower
      case i  => lower.substring(0, i).trim
    }
    noParen
  }

  // Render a protobuf Struct/List back to JSON text WITHOUT pulling in protobuf-java-util
  // (JsonFormat). godev normally sends jsonb as a plain StringValue, so this nested-value path is an
  // edge case; we still render it faithfully so the JsDoc/JsVal codecs can parse it.

  private def structToJsonText(struct: Struct): String =
    struct.fields.iterator
      .map { case (k, v) => s"${quote(k)}:${valueToJsonText(v)}" }
      .mkString("{", ",", "}")

  private def listToJsonText(list: com.google.protobuf.struct.ListValue): String =
    list.values.iterator.map(valueToJsonText).mkString("[", ",", "]")

  private def valueToJsonText(value: Value): String =
    value.kind match {
      case Kind.NullValue(_) | Kind.Empty => "null"
      case Kind.StringValue(s)            => quote(s)
      case Kind.NumberValue(n)            => if (n == n.toLong.toDouble) n.toLong.toString else n.toString
      case Kind.BoolValue(b)              => b.toString
      case Kind.StructValue(s)            => structToJsonText(s)
      case Kind.ListValue(l)              => listToJsonText(l)
    }

  private def quote(s: String): String = {
    val sb = new StringBuilder(s.length + 2)
    sb.append('"')
    s.foreach {
      case '"'  => sb.append("\\\"")
      case '\\' => sb.append("\\\\")
      case '\n' => sb.append("\\n")
      case '\r' => sb.append("\\r")
      case '\t' => sb.append("\\t")
      case c if c < 0x20 => sb.append("\\u%04x".format(c.toInt))
      case c    => sb.append(c)
    }
    sb.append('"')
    sb.toString
  }

}
