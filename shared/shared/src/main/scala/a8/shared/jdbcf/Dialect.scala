package a8.shared.jdbcf

import a8.shared.Chord
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.JdbcMetadata.{JdbcColumn, JdbcPrimaryKey}
import a8.shared.jdbcf.SqlString.{DefaultEscaper, DefaultJdbcEscaper, Escaper, RawSqlString}
import sttp.model.Uri
import UnsafeResultSetOps._
import a8.shared.SharedImports._
import zio._
import java.sql.Connection

object Dialect {

  val duobleQuote: Chord = Chord.str('"'.toString)

  case object Default extends Dialect {

    override def isPostgres: Boolean = false

    /**
     * no default case for default dialect
     */
    override def isIdentifierDefaultCase(name: String): Boolean =
      false

  }

  def apply(jdbcUri: Uri): Dialect = {
    val schemaParts = jdbcUri.toString.split(":/").head.split(":").toList
    schemaParts match {
      case "jdbc" :: "postgresql" :: _ =>
        PostgresDialect
      case "jdbc" :: _ :: "postgresql" :: _ =>
        PostgresDialect
      case "jdbc" :: "mysql" :: _ =>
        MySqlDialect
      case "jdbc" :: _ :: "mysql" :: _ =>
        MySqlDialect
      case _ =>
        DialectPlatform(jdbcUri)
          .getOrElse(Default)
    }
  }

}

trait Dialect {

  val validationQuery: Option[SqlString] = None

  def escaper(jdbcConnR: Resource[Connection]): Resource[Escaper] =
    jdbcConnR
      .flatMap { conn =>
        for {
          keywordSet <- KeywordSet.fromMetadata(conn.getMetaData)
          escaper0 <-
            ZIO.attemptBlocking {
              val identifierQuoteString = conn.getMetaData.getIdentifierQuoteString
              new DefaultEscaper(identifierQuoteString, keywordSet, defaultCaseFn = isIdentifierDefaultCase) {}
            }
        } yield escaper0
      }

  def isIdentifierDefaultCase(name: String): Boolean

  def isPostgres: Boolean

  def sqlQuotedIdentifier(identifier: String): String =
    identifier

  val schemaSeparator: SqlString = RawSqlString(".")

  def apply(jdbcUri: Uri): Option[Dialect] =
    DialectPlatform(jdbcUri)

    /**
   * will do a case insensitive lookup
   */
  def resolveTableName(tableLocator: TableLocator, conn: Conn): Task[ResolvedTableName] = {
    for {
      resolveTableTask <-
        conn.asInternal.withInternalConn { jdbcConn =>
          jdbcConn
            .getMetaData
            .getTables(
              tableLocator.metadataCatalog,
              tableLocator.metadataSchema,
              tableLocator.metadataTable,
              null
            )
            .runAsIterator { iter =>
              val foundTables =
                iter
                  .map { row =>
                    ResolvedTableName(
                      row.opt[CatalogName]("TABLE_CAT"),
                      row.opt[SchemaName]("TABLE_SCHEM"),
                      row.get[TableName]("TABLE_NAME"),
                    )
                  }
                  .toVector

              resolveTableNameImpl(tableLocator, conn, foundTables)

            }
        }
      rtn <- resolveTableTask
    } yield rtn
  }

  def resolveTableNameImpl(tableLocator: TableLocator, conn: Conn, foundTables: Vector[ResolvedTableName]): Task[ResolvedTableName] =
    ZIO.succeed(
      foundTables.head
    )


  def primaryKeys(table: ResolvedTableName, conn: Conn): Task[Vector[JdbcPrimaryKey]] = {
    val locator = table.asLocator
    conn
      .asInternal
      .withInternalConn{ jdbcConn =>
        jdbcConn
          .getMetaData
          .getPrimaryKeys(
            locator.metadataCatalog,
            locator.metadataSchema,
            locator.metadataTable,
          )
          .runAsIterator(iter =>
            iter
              .map(JdbcPrimaryKey.fromMetadataRow)
              .toVector
          )
      }
  }

  def columns(table: ResolvedTableName, conn: Conn): Task[Vector[JdbcColumn]] = {
    conn
      .asInternal
      .withInternalConn { jdbcConn =>
        val columnsRs =
          jdbcConn
            .getMetaData
            .getColumns(
              table.catalog.map(_.asString).orNull,
              table.schema.map(_.asString).orNull,
              table.name.asString,
              null,
            )
        resultSetToVector(columnsRs).map(JdbcColumn.fromMetadataRow)
      }
  }

}
