package a8.shared.jdbcf

import a8.shared.SharedImports._
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.SqlString.CompiledSql
import zio.stream.{UStream, ZSink, ZStream}

import scala.language.higherKinds

object Batcher {

  def create[A : RowWriter](conn: ConnInternal, sql: SqlString): Batcher[A] = {

    val sql0 = sql
    val writerA = implicitly[RowWriter[A]]

    new Batcher[A] {

      override lazy val sql = conn.compile(sql0)

      override def execBatch(stream: XStream[A]): XStream[Int] = {
        val effect =
          conn
            .prepare(sql0)
            .map { ps =>
              val results: XStream[Int] =
                ZStream
                  .fromIterableZIO {
                    withSqlCtx(sql) {
                      ps.executeBatch()
                    }
                  }
              val header =
                ZStream
                  .execute {
                    stream
                      .map { a =>
                        writerA.applyParameters(ps, a, 0)
                        ps.addBatch()
                      }
                      .run(ZSink.last)
                  }
              header
                .flatMap(_ => results)
            }
        ZStream.unwrapScoped(effect)
      }
    }

  }

}



trait Batcher[A] {
  val sql: CompiledSql
  def execBatch(stream: XStream[A]): XStream[Int]
}

