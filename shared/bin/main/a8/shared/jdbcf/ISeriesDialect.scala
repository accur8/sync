package a8.shared.jdbcf


import a8.shared.jdbcf.JdbcMetadata.JdbcPrimaryKey
import com.ibm.as400.access.AS400JDBCSQLSyntaxErrorException
import sttp.model.Uri
import SqlString._
import a8.shared.SharedImports._
import cats.data.OptionT
import a8.common.logging.Logger

object ISeriesDialect extends Dialect {

  override val validationQuery: Option[SqlString] =
    Some(sql"select 1 from sysibm.sysdummy1")

  val logger: Logger = Logger.of[ISeriesDialect.type]

  override def isIdentifierDefaultCase(name: String): Boolean =
    !name.exists(_.isLower)

  override def isPostgres: Boolean = false

  // Could derive separator from jdbc url "naming" parameter or call jdbc metadata getCatalogSeparator()
  override val schemaSeparator: SqlString = SqlString.operator("/")

  override def primaryKeys(table: ResolvedTableName, conn: Conn): Vector[JdbcMetadata.JdbcPrimaryKey] = {

    def attempt1: Option[Vector[JdbcPrimaryKey]] = {
      super.primaryKeys(table, conn) match {
        case v if v.isEmpty =>
          None
        case v =>
          Some(v)
      }
    }

    def attempt2: Option[Vector[JdbcPrimaryKey]] = {
      val schemaClause = table.schema.map(s => sql" and DBKLIB = ${s.asString.escape}").getOrElse(sql"")
      val sql = sql"select DBKFLD from QADBKFLD where DBKFIL = ${table.name.asString.escape}${schemaClause} order by DBKPOS"
      val columnNames = conn.query[ColumnName](sql).select
      if ( columnNames.nonEmpty ) {
        Some(
          columnNames
            .zipWithIndex
            .map { case (name, i) =>
              JdbcPrimaryKey(
                table,
                name,
                i.toShort,
                None
              )
            }
            .toVector
        )
      } else {
        None
      }
//        }
//        .catchAll {
//          case e: AS400JDBCSQLSyntaxErrorException if e.getMessage.contains("[SQL0551]") =>
//            logger.debug(s"not authorized to QADBKFLD unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
//            zsucceed(None)
//          case e: java.sql.SQLException if e.getMessage.contains("[SQL0551]") =>
//            logger.debug(s"not authorized to QADBKFLD unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
//            zsucceed(None)
//          case e: Exception =>
//            logger.debug(s"error querying QADBKFLD unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
//            zsucceed(None)
//          case th: Throwable =>
//            zfail(th)
//        }
    }

    def attempt3: Option[Vector[JdbcPrimaryKey]] = {
      val schemaClause = table.schema.map(s => sql" and index_schema = ${s.asString.escape}").getOrElse(sql"")
      val sql = sql"""select COLUMN_NAMES from qsys2${schemaSeparator}SYSPARTITIONINDEXES where table_name = ${table.name.asString.escape} and index_type = 'PHYSICAL'${schemaClause}"""
      try {
        conn.query[String](sql).fetchOpt match {
          case Some(columnNamesStr) if columnNamesStr.isNotBlank =>
            Some(
              columnNamesStr
                .split(",")
                .toVector
                .map(_.trim)
                .zipWithIndex
                .map { case (name, i) =>
                  JdbcPrimaryKey(
                    table,
                    ColumnName(name),
                    i.toShort,
                    None
                  )
                }
            )
          case _ =>
            None
        }
      } catch {
        case e: AS400JDBCSQLSyntaxErrorException if e.getMessage.contains("[SQL0551]") =>
          logger.debug(s"not authorized to qsys2${schemaSeparator}SYSPARTITIONINDEXES unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
          None
        case e: AS400JDBCSQLSyntaxErrorException if e.getMessage.contains("[SQL0204]") =>
          logger.debug(s"qsys2${schemaSeparator}SYSPARTITIONINDEXES not found -- unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
          None
        case e: java.sql.SQLException if e.getMessage.contains("[SQL0551]") =>
          logger.debug(s"not authorized to qsys2${schemaSeparator}SYSPARTITIONINDEXES unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
          None
        case e: java.sql.SQLException if e.getMessage.contains("[SQL0204]") =>
          logger.debug(s"qsys2${schemaSeparator}SYSPARTITIONINDEXES not found -- unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
          None
      }
    }

    attempt1
      .orElse(attempt2)
      .orElse(attempt3)
      .getOrElse(Vector.empty[JdbcPrimaryKey])

  }


  /**
   * case insensitive lookup on the table
   *
   * Note for this dialect the catalog parm is ignored since it is implicit in the connection
   *
   */
  override def resolveTableName(tableLocator: TableLocator, conn: Conn): ResolvedTableName = {
    import tableLocator._
    val libraryList = extractLibraryListFromJdbcUrl(conn.jdbcUrl)
    val schemaPart =
      schemaName
        .map(s => sql" and lower(table_schema) = ${s.asLowerCaseStringValue}")
        .getOrElse {
          if ( libraryList.isEmpty ) sql""
          else sql" and table_schema in (${libraryList.values.map(_.asString.escape).mkSqlString(sql",")})"
        }
    // ??? we get the alternative name here but in fact we
    val query = sql"""select table_name, table_schema, system_table_name from QSYS2${schemaSeparator}SYSTABLES where (lower(table_name) = ${tableName.asLowerCaseStringValue} or lower(system_table_name) = ${tableName.asLowerCaseStringValue}) ${schemaPart} order by table_schema"""
    val rows =
      conn
        .query[(TableName,SchemaName,TableName)](query)
        .select

    if ( rows.isEmpty ) {
      throw new RuntimeException(s"unable to resolveTableName ${tableLocator} in ${conn.jdbcUrl}")
    } else {

      val row = rows.toList.minBy(r => libraryList.indexOf(r._2).getOrElse(Integer.MAX_VALUE))

      val alternativeNames =
        if ( row._3 != row._1 )
          Iterable(row._3)
        else
          Iterable.empty

      ResolvedTableName(
        None,
        Some(row._2),
        row._1,
      )
    }
  }


  override def columns(table: ResolvedTableName, conn: Conn): Vector[JdbcMetadata.JdbcColumn] = {
    def loadColumnNames(): Map[String, Option[String]] = {
      val schemaClause = table.schema.map(s => sql" and TABLE_SCHEMA = ${s.asString.escape}").getOrElse(sql"")
      val sql = sql"select COLUMN_NAME, SYSTEM_COLUMN_NAME from QSYS2${schemaSeparator}SYSCOLUMNS where TABLE_NAME = ${table.name.asString.escape}${schemaClause}"
      conn
        .query[(String, Option[String])](sql)
        .select
        .map(t => t._1.trim -> t._2.map(_.trim))
        .toMap
    }

    val columns = super.columns(table, conn)
    val columnNames = loadColumnNames()
    columns
      .map { column =>
        columnNames
          .getOrElse(column.columnName.asString, None)
          .map(ColumnName.apply)
          .filter(_ != column.columnName)
          .map(altName => column.copy(alternativeNames = Vector(altName)))
          .getOrElse(column)
      }
  }


  def extractLibraryListFromJdbcUrl(jdbcUrl: Uri): LibraryList = {
    val jdbcUrlStr = jdbcUrl.toString
    val prefix: String = ";libraries="
    jdbcUrlStr.indexOf(prefix) match {
      case i0 if i0 >=0 =>
        val i1 = i0 + prefix.length
        val libs =
          jdbcUrlStr.indexOf(";", i1) match {
            case i2 if i2 >= 0 =>
              jdbcUrlStr.substring(i1, i2)
            case _ =>
              jdbcUrlStr.substring(i1)
          }
        // libraries are always upper case
        LibraryList(
          libs.splitList(",").map(l=>SchemaName(l.toUpperCase)).toVector
        )
      case _ =>
        LibraryList(Vector.empty[SchemaName])
    }
  }

  case class LibraryList(values: Vector[SchemaName]) {
    lazy val valuesWithIndex = values.zipWithIndex
    def indexOf(schemaName: SchemaName): Option[Int] =
      valuesWithIndex
        .find(_._1 == schemaName)
        .map(_._2)
    def isEmpty = values.isEmpty
  }
}
