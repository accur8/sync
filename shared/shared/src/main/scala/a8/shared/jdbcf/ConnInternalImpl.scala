package a8.shared.jdbcf


import java.sql.{Connection => JdbcConnection, DriverManager => JdbcDriverManager, PreparedStatement => JdbcPreparedStatement, SQLException => JdbcSQLException, Statement => JStatement}
import a8.shared.app.LoggingF
import a8.shared.jdbcf.JdbcMetadata.JdbcTable
import a8.shared.jdbcf.SqlString.{CompiledSql, Escaper}
import a8.shared.jdbcf.mapper.KeyedTableMapper.UpsertResult
import a8.shared.jdbcf.mapper.{KeyedTableMapper, TableMapper}
import sttp.model.Uri
import a8.shared.SharedImports._
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.ConnFactoryImpl.MapperMaterializer
import zio._
import zio.stream.UStream

case class ConnInternalImpl(
  jdbcMetadata: JdbcMetadata,
  jdbcConn: java.sql.Connection,
  mapperMaterializer: MapperMaterializer,
  jdbcUrl: Uri,
  dialect: Dialect,
  escaper: Escaper,
)
  extends LoggingF
  with ConnInternal
{

  jdbcConn.getMetaData.getIdentifierQuoteString

  override def asInternal: ConnInternal = this

  override def compile(sql: SqlString): CompiledSql =
    SqlString.unsafe.compile(sql, escaper)

  override def tables: Task[Iterable[JdbcTable]] = {
    ZIO.attemptBlocking {
      resultSetToVector(
        jdbcConn
          .getMetaData
          .getTables(null, null, null, null)
      )
        .map(JdbcTable.apply)
    }
  }


  override def selectOne[A: TableMapper](whereClause: SqlString): Task[A] =
    selectOpt[A](whereClause)
      .flatMap {
        case Some(a) =>
         ZIO.succeed(a)
        case None =>
          ZIO.fail(new RuntimeException(s"expected a single value and got none with whereClause = ${whereClause}"))
      }

  override def selectOpt[A: TableMapper](whereClause: SqlString): Task[Option[A]] =
    selectRows[A](whereClause)
      .flatMap {
        _.toList match {
          case Nil =>
           ZIO.succeed(None)
          case List(a) =>
           ZIO.succeed(Some(a))
          case l =>
            ZIO.fail(new RuntimeException(s"expected 0 or 1 value got ${l.size} with whereClase = ${whereClause} -- ${l}"))
        }
      }

  def scoped[A : Managed](thunk: =>A): ZIO[Scope, Throwable, A] =
    Managed.scoped[A](thunk)

  override def streamingQuery[A : RowReader](sql: SqlString): StreamingQuery[A] =
    StreamingQuery.create[A](this, sql)

  override def query[A: RowReader](sqlStr: SqlString): Query[ A] =
    Query.create[A](this, sqlStr)

  override def update(updateQuery: SqlString): Task[Int] =
    ZIO.scoped[Any](
      prepare(updateQuery)
        .flatMap { ps =>
          ZIO.attemptBlocking {
            val i = ps.executeUpdate()
            i
          }
        }
    )


  override def withStatement[A](fn: JStatement => Task[A]): Task[A] = {
    ZIO.scoped {
      statement
        .flatMap(fn)
    }
  }

  override def statement: Resource[JStatement] =
    scoped(jdbcConn.createStatement())

  override def prepare(sql: SqlString): Resource[JdbcPreparedStatement] = {
    val resolvedSql = compile(sql)
    withSqlCtx[JdbcPreparedStatement](
      resolvedSql
    )(
      jdbcConn.prepareStatement(resolvedSql.value)
    )
  }

  override def batcher[A : RowWriter](sql: SqlString): Batcher[A] =
    Batcher.create[A](this, sql)

  override def withInternalConn[A](fn: JdbcConnection => A): Task[A] = {
    ZIO.attemptBlocking(fn(jdbcConn))
  }

  override def isAutoCommit: Task[Boolean] = {
    ZIO.attempt(jdbcConn.getAutoCommit)
  }

  def runSingleRowUpdate(updateQuery: SqlString): Task[Unit] =
    runSingleRowUpdateOpt(updateQuery)
      .flatMap {
        case true =>
          ZIO.unit
        case false =>
          ZIO.fail(new RuntimeException(s"ran update and expected 1 row to be affected and 0 rows were affected -- ${updateQuery}"))
      }

  def runSingleRowUpdateOpt(updateQuery: SqlString): Task[Boolean] =
    update(updateQuery)
      .flatMap {
        case 1 =>
          ZIO.succeed(true)
        case 0 =>
          ZIO.succeed(false) 
        case i =>
          ZIO.fail(new RuntimeException(s"ran update and expected 1 row to be affected and ${i} rows were affected -- ${updateQuery}"))
      }

  override def insertRow[A: TableMapper](row: A): Task[A] = {
    val auditedRow = TableMapper[A].auditProvider.onInsert(row)
    for {
      mapper <- mapperMaterializer.materialize(implicitly[TableMapper[A]])
      row <-
        runSingleRowUpdate(mapper.insertSql(auditedRow))
          .as(auditedRow)
    } yield row
  }

  override def upsertRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A, B]): Task[(A,UpsertResult)] = {
    val mapper = implicitly[KeyedTableMapper[A,B]]
    fetchRowOpt(mapper.key(row))
      .flatMap {
        case Some(v) =>
          updateRow(row)
            .map(_ -> UpsertResult.Update)
        case None =>
          insertRow(row)
            .map(_ -> UpsertResult.Insert)
      }
  }

  override def updateRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A, B]): Task[A] = {
    val auditedRow = keyedMapper.auditProvider.onUpdate(row)
    for {
      mapper <- mapperMaterializer.materialize(implicitly[KeyedTableMapper[A, B]])
      row <-
        runSingleRowUpdate(mapper.updateSql(auditedRow))
          .as(auditedRow)
    } yield row
  }

  override def updateRowWhere[A, B](row: A)(where: SqlString)(implicit keyedMapper: KeyedTableMapper[A, B]): Task[Option[A]] = {
    val auditedRow = keyedMapper.auditProvider.onUpdate(row)
    for {
      mapper <- mapperMaterializer.materialize(implicitly[KeyedTableMapper[A, B]])
      rowUpdated <-
        runSingleRowUpdateOpt(mapper.updateSql(auditedRow, where.some))
    } yield rowUpdated.toOption(auditedRow)
  }

  override def deleteRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A, B]): Task[A] = {
    for {
      mapper <- mapperMaterializer.materialize(implicitly[KeyedTableMapper[A, B]])
      row <-
        runSingleRowUpdate(mapper.deleteSql(mapper.key(row)))
          .as(row)
    } yield row
  }

  override def selectRows[A: TableMapper](whereClause: SqlString): Task[Iterable[A]] = {
    for {
      tm <- mapperMaterializer.materialize(implicitly[TableMapper[A]])
      rows <-
        query[A](tm.selectSql(whereClause))
          .select
    } yield rows
  }

  override def streamingSelectRows[A: TableMapper](whereClause: SqlString): XStream[A] = {
    mapperMaterializer
      .materialize(implicitly[TableMapper[A]])
      .zstreamEval
      .flatMap(tableMapper =>
        streamingQuery[A](tableMapper.selectSql(whereClause))
          .run
      )
  }

  override def fetchRow[A, B](key: B)(implicit keyedMapper: KeyedTableMapper[A, B]): Task[A] =
    fetchRowOpt[A,B](key)
      .flatMap {
        case Some(row) =>
         ZIO.succeed(row)
        case None =>
          ZIO.fail(new RuntimeException(s"expected a record with key ${key} in ${implicitly[TableMapper[A]].tableName}"))
      }

  override def fetchRowOpt[A, B](key: B)(implicit keyedMapper: KeyedTableMapper[A, B]): Task[Option[A]] =
    selectOpt(keyedMapper.keyToWhereClause(key))

  override def commit: Task[Unit] =
    ZIO.attemptBlocking(jdbcConn.commit())

  override def rollback: Task[Unit] =
    ZIO.attemptBlocking(jdbcConn.rollback())

}
