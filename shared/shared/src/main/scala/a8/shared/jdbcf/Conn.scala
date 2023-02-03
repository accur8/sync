package a8.shared.jdbcf

import java.sql.{Connection => JdbcConnection, DriverManager => JdbcDriverManager, PreparedStatement => JdbcPreparedStatement, Statement => JStatement}
import a8.shared.SharedImports._
import a8.shared.app.{LoggerF, Logging, LoggingF}
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.ConnFactoryCompanion.MapperMaterializer
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import a8.shared.jdbcf.JdbcMetadata.{JdbcColumn, JdbcTable, ResolvedJdbcTable}
import a8.shared.jdbcf.PostgresDialect.self
import a8.shared.jdbcf.SqlString.{CompiledSql, Escaper}
import a8.shared.jdbcf.mapper.KeyedTableMapper.UpsertResult
import a8.shared.jdbcf.mapper.{KeyedTableMapper, TableMapper}
import sttp.model.Uri
import a8.shared.jdbcf.UnsafeResultSetOps._
import zio._
import zio.stream.UStream

object Conn extends Logging {

  object impl {

    def makeResource(connFn: =>java.sql.Connection, mapperCache: MapperMaterializer, jdbcUrl: Uri, dialect: Dialect, escaper: Escaper): ZIO[Scope,Throwable,Conn] = {
      val jdbcMetadata = JdbcMetadata.default
      Managed
        .resource(connFn)
        .map(jdbcConn => toConn(jdbcConn, jdbcMetadata, mapperCache, jdbcUrl, dialect, escaper))
    }

  }

  def fromNewConnection(
    url: Uri,
    user: String,
    password: String
  ): Resource[Conn] = ZIO.suspend {
    val config =
      DatabaseConfig(
        DatabaseId(url.toString),
        url = url,
        user = user,
        password = DatabaseConfig.Password(password),
      )
    ConnFactory
      .constructor
      .flatMap(_.connR)
      .provideSome(ZLayer.succeed(config))
  }

  def toConn(
    jdbcConn: JdbcConnection,
    jdbcMetadata: JdbcMetadata,
    mapperCache: MapperMaterializer,
    jdbcUrl: Uri,
    dialect: Dialect,
    escaper: Escaper,
  ): Conn = {
    ConnInternalImpl(jdbcMetadata, jdbcConn, mapperCache, jdbcUrl, dialect, escaper)
  }

  trait ConnInternal extends Conn {
    val jdbcMetadata: JdbcMetadata
    def compile(sql: SqlString): CompiledSql
    def withInternalConn[A](fn: JdbcConnection=>A): Task[A]
    def statement: ZIO[Scope, Throwable, JStatement]
    def prepare(sql: SqlString): ZIO[Scope, Throwable, JdbcPreparedStatement]

    def withStatement[A](fn: JStatement=>Task[A]): Task[A]

    override def resolveTableName(tableLocator: TableLocator, useCache: Boolean): Task[ResolvedTableName] =
      jdbcMetadata.resolveTableName(tableLocator, this, useCache)

    def tables: Task[Iterable[JdbcTable]] =
      jdbcMetadata.tables(this)

    def tableMetadata(tableLocator: TableLocator, useCache: Boolean): Task[ResolvedJdbcTable] =
      jdbcMetadata.tableMetadata(tableLocator, this, useCache)

    def materializedMapper[A,PK](implicit keyedTableMapper: KeyedTableMapper[A,PK]): Task[KeyedTableMapper.Materialized[A,PK]]

  }

}

trait Conn {

  val jdbcUrl: Uri

  def resolveTableName(tableLocator: TableLocator, useCache: Boolean = true): Task[ResolvedTableName]
  def tables: Task[Iterable[JdbcTable]]
  def tableMetadata(tableLocator: TableLocator, useCache: Boolean = true): Task[ResolvedJdbcTable]
  def query[A : RowReader](sql: SqlString): Query[A]
  def streamingQuery[A : RowReader](sql: SqlString): StreamingQuery[A]
  def update(updateQuery: SqlString): Task[Int]
  def batcher[A : RowWriter](sql: SqlString): Batcher[A]
  def isAutoCommit: Task[Boolean]

  def insertRow[A : TableMapper](row: A): Task[A]
  def upsertRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A,B]): Task[(A,UpsertResult)]
  def updateRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A,B]): Task[A]
  def updateRowWhere[A, B](row: A)(where: SqlString)(implicit keyedMapper: KeyedTableMapper[A,B]): Task[Option[A]]
  def deleteRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A,B]): Task[A]

  def selectRows[A : TableMapper](whereClause: SqlString): Task[Iterable[A]]
  def streamingSelectRows[A : TableMapper: Tag](whereClause: SqlString): XStream[A]

  def selectOne[A : TableMapper](whereClause: SqlString): Task[A]
  def selectOpt[A : TableMapper](whereClause: SqlString): Task[Option[A]]

  def fetchRow[A, B](key: B)(implicit keyedMapper: KeyedTableMapper[A,B]): Task[A]
  def fetchRowOpt[A, B](key: B)(implicit keyedMapper: KeyedTableMapper[A,B]): Task[Option[A]]

  def asInternal: ConnInternal

  def commit: Task[Unit]
  def rollback: Task[Unit]

  implicit val escaper: Escaper
  implicit val dialect: Dialect

}
