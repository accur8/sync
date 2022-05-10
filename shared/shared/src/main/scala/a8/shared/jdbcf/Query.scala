package a8.shared.jdbcf

import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.Conn.impl.withSqlCtx0
import a8.shared.jdbcf.SqlString.CompiledSql
import cats.effect.Async
import cats.implicits._

import scala.language.higherKinds

object Query {

  def create[F[_] : Async, A : RowReader](conn: ConnInternal[F], sql: SqlString): Query[F,A] = {

    val F = Async[F]
    val sql0 = conn.compile(sql)

    new Query[F,A] {

      override val sql = sql0

      override val reader: RowReader[A] = implicitly[RowReader[A]]

      def stream: fs2.Stream[F, A] = {
        conn
          .statement
          .flatMap { st =>
            Managed.stream[F,java.sql.ResultSet](withSqlCtx0(sql)(st.executeQuery(sql.value)))
              .flatMap(rs => resultSetToStream(rs))
                .map(reader.read)
          }
      }

      override def select: F[Iterable[A]] =
        stream
          .compile
          .toList
          .map(_.toIterable)

      override def unique: F[A] =
        fetch
          .flatMap {
            case None =>
              F.raiseError(throw new java.sql.SQLException(s"query return 0 records expected 1 -- ${sql}"))
            case Some(v) =>
              F.pure(v)
          }


      override def fetch: F[Option[A]] =
        stream
          .take(1)
          .compile
          .last

    }
  }

}


trait Query[F[_],A] { query =>
  val sql: CompiledSql
  val reader: RowReader[A]
  def select: F[Iterable[A]]
  def unique: F[A]
  def fetch: F[Option[A]]
}

