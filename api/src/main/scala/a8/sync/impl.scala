package a8.sync


import a8.shared.jdbcf
import java.sql.{PreparedStatement, SQLException}

import a8.sync.ResolvedTable.{ColumnMapper, ResolvedField}
import a8.sync.dsl.{Mapping, ResolvedMapping, Table}
import a8.shared.jdbcf.JdbcMetadata.{JdbcColumn, JdbcPrimaryKey, JdbcTable}
import a8.shared.jdbcf.SqlString.HasSqlString
import a8.shared.jdbcf.{Dialect, Row, RowWriter, SchemaName, SqlString, TableLocator, TableName}
import a8.shared.json.ast.{JsNull, JsVal}
import fs2.Chunk
import cats._
import cats.effect._
import cats.implicits._
import org.typelevel.ci.CIString

object impl {

  object queryService {

    def query[F[_] : Monad](query: SqlString, conn: jdbcf.Conn[F]): F[DataSet] = {
      conn
        .query[Row](query)
        .select
        .map(c => DataSet(c.toVector))
    }

    def updates[F[_] : Monad](updateQueries: Vector[SqlString], conn: jdbcf.Conn[F]): F[Vector[Int]] =
      updateQueries.traverse(uq => update[F](uq, conn))

    def update[F[_]](updateSql: SqlString, conn: jdbcf.Conn[F]): F[Int] = {
      conn.update(updateSql)
    }

  }


  def resolveMapping[F[_] : Async](schema: SchemaName, mapping: Mapping, conn: jdbcf.Conn[F]): F[ResolvedMapping] = {
    mapping
      .tables
      .traverse(t => resolveTable(schema, t, conn))
      .map { resolvedTables =>
        ResolvedMapping(schema, resolvedTables, mapping)
      }
  }

  def resolveTable[F[_] : Async](schema: SchemaName, table: Table, conn: jdbcf.Conn[F]): F[ResolvedTable] = {
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
        Chunk.array(resolvedFields.toArray),
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
