package a8.shared.jdbcf

import a8.shared.Chord
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.JdbcMetadata.{JdbcColumn, JdbcPrimaryKey}
import a8.shared.jdbcf.SqlString.{AbstractEscaper, DefaultJdbcEscaper, Escaper, RawSqlString}
import cats.effect.{Resource, Sync}
import sttp.model.Uri
import UnsafeResultSetOps._
import cats.effect.kernel.Async
import a8.shared.SharedImports._

import java.sql.Connection

trait Dialect {

  val validationQuery: Option[SqlString] = None

  def escaper[F[_] : Async](jdbcConnR: Resource[F, Connection]): Resource[F, Escaper] = {
    Resource.eval[F, Escaper](
      jdbcConnR.use(conn =>
        for {
          keywordSet <- KeywordSet.fromMetadata[F](conn.getMetaData)
          escaper0 <-
            Async[F].blocking {
              val identifierQuoteString = conn.getMetaData.getIdentifierQuoteString
              new AbstractEscaper(identifierQuoteString, keywordSet, defaultCaseFn = isIdentifierDefaultCase) {}
            }
        } yield escaper0
      )
    )
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
  def resolveTableName[F[_] : Sync](tableLocator: TableLocator, conn: Conn[F]): F[ResolvedTableName] =
    conn.asInternal.withInternalConn { jdbcConn =>
      jdbcConn
        .getMetaData
        .getTables(
          tableLocator.metadataCatalog,
          tableLocator.metadataSchema,
          tableLocator.metadataTable,
          null
        )
        .runAsIterator( iter =>
          iter
            .take(1)
            .map { row =>
              ResolvedTableName(
                row.opt[CatalogName]("TABLE_CAT"),
                row.opt[SchemaName]("TABLE_SCHEM"),
                row.get[TableName]("TABLE_NAME"),
              )
            }
            .toList
            .head
        )
    }

  def primaryKeys[F[_] : Sync](table: ResolvedTableName, conn: Conn[F]): F[Vector[JdbcPrimaryKey]] = {
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

  def columns[F[_] : Sync](table: ResolvedTableName, conn: Conn[F]): F[Vector[JdbcColumn]] = {
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

object Dialect {

  val duobleQuote = Chord.str('"'.toString)

  case object Default extends Dialect {

    override def isPostgres: Boolean = false

    /**
     * no default case for default dialect
     */
    override def isIdentifierDefaultCase(name: String): Boolean =
      false

  }

  def apply[F[_]](jdbcUri: Uri): Dialect = {
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
