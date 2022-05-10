package a8.shared.jdbcf

import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.Conn.impl.withSqlCtx0
import a8.shared.jdbcf.SqlString.CompiledSql
import cats.effect.Async

import scala.language.higherKinds

object StreamingQuery {

  def create[F[_] : Async, A : RowReader](conn: ConnInternal[F], sql: SqlString): StreamingQuery[F,A] = {
    Impl(conn, conn.compile(sql), 1000)
  }

  case class Impl[F[_] : Async, A : RowReader](conn: ConnInternal[F], sql: CompiledSql, batchSize: Int) extends StreamingQuery[F,A] {

    val F = Async[F]

    override val reader: RowReader[A] = implicitly[RowReader[A]]

    override def run: fs2.Stream[F, A] = {
      conn
        .statement
        .flatMap { st =>
          st.getConnection.setAutoCommit(false)
          st.setFetchSize(batchSize)
          Managed.stream[F,java.sql.ResultSet](withSqlCtx0(sql)(st.executeQuery(sql.value)))
            .flatMap(rs => resultSetToStream(rs, batchSize))
            .map(reader.read)
        }
    }

    override def batchSize(size: Int): StreamingQuery[F, A] =
      copy(batchSize = size)

  }

}


trait StreamingQuery[F[_],A] {
  val sql: CompiledSql
  val reader: RowReader[A]
  def run: fs2.Stream[F,A]
  def batchSize(size: Int): StreamingQuery[F,A]
}
