package a8.shared.jdbcf


import a8.shared.{AtomicMap, NamedToString}
import a8.shared.jdbcf.JdbcMetadata.{JdbcTable, ResolvedJdbcTable}
import a8.shared.jdbcf.{CatalogName, ColumnName, ResolvedTableName, SchemaName, TableLocator, TableName}
import a8.shared.SharedImports._
import a8.shared.jdbcf.UnsafeResultSetOps.asImplicit
import zio._

import java.sql.ResultSetMetaData

object JdbcMetadata {

  object JdbcPrimaryKey {
    def fromMetadataRow(row: Row): JdbcPrimaryKey =
      new JdbcPrimaryKey(
        ResolvedTableName.fromMetadataRow(row),
        row.get[ColumnName]("COLUMN_NAME"),
        row.get[Short]("KEY_SEQ"),
        row.opt[String]("PK_NAME")
      )
  }
  case class JdbcPrimaryKey(
    resolvedTableName: ResolvedTableName,
    columnName: ColumnName,
    keyIndex: Short,
    primaryKeyName: Option[String]
  )


  object JdbcColumn {
    def fromMetadataRow(row: Row): JdbcColumn =
      new JdbcColumn(
        ResolvedTableName.fromMetadataRow(row),
        row.get[ColumnName]("COLUMN_NAME"),
        row.get[Int]("DATA_TYPE"),
        row.get[String]("TYPE_NAME"),
        row.get[Int]("COLUMN_SIZE"),
        row.opt[Int]("DECIMAL_DIGITS"),
        row.get[Int]("NULLABLE"),
        row.opt[String]("REMARKS"),
        row.opt[String]("COLUMN_DEF"),
        row.get[Int]("ORDINAL_POSITION"),
        row.opt[Boolean]("IS_NULLABLE"),
        row.opt[Boolean]("IS_AUTOINCREMENT"),
      )
  }
  case class JdbcColumn(
    resolvedTableName: ResolvedTableName,
    columnName: ColumnName,
    dataType: Int,
    typeName: String,
    columnSize: Int,
    decimalDigits: Option[Int],
    nullable: Int,
    remarks: Option[String],
    defaultValue: Option[String],
    /**
    *  The index of this column in the table (counting from 1 because that is how sql does it)
    */
    indexInTable: Int,
    isNullable: Option[Boolean],
    isAutoIncrement: Option[Boolean],
    alternativeNames: Vector[ColumnName] = Vector(),
  ) extends NamedToString {
    def qualifiedName = resolvedTableName.qualifiedName + "." + columnName.asString
    lazy val columnNames = alternativeNames.prepended(columnName)
  }

  object JdbcTable {
    def apply(row: Row, origin: TableLocator): JdbcTable = {
      apply(row).copy(origin = Some(origin))
    }

    def apply(row: Row): JdbcTable =
      new JdbcTable(
        row.opt[CatalogName]("TABLE_CAT"),
        row.opt[SchemaName]("TABLE_SCHEM"),
        row.get[TableName]("TABLE_NAME"),
        row.opt[String]("TABLE_TYPE"),
        row.opt[String]("REMARKS"),
        row,
        None,
      )
  }
  case class JdbcTable(
      catalog: Option[CatalogName],
      schema: Option[SchemaName],
      tableName: TableName,
      tableType: Option[String],
      remarks: Option[String],
      rawRow: Row,
      origin: Option[TableLocator] = None,
  ) {
    def locator = TableLocator(catalog, schema, tableName)
  }

  case class ResolvedJdbcTable(
    resolvedTableName: ResolvedTableName,
    jdbcTable: JdbcTable,
    jdbcKeys: Vector[JdbcPrimaryKey],
    jdbcColumns: Vector[JdbcColumn],
//    searchSchema: Option[String]
  ) {

    lazy val columnsByName = columns.toMapTransform(_.name)

    lazy val columns: Seq[ResolvedColumn] = {
      val keysByColumnName = jdbcKeys.map(k => k.columnName -> k).iterator.toMap
      jdbcColumns.zipWithIndex.map { case (column,i) =>
        ResolvedColumn(
          column.columnName,
          column,
          keysByColumnName.get(column.columnName),
          i,
        )(this)
      }
    }

    lazy val keys = columns.filter(_.isPrimaryKey)

    def querySql(whereExpr: Option[SqlString]): SqlString = {
      import SqlString._
      val selectFields = jdbcColumns.map(liftJdbcColumn).mkSqlString(q", ")
      val whereClause = whereExpr.map(e => q" where ${e}")
      q"select ${selectFields} from ${resolvedTableName.qualifiedName.identifier}${whereClause}"
    }

  }

  case class ResolvedColumn(name: ColumnName, jdbcColumn: JdbcColumn, jdbcPrimaryKey: Option[JdbcPrimaryKey], ordinalPosition: Int /** from 0 */)(table: ResolvedJdbcTable) extends NamedToString {
    def isPrimaryKey = jdbcPrimaryKey.isDefined
    def isNullable =
      jdbcColumn
        .isNullable
        .getOrElse {
          jdbcColumn.nullable === ResultSetMetaData.columnNullable
        }
  }


  val default =

    new JdbcMetadata {

      val resolvedTableNameCache = AtomicMap[TableLocator, ResolvedTableName]
      val tableMetadataCache = AtomicMap[TableLocator, ResolvedJdbcTable]

      override def resolveTableName(tableLocator: TableLocator, conn: Conn, useCache: Boolean): Task[ResolvedTableName] = {
        resolvedTableNameCache
          .get(tableLocator)
          .filter(_ => useCache)
          .map(a => ZIO.succeed(a))
          .getOrElse {
            impl.resolveTableName(tableLocator, conn)
              .map { table =>
                resolvedTableNameCache.put(tableLocator, table)
                table
              }
          }
      }


      override def tables(conn: Conn): Task[Iterable[JdbcTable]] = {
        conn.asInternal.withInternalConn { jdbcConn =>
          resultSetToVector(
            jdbcConn
              .getMetaData
              .getTables(null, null, null, null)
          ).map(JdbcTable.apply)
        }
      }

      override def tableMetadata(tableLocator: TableLocator, conn: Conn, useCache: Boolean): Task[ResolvedJdbcTable] = {
        tableMetadataCache
          .get(tableLocator)
          .filter(_ => useCache)
          .map(a => ZIO.succeed(a))
          .getOrElse {
            impl.tableMeta(tableLocator, conn)
              .map { table =>
                tableMetadataCache.put(tableLocator, table)
                table
              }
          }
      }
    }

  object impl {

    def resolveTableName(tableLocator: TableLocator, conn: Conn): Task[ResolvedTableName] =
      conn.dialect.resolveTableName(tableLocator, conn)

    def tableMeta(tableLocator: TableLocator, conn: Conn): Task[ResolvedJdbcTable] = {
      import conn.dialect
      for {
        resolvedTableName <- dialect.resolveTableName(tableLocator, conn)
        jdbcKeys <- dialect.primaryKeys(resolvedTableName, conn)
        jdbcColumns <- dialect.columns(resolvedTableName, conn)
        result <-
          conn.asInternal.withInternalConn { jdbcConn =>

            val jdbcTable: JdbcTable =
              jdbcConn
                .getMetaData
                .getTables(
                  resolvedTableName.catalog.map(_.asString).orNull,
                  resolvedTableName.schema.map(_.asString).orNull,
                  resolvedTableName.name.asString,
                  null,
                )
                .runAsIterator { iter =>
                  iter
                    .map(row => JdbcTable(row, tableLocator))
                    .take(1)
                    .toList
                    .head
                }

            ResolvedJdbcTable(resolvedTableName, jdbcTable, jdbcKeys, jdbcColumns)
          }
      } yield result

    }

  }

}

trait JdbcMetadata {

  def resolveTableName(tableLocator: TableLocator, conn: Conn, useCache: Boolean): Task[ResolvedTableName]
  def tables(conn: Conn): Task[Iterable[JdbcTable]]
  def tableMetadata(tableLocator: TableLocator, conn: Conn, useCache: Boolean): Task[ResolvedJdbcTable]

}
