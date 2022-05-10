package a8.shared.jdbcf

import java.sql.{Connection => JdbcConnection, DriverManager => JdbcDriverManager, PreparedStatement => JdbcPreparedStatement, SQLException => JdbcSQLException, Statement => JStatement}
import a8.shared.app.LoggingF
import a8.shared.jdbcf.Conn.impl.withSqlCtx0
import a8.shared.jdbcf.JdbcMetadata.JdbcTable
import a8.shared.jdbcf.SqlString.{CompiledSql, Escaper}
import a8.shared.jdbcf.mapper.KeyedTableMapper.UpsertResult
import a8.shared.jdbcf.mapper.{KeyedTableMapper, TableMapper}
import sttp.model.Uri
import a8.shared.SharedImports._
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.ConnFactoryImpl.MapperMaterializer

case class ConnInternalImpl[F[_] : Async](
  jdbcMetadata: JdbcMetadata[F],
  jdbcConn: java.sql.Connection,
  mapperMaterializer: MapperMaterializer[F],
  jdbcUrl: Uri,
  dialect: Dialect,
  escaper: Escaper,
)
  extends LoggingF[F]
  with ConnInternal[F]
{

  jdbcConn.getMetaData.getIdentifierQuoteString

  val F = Async[F]

  override def asInternal: ConnInternal[F] = this

  override def compile(sql: SqlString): CompiledSql =
    SqlString.unsafe.compile(sql, escaper)

  override def tables: F[Iterable[JdbcTable]] = {
    F.blocking {
      resultSetToVector(
        jdbcConn
          .getMetaData
          .getTables(null, null, null, null)
      )
        .map(JdbcTable.apply)
    }
  }


  override def selectOne[A: TableMapper](whereClause: SqlString): F[A] =
    selectOpt[A](whereClause)
      .flatMap {
        case Some(a) =>
          F.pure(a)
        case None =>
          F.raiseError(new RuntimeException(s"expected a single value and got none with whereClause = ${whereClause}"))
      }

  override def selectOpt[A: TableMapper](whereClause: SqlString): F[Option[A]] =
    selectRows[A](whereClause)
      .flatMap {
        _.toList match {
          case Nil =>
            F.pure(None)
          case List(a) =>
            F.pure(Some(a))
          case l =>
            F.raiseError(new RuntimeException(s"expected 0 or 1 value got ${l.size} with whereClase = ${whereClause} -- ${l}"))
        }
      }

  def managedStream[A : Managed](thunk: =>A): fs2.Stream[F,A] =
    Managed.stream[F, A](thunk)

  override def streamingQuery[A : RowReader](sql: SqlString): StreamingQuery[F,A] =
    StreamingQuery.create[F,A](this, sql)

  override def query[A: RowReader](sqlStr: SqlString): Query[F, A] =
    Query.create[F,A](this, sqlStr)

  override def update(updateQuery: SqlString): F[Int] = {
    prepare(updateQuery)
      .evalMap { ps =>
        F.blocking {
          val i = ps.executeUpdate()
          i
        }
      }
      .compile
      .lastOrError
  }


  override def withStatement[A](fn: JStatement => F[A]): F[A] =
    statement
      .evalMap(fn)
      .compile
      .lastOrError

  override def statement: fs2.Stream[F, JStatement] =
    managedStream(jdbcConn.createStatement())

  override def prepare(sql: SqlString): fs2.Stream[F, JdbcPreparedStatement] = {
    val resolvedSql = compile(sql)
    managedStream(withSqlCtx0[JdbcPreparedStatement](resolvedSql)(jdbcConn.prepareStatement(resolvedSql.value)))
  }

  override def batcher[A : RowWriter](sql: SqlString): Batcher[F,A] =
    Batcher.create[F,A](this, sql)

  override def withInternalConn[A](fn: JdbcConnection => A): F[A] = {
    F.blocking(fn(jdbcConn))
  }

  override def isAutoCommit: F[Boolean] =
    F.delay(jdbcConn.getAutoCommit)

  def runSingleRowUpdate(updateQuery: SqlString): F[Unit] =
    update(updateQuery)
      .flatMap {
        case 1 =>
          F.unit
        case i =>
          F.raiseError[Unit](new RuntimeException(s"ran update and expected 1 row to be affected and ${i} rows were affected -- ${updateQuery}"))
      }

  override def insertRow[A: TableMapper](row: A): F[A] = {
    for {
      mapper <- mapperMaterializer.materialize(implicitly[TableMapper[A]])
      row <-
        runSingleRowUpdate(mapper.insertSql(row))
          .as(row)
    } yield row
  }

  override def upsertRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A, B]): F[(A,UpsertResult)] = {
    val mapper = implicitly[KeyedTableMapper[A,B]]
    fetchRowOpt(mapper.key(row))
      .flatMap {
        case Some(v) =>
          updateRow(row)
            .as(row -> UpsertResult.Update)
        case None =>
          insertRow(row)
            .as(row -> UpsertResult.Insert)
      }
  }

  override def updateRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A, B]): F[A] = {
    for {
      mapper <- mapperMaterializer.materialize(implicitly[KeyedTableMapper[A, B]])
      row <-
        runSingleRowUpdate(mapper.updateSql(row))
          .as(row)
    } yield row
  }

  override def deleteRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A, B]): F[A] = {
    for {
      mapper <- mapperMaterializer.materialize(implicitly[KeyedTableMapper[A, B]])
      row <-
        runSingleRowUpdate(mapper.deleteSql(mapper.key(row)))
          .as(row)
    } yield row
  }

  override def selectRows[A: TableMapper](whereClause: SqlString): F[Iterable[A]] = {
    for {
      tm <- mapperMaterializer.materialize(implicitly[TableMapper[A]])
      rows <-
        query[A](tm.selectSql(whereClause))
          .select
    } yield rows
  }

  override def streamingSelectRows[A: TableMapper](whereClause: SqlString): fs2.Stream[F, A] = {
    mapperMaterializer
      .materialize(implicitly[TableMapper[A]])
      .fs2StreamEval
      .flatMap(tableMapper =>
        streamingQuery[A](tableMapper.selectSql(whereClause))
          .run
      )
  }

  override def fetchRow[A, B](key: B)(implicit keyedMapper: KeyedTableMapper[A, B]): F[A] =
    fetchRowOpt[A,B](key)
      .flatMap {
        case Some(row) =>
          F.pure(row)
        case None =>
          F.raiseError[A](new RuntimeException(s"expected a record with key ${key} in ${implicitly[TableMapper[A]].tableName}"))
      }

  override def fetchRowOpt[A, B](key: B)(implicit keyedMapper: KeyedTableMapper[A, B]): F[Option[A]] =
    selectOpt(keyedMapper.keyToWhereClause(key))

  override def commit: F[Unit] =
    F.blocking(jdbcConn.commit())

  override def rollback: F[Unit] =
    F.blocking(jdbcConn.rollback())

}
