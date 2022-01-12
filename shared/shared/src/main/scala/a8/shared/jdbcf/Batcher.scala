package a8.shared.jdbcf

import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.Conn.impl.withSqlCtx
import a8.shared.jdbcf.SqlString.ResolvedSql
import cats.effect.Async
import fs2.Chunk

import scala.language.higherKinds

object Batcher {

  def create[F[_] : Async, A : RowWriter](conn: ConnInternal[F], sql: SqlString): Batcher[F,A] = {

    val sql0 = sql
    val writerA = implicitly[RowWriter[A]]
    val F = Async[F]

    new Batcher[F,A] {

      override lazy val sql = conn.resolve(sql0)

      override def execBatch(stream: fs2.Stream[F, A]): fs2.Stream[F,Int] = {
        conn
          .prepare(sql0)
          .flatMap { ps =>
            val results =
              fs2.Stream
                .eval[F, fs2.Stream[F,Int]] {
                  withSqlCtx(sql) {
                    val results = ps.executeBatch()
                    fs2.Stream.chunk[F,Int](Chunk.array(results))
                  }
                }
                .flatten
            fs2.Stream.exec {
              stream
                .map { a =>
                  writerA.write(ps, 1, a)
                  ps.addBatch()
                }
                .compile
                .drain
            } ++ results
          }
      }
    }

  }

}



trait Batcher[F[_],A] {
  val sql: ResolvedSql
  def execBatch(stream: fs2.Stream[F,A]): fs2.Stream[F,Int]
}

