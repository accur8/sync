package a8.shared.jdbcf


import a8.shared.SharedImports.{OptionT, Sync}
import a8.shared.jdbcf.JdbcMetadata.JdbcPrimaryKey
import com.ibm.as400.access.AS400JDBCSQLSyntaxErrorException
import sttp.model.Uri
import wvlet.log.Logger
import SqlString._
import a8.shared.SharedImports._

object ISeriesDialect extends Dialect {

  override val validationQuery: Option[SqlString] =
    Some(sql"select 1 from sysibm.sysdummy1")

  val logger = Logger.of[ISeriesDialect.type]

  override def isIdentifierDefaultCase(name: String): Boolean =
    !name.exists(_.isLower)

  override def isPostgres: Boolean = false

  // Could derive separator from jdbc url "naming" parameter or call jdbc metadata getCatalogSeparator()
  override val schemaSeparator: SqlString = SqlString.operator("/")

  override def primaryKeys[F[_] : Sync](table: ResolvedTableName, conn: Conn[F]): F[Vector[JdbcMetadata.JdbcPrimaryKey]] = {

    val F = Sync[F]

    def attempt1: F[Option[Vector[JdbcPrimaryKey]]] = {
      super
        .primaryKeys[F](table, conn)
        .map {
          case v if v.isEmpty =>
            None
          case v =>
            Some(v)
        }
    }

    def attempt2: F[Option[Vector[JdbcPrimaryKey]]] = {
      val schemaClause = table.schema.map(s => sql" and DBKLIB = ${s.asString.escape}").getOrElse(sql"")
      val sql = sql"select DBKFLD from QADBKFLD where DBKFIL = ${table.name.asString.escape}${schemaClause} order by DBKPOS"
      conn
        .query[ColumnName](sql)
        .select
        .map { column_names =>
          if ( column_names.nonEmpty ) {
            Some(
              column_names
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
        }
        .handleErrorWith {
          case e: AS400JDBCSQLSyntaxErrorException if e.getMessage.contains("[SQL0551]") =>
            logger.debug(s"not authorized to QADBKFLD unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
            F.pure(None)
          case e: java.sql.SQLException if e.getMessage.contains("[SQL0551]") =>
            logger.debug(s"not authorized to QADBKFLD unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
            F.pure(None)
          case e: Exception =>
            logger.debug(s"error querying QADBKFLD unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
            F.pure(None)
          case th: Throwable =>
            F.raiseError(th)
        }
    }

    def attempt3: F[Option[Vector[JdbcPrimaryKey]]] = {
      val schemaClause = table.schema.map(s => sql" and index_schema = ${s.asString.escape}").getOrElse(sql"")
      val sql = sql"""select COLUMN_NAMES from qsys2${schemaSeparator}SYSPARTITIONINDEXES where table_name = ${table.name.asString.escape} and index_type = 'PHYSICAL'${schemaClause}"""
      conn
        .query[String](sql)
        .fetch
        .map {
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
        .handleErrorWith {
          case e: AS400JDBCSQLSyntaxErrorException if e.getMessage.contains("[SQL0551]") =>
            logger.debug(s"not authorized to qsys2${schemaSeparator}SYSPARTITIONINDEXES unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
            F.pure(None)
          case e: AS400JDBCSQLSyntaxErrorException if e.getMessage.contains("[SQL0204]") =>
            logger.debug(s"qsys2${schemaSeparator}SYSPARTITIONINDEXES not found -- unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
            F.pure(None)
          case e: java.sql.SQLException if e.getMessage.contains("[SQL0551]") =>
            logger.debug(s"not authorized to qsys2${schemaSeparator}SYSPARTITIONINDEXES unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
            F.pure(None)
          case e: java.sql.SQLException if e.getMessage.contains("[SQL0204]") =>
            logger.debug(s"qsys2${schemaSeparator}SYSPARTITIONINDEXES not found -- unable to get key fields for ${table} DDS defined tables -- ${e.getMessage}")
            F.pure(None)
          case th: Throwable =>
            F.raiseError(th)
        }
    }

    val result: OptionT[F, Vector[JdbcPrimaryKey]] = OptionT(attempt1) <+> OptionT(attempt2) <+> OptionT(attempt3)

    result
      .value
      .map {
        case None =>
          Vector.empty
        case Some(v) =>
          v
      }

  }


  /**
   * case insensitive lookup on the table
   *
   * Note for this dialect the catalog parm is ignored since it is implicit in the connection
   *
   */
  override def resolveTableName[F[_] : Sync](tableLocator: TableLocator, conn: Conn[F]): F[ResolvedTableName] = {
    import tableLocator._
    val F = Sync[F]
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
    conn
      .query[(TableName,SchemaName,TableName)](query)
      .select
      .flatMap { rows =>
        if ( rows.isEmpty ) {
          F.raiseError(new RuntimeException(s"unable to resolveTableName ${tableLocator} in ${conn.jdbcUrl}"))
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
          ).pure[F]
        }
      }
  }


  override def columns[F[_] : Sync](table: ResolvedTableName, conn: Conn[F]): F[Vector[JdbcMetadata.JdbcColumn]] = {
    def loadColumnNames(): F[Map[String, Option[String]]] = {
      val schemaClause = table.schema.map(s => sql" and TABLE_SCHEMA = ${s.asString.escape}").getOrElse(sql"")
      val sql = sql"select COLUMN_NAME, SYSTEM_COLUMN_NAME from QSYS2${schemaSeparator}SYSCOLUMNS where TABLE_NAME = ${table.name.asString.escape}${schemaClause}"
      conn
        .query[(String, Option[String])](sql)
        .select
        .map(_.map(t => t._1.trim -> t._2.map(_.trim)).toMap)
    }

    for {
      columns <- super.columns(table, conn)
      columnNames <- loadColumnNames()
    } yield {
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
        LibraryList(Vector.empty)
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
