package a8.shared.jdbcf

import a8.shared.{ProgressCounter, SharedImports}
import a8.shared.SharedImports.*
import a8.shared.app.Ctx
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.SqlString.CompiledSql
import a8.shared.zreplace.XStream

object Batcher {

  def create[A : RowWriter](conn: ConnInternal, sql: SqlString)(using Ctx): Batcher[A] = {

    val batchSize = 10_000
    val sql0 = sql
    val writerA = implicitly[RowWriter[A]]

    new Batcher[A] {

      override lazy val sql = conn.compile(sql0)

      var incrementFn: ()=>Unit = () => ()

      override def useCounter(ctx: String, totalCount: Long, reportEvery: Long = 1000)(using SharedImports.Logger): Batcher[A] = {
        val progressCounter = new ProgressCounter(ctx, totalCount, reportEvery)
        incrementFn = () => progressCounter.increment()
        this
      }

      override def execBatch(stream: XStream[A]): Iterable[Int] = {
        val ps =
          conn
            .prepare(sql0)
            .unwrap

        var ct = 0

        stream
          .runForeach { a =>
            writerA.applyParameters(ps, a, 0)
            ps.addBatch()
            ct += 1
            if ( ct % batchSize == 0 ) {
              ps.executeBatch()
              ps.clearBatch()
            }
            incrementFn()
          }

        withSqlCtx(conn.databaseId, sql) {
          ps.executeBatch()
        }
      }
    }



  }

}



trait Batcher[A] {
  lazy val sql: CompiledSql
  def execBatch(stream: XStream[A]): Iterable[Int]
  def useCounter(ctx: String, totalCount: Long, reportEvery: Long = 1000)(using Logger): Batcher[A]
}

