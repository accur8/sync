package a8.hermes.jdbcrpc

import a8.shared.SharedImports.*
import a8.shared.app.Ctx
import a8.shared.jdbcf.*
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.ConnFactoryCompanion.MapperMaterializer
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import a8.shared.jdbcf.JdbcMetadata.{JdbcTable, ResolvedJdbcTable}
import a8.shared.jdbcf.SqlString.{CompiledSql, Escaper}
import a8.shared.jdbcf.mapper.KeyedTableMapper.UpsertResult
import a8.shared.jdbcf.mapper.{KeyedTableMapper, TableMapper}
import a8.common.logging.Logging

import java.sql.{Connection as JdbcConnection, PreparedStatement as JdbcPreparedStatement, Statement as JStatement}

/**
 * A [[Conn]] implementation that executes SQL through the godev "dbaccess" SQL-firewall RPC instead of
 * JDBC. This is what lets checkpoint use `Mapper` (and therefore generated continuum models) with NO DB
 * credentials — auth rides the caller's mailbox identity inside [[DbRpcClient]].
 *
 * Design (see plan): reuse Mapper's existing `SqlString` generation and route it through the firewall's
 * `Query` endpoint; convert the `QueryResponse` Struct rows back into sync `Row`s via [[StructRowReader]].
 * The row-level ops (`insertRow`/`updateRow`/`fetchRowOpt`/...) are implemented exactly as in
 * `ConnInternalImpl` because they already delegate to `query`/`update` + Mapper SqlString — only the two
 * execution points (`query`, `update`) differ.
 *
 * JDBC-only members of the trait (`withInternalConn`, `statement`, `prepare`, `unsafeStatement`, `tables`,
 * `tableMetadata`, `batcher`, `isAutoCommit`) are unsupported here; checkpoint never calls them. The firewall
 * resolves schema/table names server-side, so `resolveTableName` returns the locator unchanged and
 * `mapperMaterializer` is the noop materializer.
 *
 * Transactions: `commit`/`rollback` are no-ops — each RPC is its own server-side transaction (v1). Single-row
 * conditional updates (e.g. pause/resume's `WHERE ... resumeuid is null`) remain atomic within one statement.
 */
case class RpcConn(
  databaseId: DatabaseId,
  dbClient: DbRpcClient,
)(
  using ctx0: Ctx
) extends Logging
  with ConnInternal {

  import RpcConn.unsupported

  override def ctx: Ctx = ctx0
  override def dialect: Dialect = PostgresDialect
  // Never quote identifiers: the generated continuum model carries mixed-case logical names
  // (`ProcessRun`, `workerUid`), but the physical Postgres identifiers are all default-case
  // (`processrun`, `workeruid`). Emitting them UNQUOTED lets Postgres fold them to the physical
  // lowercase names; quoting would make them case-sensitive and fail (`column "workerUid" does not
  // exist`). NoopEscaper leaves identifiers bare while still safely escaping string literal VALUES.
  // (Simple fix for now; the firewall is also being taught to resolve identifier case server-side.)
  override def escaper: Escaper = SqlString.NoopEscaper
  override val jdbcUrl: sttp.model.Uri = sttp.model.Uri.unsafeParse("rpc://dbaccess")
  override val jdbcMetadata: JdbcMetadata = JdbcMetadata.default

  override def asInternal: ConnInternal = this

  override def compile(sql: SqlString): CompiledSql =
    SqlString.unsafe.compile(sql, escaper)

  // ---- the two real execution points -------------------------------------------------------------

  override def query[A: RowReader](sqlStr: SqlString): Query[A] = {
    val compiled = compile(sqlStr)
    new Query[A] {
      override val sql: CompiledSql = compiled
      override val reader: RowReader[A] = summon[RowReader[A]]
      override def select: Iterable[A] =
        withSqlCtx(databaseId, compiled) {
          val resp = dbClient.query(compiled.value)
          StructRowReader.toRows(resp).map(reader.read)
        }
      override def fetchOpt: Option[A] = select.headOption
    }
  }

  override def update(updateQuery: SqlString): Int = {
    val compiled = compile(updateQuery)
    withSqlCtx(databaseId, compiled) {
      dbClient.query(compiled.value).rowCount
    }
  }

  // ---- name resolution: firewall does it server-side --------------------------------------------

  override def resolveTableName(tableLocator: TableLocator, useCache: Boolean = true): ResolvedTableName =
    ResolvedTableName(None, tableLocator.schemaName, tableLocator.tableName)

  override def tables: Iterable[JdbcTable] = unsupported("tables")
  override def tableMetadata(tableLocator: TableLocator, useCache: Boolean = true): ResolvedJdbcTable =
    unsupported("tableMetadata")

  // ---- Mapper-based row ops (identical to ConnInternalImpl; they route through query/update) -----

  private val mapperMaterializer: MapperMaterializer = MapperMaterializer.noop

  override def materializedMapper[A, PK](using keyedTableMapper: KeyedTableMapper[A, PK]): KeyedTableMapper.Materialized[A, PK] =
    mapperMaterializer.materialize(keyedTableMapper)

  private def runSingleRowUpdate(updateQuery: SqlString): Unit =
    if !runSingleRowUpdateOpt(updateQuery) then
      throw new RuntimeException(s"ran update and expected 1 row to be affected and 0 rows were affected -- ${updateQuery}")

  private def runSingleRowUpdateOpt(updateQuery: SqlString): Boolean =
    update(updateQuery) match {
      case 1 => true
      case 0 => false
      case i => throw new RuntimeException(s"ran update and expected 1 row to be affected and ${i} rows were affected -- ${updateQuery}")
    }

  override def insertRow[A: TableMapper](row: A): A = {
    val auditedRow = TableMapper[A].auditProvider.onInsert(row)
    val mapper = mapperMaterializer.materialize(summon[TableMapper[A]])
    runSingleRowUpdate(mapper.insertSql(auditedRow))
    auditedRow
  }

  override def upsertRow[A, B](row: A)(implicit keyedMapper: KeyedTableMapper[A, B]): (A, UpsertResult) =
    fetchRowOpt(keyedMapper.key(row)) match {
      case Some(_) => updateRow(row) -> UpsertResult.Update
      case None    => insertRow(row) -> UpsertResult.Insert
    }

  override def updateRow[A, B](row: A)(using keyedMapper: KeyedTableMapper[A, B]): A = {
    val auditedRow = keyedMapper.auditProvider.onUpdate(row)
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
    val mapper = mapperMaterializer.materialize(keyedMapper)
    runSingleRowUpdate(mapper.value.deleteSql(mapper.value.key(row)))
    row
  }

  override def selectRows[A: TableMapper](whereClause: SqlString): Iterable[A] = {
    val tm = mapperMaterializer.materialize(summon[TableMapper[A]])
    query[A](tm.selectSql(whereClause)).select
  }

  override def selectOne[A: TableMapper](whereClause: SqlString): A =
    selectOpt[A](whereClause).getOrElse(throw new RuntimeException(s"expected a single value and got none with whereClause = ${whereClause}"))

  override def selectOpt[A: TableMapper](whereClause: SqlString): Option[A] =
    selectRows[A](whereClause).toList match {
      case Nil      => None
      case List(a)  => Some(a)
      case l        => throw new RuntimeException(s"expected 0 or 1 value got ${l.size} with whereClause = ${whereClause} -- ${l}")
    }

  override def fetchRow[A, B](key: B)(implicit keyedMapper: KeyedTableMapper[A, B]): A =
    fetchRowOpt[A, B](key).getOrElse(throw new RuntimeException(s"expected a record with key ${key} in ${summon[TableMapper[A]].tableName}"))

  override def fetchRowOpt[A, B](key: B)(implicit keyedMapper: KeyedTableMapper[A, B]): Option[A] =
    selectOpt[A](keyedMapper.keyToWhereClause(key))

  // ---- transactions: no-op (each RPC is its own server-side txn in v1) ---------------------------

  override def commit: Unit = ()
  override def rollback: Unit = ()

  // ---- JDBC-only members: unsupported ------------------------------------------------------------

  override def streamingQuery[A: RowReader](sql: SqlString): StreamingQuery[A] = unsupported("streamingQuery")
  override def streamingSelectRows[A: TableMapper](whereClause: SqlString): zio.XStream[A] = unsupported("streamingSelectRows")
  override def batcher[A: RowWriter](sql: SqlString): Batcher[A] = unsupported("batcher")
  override def isAutoCommit: Boolean = unsupported("isAutoCommit")
  override def withInternalConn[A](fn: JdbcConnection => A): A = unsupported("withInternalConn")
  override def withStatement[A](fn: JStatement => A): A = unsupported("withStatement")
  override def statement: zio.Resource[JStatement] = unsupported("statement")
  override def unsafeStatement(): JStatement = unsupported("unsafeStatement")
  override def prepare(sql: SqlString): zio.Resource[JdbcPreparedStatement] = unsupported("prepare")

}

object RpcConn {
  private def unsupported(member: String): Nothing =
    throw new UnsupportedOperationException(s"RpcConn does not support `$member` (JDBC-only); checkpoint should not call it")
}
