package a8.sync


import a8.shared.jdbcf

import java.sql.{PreparedStatement, SQLException}
import a8.sync.ResolvedTable.{ColumnMapper, ResolvedField}
import a8.sync.dsl.{Mapping, ResolvedMapping, Table}
import a8.shared.jdbcf.JdbcMetadata.{JdbcColumn, JdbcPrimaryKey, JdbcTable}
import a8.shared.jdbcf.SqlString.HasSqlString
import a8.shared.jdbcf.{Dialect, Row, RowWriter, SchemaName, SqlString, TableLocator, TableName}
import a8.shared.json.ast.{JsNull, JsVal}
import org.typelevel.ci.CIString
import zio._
import Imports._

object impl {

  object queryService {

    def query(query: SqlString, conn: jdbcf.Conn): Task[DataSet] = {
      conn
        .query[Row](query)
        .select
        .map(c => DataSet(c.toVector))
    }

    def updates(updateQueries: Vector[SqlString], conn: jdbcf.Conn): Task[Vector[Int]] =
      updateQueries
        .map(uq => update(uq, conn))
        .sequence

    def update(updateSql: SqlString, conn: jdbcf.Conn): Task[Int] = {
      conn.update(updateSql)
    }

  }


  def resolveMapping(schema: SchemaName, mapping: Mapping, conn: jdbcf.Conn): Task[ResolvedMapping] = {
    mapping
      .tables
      .map(t => resolveTable(schema, t, conn))
      .sequence
      .map { resolvedTables =>
        ResolvedMapping(schema, resolvedTables, mapping)
      }
  }

  def resolveTable(schema: SchemaName, table: Table, conn: jdbcf.Conn): Task[ResolvedTable] = {
    val locator = TableLocator(schema, table.targetTable)
    for {
      resolvedJdbcTable <- conn.tableMetadata(locator)
    } yield {

      val jdbcColumnsByName =
        resolvedJdbcTable
          .jdbcColumns
          .flatMap(c => c.columnNames.map(_ -> c))
          .toMap

//      val jdbcKeysByName = jdbcKeys.map(k => CIString(k.columnName) -> k).toMap

      val resolvedFields =
        table
          .fields
          .zipWithIndex
          .map { case (field, i) =>
            val jdbcColumn = jdbcColumnsByName.getOrElse(field.toColumnName, sys.error(s"Column ${field.toColumnName.asString} not found in ${resolvedJdbcTable.resolvedTableName.qualifiedName}"))
//            val jdbcKey = jdbcKeysByName.get(CIString(field.toColumn))
            ResolvedField(
              field,
              jdbcColumn,
              i,
            )
          }
      ResolvedTable(
        schema,
        table,
        Chunk.fromArray(resolvedFields.toArray),
        conn.dialect,
      )
    }
  }

  object NormalizedDataSet {
    def apply(table: ResolvedTable, dataSet: DataSet): NormalizedDataSet = {
      val rows =
        dataSet
          .rows
          .map { row =>
            table.normalizedRow(row)
          }
      NormalizedDataSet(rows)
    }
  }

  case class NormalizedDataSet(rows: Vector[NormalizedRow]) {
    lazy val rowsByKey = rows.map(row => row.key -> row).toMap
  }

  type NormalizedTuple = Chunk[NormalizedValue]

  case class NormalizedRow(key: NormalizedKey, values: NormalizedTuple)

  case class NormalizedKey(values: NormalizedTuple)

  object NormalizedValue {
    case object Null extends NormalizedValue {
      override def asSqlFragment = SqlString.Null
      override def prepare(ps: PreparedStatement, parameterIndex: Int)(implicit dialect: Dialect): Unit = ps.setNull(parameterIndex, java.sql.Types.NULL)
      override lazy val asJsVal: JsVal = JsNull
    }

    case object Omit extends NormalizedValue {
      override def asSqlFragment: SqlString = sys.error("Omit.asSqlFragment not implemented")
      override def prepare(ps: PreparedStatement, parameterIndex: Int)(implicit dialect: Dialect): Unit = sys.error("Omit.prepare not implemented")
      override def asJsVal: JsVal = sys.error("Omit.asJsVal not implemented")
    }
  }

  trait NormalizedValue extends HasSqlString {
    def prepare(ps: java.sql.PreparedStatement, parameterIndex: Int)(implicit dialect: Dialect): Unit
    def asJsVal: JsVal
  }

  case class SqlValue(rawValue: String)

}
