package a8.sync


import a8.sync.ResolvedTable.ResolvedField
import a8.sync.dsl.{Field, Table, TruncateAction}
import a8.sync.impl.{NormalizedDataSet, NormalizedKey, NormalizedRow, NormalizedTuple, NormalizedValue, SqlValue, queryService}
import Imports._
import a8.shared.CompanionGen
import a8.shared.jdbcf.{Conn, Dialect, Row, SqlString}
import a8.sync.ResolvedTable.ColumnMapper.{DateMapper, NumberMapper, StringMapper, TimeMapper, TimestampMapper}
import a8.shared.jdbcf.{SchemaName, TypeName}
import a8.shared.jdbcf.JdbcMetadata.JdbcColumn
import a8.shared.jdbcf.SqlString.HasSqlString

import java.sql.{PreparedStatement => JPreparedStatement}
import a8.shared.json.DynamicJson
import a8.shared.json.ast._
import a8.sync.RowSync.ValidationMessage
import cats.data.Chain
import zio._

import scala.util.Try

object ResolvedTable {

  case class ResolvedField(
    field: Field,
    jdbcColumn: JdbcColumn,
    ordinal: Int,
  ) extends HasSqlString {
    lazy val defaultValue: Option[JsVal] =
      field.defaultValue match {
        case JsNothing =>
          None
        case v =>
          Some(v)
      }
    def keyOrdinal = field.keyOrdinal
    lazy val columnMapper: ColumnMapper = field.dataType(jdbcColumn)
    override val asSqlFragment: SqlString = jdbcColumn
  }

  object DataType {
    case object ColumnDefault extends DataType {
      def apply(jdbcColumn: JdbcColumn): ColumnMapper = {
        import java.sql.{ Types => SqlType }
        jdbcColumn.dataType match {
          case SqlType.CHAR =>
            StringMapper(jdbcColumn)
          case SqlType.NUMERIC | SqlType.DECIMAL | SqlType.BIGINT =>
            NumberMapper(jdbcColumn)
          case SqlType.DATE =>
            DateMapper(jdbcColumn)
          case SqlType.TIME =>
            TimeMapper(jdbcColumn)
          case SqlType.TIMESTAMP =>
            TimestampMapper(jdbcColumn)
          case dt =>
            sys.error("Unsupported column type: " + dt)
        }
      }
    }
  }

  trait DataType {
    def apply(jdbcColumn: JdbcColumn): ColumnMapper
  }

  object ColumnMapper {
    import a8.shared.jdbcf.SqlString._

    case class StringNormalValue(value: String) extends NormalizedValue {
      override lazy val asSqlFragment: SqlString = value.escape
      override def prepare(ps: JPreparedStatement, parameterIndex: Int)(implicit dialect: Dialect): Unit =
        ps.setString(parameterIndex, value)
      override lazy val asJsVal: JsVal = JsStr(value)
    }

    case class StringMapper(targetColumn: JdbcColumn) extends ColumnMapper("string", targetColumn) {

      type A = String

      override def normalize(value: String): NormalizedValue =
        StringNormalValue(value)

      override def fromJson(dj: DynamicJson): Either[String, String] =
        impl.fromJson(dj) {
          case JsStr(s) =>
            s.rtrim
          case JsNum(num) =>
            num.toString
        }

      override def fromDatabase(a: AnyRef): Either[String, String] =
        impl.fromDatabase(a) {
          case s: String =>
            s.rtrim
        }

    }

    case class TimeNormalValue(value: java.sql.Time) extends NormalizedValue {
      override lazy val asSqlFragment: SqlString = SqlString.escapedString(value.toString)
      override def prepare(ps: JPreparedStatement, parameterIndex: Int)(implicit dialect: Dialect): Unit =
        ps.setTime(parameterIndex, value)
      override lazy val asJsVal: JsVal = JsStr(value.toString)
    }

    case class TimeMapper(targetColumn: JdbcColumn) extends ColumnMapper("time", targetColumn) {

      type A = java.sql.Time

      override def normalize(value: java.sql.Time): NormalizedValue =
        TimeNormalValue(value)

      override def fromJson(dj: DynamicJson): Either[String, java.sql.Time] =
        dj.__.wrappedValue match {
          case JsStr(s) =>
            try {
              Right(java.sql.Time.valueOf(s))
            } catch {
              case IsNonFatal(th) =>
                Left(th.getMessage)
            }

          case _ =>
            Left(impl.fromJsonErrorMessage(dj))
        }

      override def fromDatabase(a: AnyRef): Either[String, java.sql.Time] =
        impl.fromDatabase(a) {
          case t: java.sql.Time =>
            t
        }

    }

    case class DateNormalValue(value: java.sql.Date) extends NormalizedValue {
      override lazy val asSqlFragment: SqlString = SqlString.escapedString(value.toString)
      override def prepare(ps: JPreparedStatement, parameterIndex: Int)(implicit dialect: Dialect): Unit =
        ps.setDate(parameterIndex, value)
      override lazy val asJsVal: JsVal = JsStr(value.toString)
    }

    case class DateMapper(targetColumn: JdbcColumn) extends ColumnMapper("date", targetColumn) {

      type A = java.sql.Date

      override def normalize(value: java.sql.Date): NormalizedValue =
        DateNormalValue(value)

      override def fromJson(dj: DynamicJson): Either[String, java.sql.Date] =
        dj.__.wrappedValue match {
          case JsStr(s) =>
            try {
              Right(java.sql.Date.valueOf(s))
            } catch {
              case IsNonFatal(th) =>
                Left(th.getMessage)
            }

          case _ =>
            Left(impl.fromJsonErrorMessage(dj))
        }

      override def fromDatabase(a: AnyRef): Either[String, java.sql.Date] =
        impl.fromDatabase(a) {
          case d: java.sql.Date =>
            d
        }

    }

    case class TimestampMapper(targetColumn: JdbcColumn) extends ColumnMapper("timestamp", targetColumn) {

      type A = java.sql.Timestamp

      override def normalize(value: java.sql.Timestamp): NormalizedValue =
        TimestampNormalValue(value)

      override def fromJson(dj: DynamicJson): Either[String, java.sql.Timestamp] =
        dj.__.wrappedValue match {
          case JsStr(s) =>
            try {
              Right(java.sql.Timestamp.valueOf(s))
            } catch {
              case IsNonFatal(th) =>
                Left(th.getMessage)
            }

          case _ =>
            Left(impl.fromJsonErrorMessage(dj))
        }

      override def fromDatabase(a: AnyRef): Either[String, java.sql.Timestamp] =
        impl.fromDatabase(a) {
          case ts: java.sql.Timestamp =>
            ts
        }

    }

    case class NumberNormalValue(value: BigDecimal) extends NormalizedValue {
      override lazy val asSqlFragment: SqlString = SqlString.number(value)
      override def prepare(ps: JPreparedStatement, parameterIndex: Int)(implicit dialect: Dialect): Unit =
        ps.setBigDecimal(parameterIndex, value.bigDecimal)
      override lazy val asJsVal: JsVal = JsNum(value)
    }

    case class NumberMapper(targetColumn: JdbcColumn) extends ColumnMapper("number", targetColumn) {

      type A = BigDecimal

      override def normalize(value: BigDecimal): NormalizedValue =
        NumberNormalValue(value)

      override def fromJson(dj: DynamicJson): Either[String, BigDecimal] =
        dj.__.wrappedValue match {
          case JsNum(num) =>
            Right(num)
          case JsStr(s) =>
            Try(BigDecimal(s))
              .toEither
              .left
              .map(_ => impl.fromJsonErrorMessage(dj))
          case _ =>
            Left(impl.fromJsonErrorMessage(dj))
        }

      override def fromDatabase(a: AnyRef): Either[String, BigDecimal] =
        impl.fromDatabase(a) {
          case n: java.lang.Number =>
            BigDecimal(n.toString)
        }

    }

    case class BooleanNormalValue(value: java.lang.Boolean) extends NormalizedValue {
      override def asSqlFragment: SqlString = SqlString.boolean(value)
      override def prepare(ps: JPreparedStatement, parameterIndex: Int)(implicit dialect: Dialect): Unit =
        ps.setBoolean(parameterIndex, value)
      override lazy val asJsVal: JsVal = JsBool(value)
    }

    case class JsonNormalValue(value: String, jsonType: TypeName) extends NormalizedValue {
      override lazy val asSqlFragment: SqlString = q"${value.escape}::${jsonType}"

      override def prepare(ps: JPreparedStatement, parameterIndex: Int)(implicit dialect: Dialect): Unit = {
        import org.postgresql.util.PGobject
        val jsonObject = new PGobject
        jsonObject.setType(jsonType.name)
        jsonObject.setValue(value)
        ps.setObject(parameterIndex, jsonObject)
      }

      override lazy val asJsVal: JsVal = json.unsafeParse(value)
    }


    case class TimestampNormalValue(value: java.sql.Timestamp) extends NormalizedValue {

      override def asSqlFragment: SqlString = SqlString.timestamp(value)

      override def prepare(ps: JPreparedStatement, parameterIndex: Int)(implicit dialect: Dialect): Unit =
        ps.setTimestamp(parameterIndex, value)

      override lazy val asJsVal: JsVal = JsStr(value.toString)
    }

  }


  abstract class ColumnMapper(typeName: String, targetColumn: JdbcColumn) {

    type A

//    def selectExpr: SqlValue

    def normalize(value: A): NormalizedValue

    def normalizeFromJson(dj: DynamicJson): Either[String, NormalizedValue] =
      try {
        fromJson(dj)
          .map(normalize)
      } catch {
        case e: Exception =>
          Left(s"error reading path ${dj.__.path} in json -- " + e.getMessage)
      }

    def normalizeFromDatabase(a: AnyRef): Either[String, NormalizedValue] = {
      try {
        fromDatabase(a)
          .map(normalize)
      } catch {
        case e: Exception =>
          Left(s"error reading value in column ${targetColumn.qualifiedName} -- " + e.getMessage)
      }
    }

    def fromJson(dj: DynamicJson): Either[String, A]
    def fromDatabase(a: AnyRef): Either[String, A]

    def toSqlValue(jv: JsVal): SqlValue =
      jv match {
        case JsStr(s) =>
          SqlValue(s"""'${s.replaceAll("'", "''")}'""")
        case n: JsNum =>
          SqlValue(n.value.toString)
        case jsb: JsBool =>
          SqlValue(if (jsb.value) "1" else "0")
        case JsNull =>
          SqlValue("null")
        case _ =>
          sys.error(s"don't know how to marshal into an sql value -- ${jv}")
      }

    object impl {

      def fromJsonErrorMessage(dj: DynamicJson): String =
        s"unable to resolve ${dj.__.wrappedValue} @ ${dj.__.path} to a ${typeName}"

      def fromJson(dj: DynamicJson)(pf: PartialFunction[JsVal, A]): Either[String, A] = {
        val jv = dj.__.wrappedValue
        pf
          .lift(jv)
          .map(Right.apply)
          .getOrElse(Left(fromJsonErrorMessage(dj)))
      }

      def fromDatabase(a: AnyRef)(pf: PartialFunction[AnyRef, A]): Either[String, A] = {
        pf
          .lift(a)
          .map(Right.apply)
          .getOrElse(Left(s"unable to resolve ${a} @ ${targetColumn.qualifiedName} to a ${typeName}"))
      }

    }

  }


}

case class ResolvedTable(
  schema: SchemaName,
  table: Table,
  resolvedFields: Chunk[ResolvedField],
  dialect: Dialect,
) { resolvedTable =>

  implicit def implicitDialect: Dialect = dialect

  def qualifiedTargetTable: SqlString = {
    import a8.shared.jdbcf.SqlString._
    sql"${schema}${dialect.schemaSeparator}${table.targetTable}"
  }

//  def targetTable = table.targetTable

  def targetQuery(rootDocument: DynamicJson): SqlString = {
    import a8.shared.jdbcf.SqlString._
    val selectFields =
      resolvedFields
        .iterator
        .map(_.asSqlFragment)
        .mkSqlString(q", ")
    q"select ${selectFields} from ${qualifiedTargetTable} where ${table.syncWhereClause(rootDocument)}"
  }

  lazy val keyFieldsByIndex: Chunk[ResolvedField] =
    Chunk.fromArray(
      resolvedFields
        .iterator
        .flatMap(f => f.keyOrdinal.map(_ -> f))
        .toList
        .sortBy(_._1)
        .map(_._2)
        .toArray
    )

  def normalizedRow(row: Row): NormalizedRow = {
    val rowTuple =
      resolvedFields
        .map { field =>
          field.columnMapper.normalizeFromDatabase(row.rawValueByIndex(field.ordinal)) match {
            case Left(error) =>
              sys.error(error)
            case Right(v) =>
              v
          }
        }
    normalizedRow(rowTuple)
  }

  def normalizedRow(rowTuple: NormalizedTuple): NormalizedRow = {
    assert(rowTuple.size == resolvedFields.size)
    NormalizedRow(normalizedKey(rowTuple), rowTuple)
  }

  private def normalizedKey(rowTuple: NormalizedTuple): NormalizedKey =
    NormalizedKey(
      keyFieldsByIndex
        .map { keyField =>
          rowTuple(keyField.ordinal)
        }
    )

  def runSync(rootDocument: DynamicJson, conn: Conn, defaultTruncation: TruncateAction): Task[Chain[RowSync]] = {
    targetDataSet(rootDocument, conn)
      .map { targetDs =>
        val sourceDs = sourceDataSet(rootDocument)
        sync(sourceDs, targetDs, defaultTruncation)
      }
  }

  def sync(source: NormalizedDataSet, target: NormalizedDataSet, defaultTruncation: TruncateAction): Chain[RowSync] = {

    val insertsUpdates: Iterable[RowSync] =
      source
        .rowsByKey
        .map { case (sKey,originalSourceRow) =>
          val (validationMessages, truncatedRow) = RowSync.impl.validateRow(originalSourceRow, defaultTruncation, this)
          target.rowsByKey.get(sKey) match {
            case None =>
              RowSync.Insert(truncatedRow, validationMessages, originalSourceRow)(resolvedTable)
            case Some(tRow) =>
              RowSync.Update(truncatedRow, tRow, validationMessages, originalSourceRow)(resolvedTable)
          }
        }

    val deletes: Iterable[RowSync] =
      target
        .rowsByKey
        .flatMap { case (tKey, tRow) =>
          source.rowsByKey.get(tKey) match {
            case None =>
              Some(RowSync.Delete(tRow, Chain.empty)(resolvedTable))
            case Some(_) =>
              None
          }
        }

    Chain.fromSeq(insertsUpdates.toSeq) ++ Chain.fromSeq(deletes.toSeq)

  }

  def sourceDataSet(rootDocument: DynamicJson): NormalizedDataSet = {
    val table = resolvedTable.table
    val rows =
      table
        .sourceRows
        .getter(rootDocument)
        .__.asArray(coerceJsObj = true)
        .filter(table.sourceRowsFilter)
        .map { jobjectRow =>
          val rowTuple =
            resolvedFields
              .map { resolvedField =>
                if (resolvedField.field.omitOnInsertUpdate(jobjectRow)) {
                  NormalizedValue.Omit
                } else {
                  val value: JsVal =
                    (resolvedField.field.from.getter(jobjectRow).__.asJsVal, resolvedField.field.defaultValue) match {
                      case (JsNothing, dv) =>
                        dv
                      case (JsNull, JsNothing) =>
                        JsNull
                      case (JsNull, dv) =>
                        dv
                      case (jv, _) =>
                        jv
                    }
                  val jv = {
                    resolvedField.columnMapper.normalizeFromJson(DynamicJson(value)) match {
                      case Left(error) =>
                        sys.error(s"${resolvedField.jdbcColumn.resolvedTableName.name.asString}.${resolvedField.jdbcColumn.columnName.asString}: ${error}")
                      case Right(v) =>
                        v
                    }
                  }
                  jv
                }
              }
          resolvedTable.normalizedRow(rowTuple)
        }
    NormalizedDataSet(rows)
  }

  def targetDataSet(rootDocument: DynamicJson, conn: Conn): Task[NormalizedDataSet] = {
    queryService
      .query(targetQuery(rootDocument), conn)
      .map(ds => NormalizedDataSet(resolvedTable, ds))
  }

}
