package a8.shared.jdbcf


import java.sql.{Connection as JdbcConnection, DriverManager as JdbcDriverManager, PreparedStatement as JdbcPreparedStatement, SQLException as JdbcSQLException, Statement as JStatement}
import a8.shared.jdbcf.JdbcMetadata.JdbcTable
import a8.shared.jdbcf.SqlString.{CompiledSql, Escaper}
import a8.shared.jdbcf.mapper.KeyedTableMapper.UpsertResult
import a8.shared.jdbcf.mapper.{KeyedTableMapper, TableMapper}
import sttp.model.Uri
import a8.shared.SharedImports.*
import a8.shared.app.Ctx
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.ConnFactoryCompanion.MapperMaterializer
import zio.*

case class ConnInternalImpl(
  jdbcMetadata: JdbcMetadata,
  jdbcConn: java.sql.Connection,
  mapperMaterializer: MapperMaterializer,
  jdbcUrl: Uri,
  dialect: Dialect,
  escaper: Escaper,
)(
  using ctx0: Ctx
)
  extends Logging
  with ConnInternal
{

  /**
   * provides a statement scoped to the current Ctx
   *
   * @return
   */
  override def unsafeStatement(): JStatement = {
    val st = jdbcConn.createStatement()
    lazy val release = {
      if ( !st.isClosed )
        st.close()
    }
    ctx.register(new Ctx.Listener {
      override def onCompletion(ctx: Ctx, completion: Ctx.Completion): Unit =
        release
    })
    st
  }


  override def ctx: Ctx = ctx0

  override def asInternal: ConnInternal = this

  override def compile(sql: SqlString): CompiledSql =
    SqlString.unsafe.compile(sql, escaper)

  override def tables: Iterable[JdbcTable] = {
    resultSetToVector(
      jdbcConn
        .getMetaData
        .getTables(null, null, null, null)
    )
      .map(JdbcTable.apply)
  }


  override def selectOne[A: TableMapper](whereClause: SqlString): A =
    selectOpt[A](whereClause) match {
      case Some(a) =>
        a
      case None =>
        throw new RuntimeException(s"expected a single value and got none with whereClause = ${whereClause}")
    }

  override def selectOpt[A: TableMapper](whereClause: SqlString): Option[A] =
    selectRows[A](whereClause).toList match {
      case Nil =>
        None
      case List(a) =>
        Some(a)
      case l =>
        throw new RuntimeException(s"expected 0 or 1 value got ${l.size} with whereClase = ${whereClause} -- ${l}")
    }

  override def streamingQuery[A : RowReader](sql: SqlString): StreamingQuery[A] =
    StreamingQuery.create[A](this, sql)

  override def query[A: RowReader](sqlStr: SqlString): Query[ A] =
    Query.create[A](this, sqlStr)

  override def update(updateQuery: SqlString): Int = {
    withPreparedStatement(updateQuery) { ps =>
      ps.executeUpdate()
    }
  }


  override def withStatement[A](fn: JStatement => A): A = {
    val st = jdbcConn.createStatement()
    try {
      fn(st)
    } finally {
      st.close()
    }
  }

  override def statement: Resource[JStatement] = {
    Resource.acquireRelease[JStatement](jdbcConn.createStatement())(_.close())
  }

  override def prepare(sql: SqlString): Resource[JdbcPreparedStatement] = {
    val resolvedSql = compile(sql)
    def acquire =
      withSqlCtx[JdbcPreparedStatement](
        resolvedSql
      )(
        jdbcConn.prepareStatement(resolvedSql.value)
      )
    Resource.acquireRelease(acquire)(_.close())
  }

  protected def withPreparedStatement[A](sql: SqlString)(fn: JdbcPreparedStatement=>A): A = {
    val resolvedSql = compile(sql)
    withSqlCtx[A](
      resolvedSql
    ) {
      val ps = jdbcConn.prepareStatement(resolvedSql.value)
      try {
        fn(ps)
      } finally {
        ps.close()
      }
    }
  }

  override def batcher[A : RowWriter](sql: SqlString): Batcher[A] =
    Batcher.create[A](this, sql)

  override def withInternalConn[A](fn: JdbcConnection => A): A = {
    fn(jdbcConn)
  }

  override def isAutoCommit: Boolean = {
    jdbcConn.getAutoCommit
  }

  def runSingleRowUpdate(updateQuery: SqlString): Unit =
    runSingleRowUpdateOpt(updateQuery) match {
      case true =>
        ()
      case false =>
        throw new RuntimeException(s"ran update and expected 1 row to be affected and 0 rows were affected -- ${updateQuery}")
    }

  def runSingleRowUpdateOpt(updateQuery: SqlString): Boolean =
    update(updateQuery) match {
      case 1 =>
        true
      case 0 =>
        false
      case i =>
        throw new RuntimeException(s"ran update and expected 1 row to be affected and ${i} rows were affected -- ${updateQuery}")
    }

  override def insertRow[A: TableMapper](row: A): A = {
    val auditedRow: A = TableMapper[A].auditProvider.onInsert(row)
    val mapper = mapperMaterializer.materialize(implicitly[TableMapper[A]])
    runSingleRowUpdate(mapper.insertSql(auditedRow))
    auditedRow
  }

  override def upsertRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A, B]): (A,UpsertResult) = {
    val mapper = implicitly[KeyedTableMapper[A,B]]
    fetchRowOpt(mapper.key(row)) match {
      case Some(v) =>
        updateRow(row) -> UpsertResult.Update
      case None =>
        insertRow(row) -> UpsertResult.Insert
    }
  }

  override def updateRow[A, B](row: A)(using keyedMapper: KeyedTableMapper[A, B]): A = {
    val auditedRow: A = keyedMapper.auditProvider.onUpdate(row)
    val mapper = mapperMaterializer.materialize(keyedMapper)
    runSingleRowUpdate(mapper.value.updateSql(auditedRow))
    auditedRow
  }

  override def updateRowWhere[A, B](row: A)(where: SqlString)(implicit keyedMapper: KeyedTableMapper[A, B]): Option[A] = {
    val auditedRow = keyedMapper.auditProvider.onUpdate(row)
    val mapper = mapperMaterializer.materialize(keyedMapper)
    val rowUpdated = runSingleRowUpdateOpt(mapper.value.updateSql(auditedRow, where.some))
    rowUpdated.toOption(auditedRow)
  }

  override def deleteRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A, B]): A = {
    val mapper: KeyedTableMapper.Materialized[A, B] = mapperMaterializer.materialize(keyedMapper)
    runSingleRowUpdate(mapper.value.deleteSql(mapper.value.key(row)))
    row
  }

  override def selectRows[A: TableMapper](whereClause: SqlString): Iterable[A] = {
    val tm = mapperMaterializer.materialize(summon[TableMapper[A]])
    val rows = query[A](tm.selectSql(whereClause)).select
    rows
  }

  override def streamingSelectRows[A: TableMapper](whereClause: SqlString): XStream[A] = {
    val tableMapper =
      mapperMaterializer
        .materialize(implicitly[TableMapper[A]])
    streamingQuery[A](tableMapper.selectSql(whereClause))
      .stream
  }

  override def fetchRow[A, B](key: B)(implicit keyedMapper: KeyedTableMapper[A, B]): A =
    fetchRowOpt[A,B](key) match {
      case Some(row) =>
        row
      case None =>
        throw new RuntimeException(s"expected a record with key ${key} in ${implicitly[TableMapper[A]].tableName}")
    }

  override def fetchRowOpt[A, B](key: B)(implicit keyedMapper: KeyedTableMapper[A, B]): Option[A] = {
    selectOpt[A](keyedMapper.keyToWhereClause(key))
  }

  override def commit: Unit =
    jdbcConn.commit()

  override def rollback: Unit =
    jdbcConn.rollback()

  override def materializedMapper[A, PK](using keyedTableMapper: KeyedTableMapper[A, PK]): KeyedTableMapper.Materialized[A, PK] =
    mapperMaterializer.materialize(keyedTableMapper)
}
