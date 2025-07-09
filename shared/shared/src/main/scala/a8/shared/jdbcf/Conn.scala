package a8.shared.jdbcf

import java.sql.{Connection as JdbcConnection, DriverManager as JdbcDriverManager, PreparedStatement as JdbcPreparedStatement, Statement as JStatement}
import a8.shared.SharedImports.*
import a8.common.logging.Logging
import a8.shared.app.Ctx
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.ConnFactoryCompanion.MapperMaterializer
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import a8.shared.jdbcf.JdbcMetadata.{JdbcColumn, JdbcTable, ResolvedJdbcTable}
import a8.shared.jdbcf.PostgresDialect.self
import a8.shared.jdbcf.SqlString.{CompiledSql, Escaper}
import a8.shared.jdbcf.mapper.KeyedTableMapper.UpsertResult
import a8.shared.jdbcf.mapper.{KeyedTableMapper, TableMapper}
import sttp.model.Uri
import a8.shared.jdbcf.UnsafeResultSetOps.*
import zio.*

object Conn extends Logging {

  object impl {

    def makeResource(connFn: Ctx ?=> java.sql.Connection, mapperCache: MapperMaterializer, jdbcUrl: Uri, dialect: Dialect, escaper: Escaper): Resource[Conn] = {
      val jdbcMetadata = JdbcMetadata.default
      Resource
        .acquireRelease {
          val jdbcConn = connFn
          toConn(jdbcConn, jdbcMetadata, mapperCache, jdbcUrl, dialect, escaper)
        } {
          _.asInternal.withInternalConn { conn =>
            tryLogDebug(s"closing connection to ${conn.getMetaData.getURL}") {
              if ( !conn.isClosed ) {
                conn.close()
              }
            }
          }
        }
    }

  }

  def newConnection(
    url: Uri,
    user: String,
    password: String
  )(using Ctx): Conn = {

    val config =
      DatabaseConfig(
        DatabaseId(url.toString),
        url = url,
        user = user,
        password = DatabaseConfig.Password(password),
        maxPoolSize = 1,
        minIdle = 1,
      )

    val factory = ConnFactory.constructor(config)

    factory.connR.unwrap

  }

  def toConn(
    jdbcConn: JdbcConnection,
    jdbcMetadata: JdbcMetadata,
    mapperCache: MapperMaterializer,
    jdbcUrl: Uri,
    dialect: Dialect,
    escaper: Escaper,
  )(using Ctx): Conn = {
    ConnInternalImpl(jdbcMetadata, jdbcConn, mapperCache, jdbcUrl, dialect, escaper)
  }

  trait ConnInternal extends Conn {
    val jdbcMetadata: JdbcMetadata
    def compile(sql: SqlString): CompiledSql
    def withInternalConn[A](fn: JdbcConnection=>A): A
    def statement: Resource[JStatement]
    def prepare(sql: SqlString): Resource[JdbcPreparedStatement]

    def withStatement[A](fn: JStatement=>A): A

    /**
     * provides a statement scoped to the current Ctx
     * @return
     */
    def unsafeStatement(): JStatement

    override def resolveTableName(tableLocator: TableLocator, useCache: Boolean): ResolvedTableName =
      jdbcMetadata.resolveTableName(tableLocator, this, useCache)

    def tables: Iterable[JdbcTable] =
      jdbcMetadata.tables(this)

    def tableMetadata(tableLocator: TableLocator, useCache: Boolean): ResolvedJdbcTable =
      jdbcMetadata.tableMetadata(tableLocator, this, useCache)

    def materializedMapper[A,PK](implicit keyedTableMapper: KeyedTableMapper[A,PK]): KeyedTableMapper.Materialized[A,PK]

  }

}

trait Conn {

  val jdbcUrl: Uri

  def ctx: Ctx
  def dialect: Dialect
  def escaper: Escaper

  /**
   * The returned value is lazy meaning that the sql isn't run til you call `select` or `fetch` or `fetchOpt` on the Query object
   */
  def query[A: RowReader](sql: SqlString): Query[A]

  /**
   * The returned value is lazy meaning that the sql isn't run til you actually run the stream in the returned StreamingQuery
   */
  def streamingQuery[A: RowReader](sql: SqlString): StreamingQuery[A]

  def compile(sql: SqlString): SqlString.CompiledSql
  def resolveTableName(tableLocator: TableLocator, useCache: Boolean = true): ResolvedTableName
  def tables: Iterable[JdbcTable]
  def tableMetadata(tableLocator: TableLocator, useCache: Boolean = true): ResolvedJdbcTable
  def update(updateQuery: SqlString): Int
  def batcher[A : RowWriter](sql: SqlString): Batcher[A]
  def isAutoCommit: Boolean

  def insertRow[A : TableMapper](row: A): A
  def upsertRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A,B]): (A,UpsertResult)
  def updateRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A,B]): A
  def updateRowWhere[A, B](row: A)(where: SqlString)(implicit keyedMapper: KeyedTableMapper[A,B]): Option[A]
  def deleteRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A,B]): A

  def selectRows[A : TableMapper](whereClause: SqlString): Iterable[A]
  def streamingSelectRows[A : TableMapper](whereClause: SqlString): XStream[A]

  def selectOne[A : TableMapper](whereClause: SqlString): A
  def selectOpt[A : TableMapper](whereClause: SqlString): Option[A]

  def fetchRow[A, B](key: B)(implicit keyedMapper: KeyedTableMapper[A,B]): A
  def fetchRowOpt[A, B](key: B)(implicit keyedMapper: KeyedTableMapper[A,B]): Option[A]

  def asInternal: ConnInternal

  def commit: Unit
  def rollback: Unit

  given Escaper = escaper
  given Dialect = dialect

}
