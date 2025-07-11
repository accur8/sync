package a8.shared.jdbcf

import a8.shared.SharedImports.*
import a8.shared.app.Ctx
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.SqlString.CompiledSql
import a8.shared.zreplace.XStream

object Batcher {

  def create[A : RowWriter](conn: ConnInternal, sql: SqlString)(using Ctx): Batcher[A] = {

    val sql0 = sql
    val writerA = implicitly[RowWriter[A]]

    new Batcher[A] {

      override lazy val sql = conn.compile(sql0)

      override def execBatch(stream: XStream[A]): Iterable[Int] = {
        val ps =
          conn
            .prepare(sql0)
            .unwrap

        stream
          .runForeach { a =>
            writerA.applyParameters(ps, a, 0)
            ps.addBatch()
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
}

