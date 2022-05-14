package a8.shared.jdbcf

import a8.shared.SharedImports._
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.Conn.impl.withSqlCtx0
import a8.shared.jdbcf.SqlString.CompiledSql
import zio._
import zio.stream.{UStream, ZSink, ZStream}

object Query {

  def create[A : RowReader](conn: ConnInternal, sql: SqlString): Query[A] = {

    val sql0 = conn.compile(sql)

    new Query[A] {

      override val sql = sql0

      override val reader: RowReader[A] = implicitly[RowReader[A]]

      def stream: XStream[A] = {
        val effect: ZIO[Scope, Throwable, XStream[A]] =
          conn
            .statement
            .flatMap(st =>
              Managed
                .scoped[java.sql.ResultSet](withSqlCtx0(sql)(st.executeQuery(sql.value)))
                .map(rs =>
                  resultSetToStream(rs)
                    .map(reader.read)
                )
            )
        ZStream.unwrapScoped(effect)
      }

      override def select: Task[Iterable[A]] =
        stream
          .run(ZSink.collectAll)
          .map(values => values: Iterable[A])

      override def unique: Task[A] =
        fetch
          .flatMap {
            case None =>
              ZIO.die(throw new java.sql.SQLException(s"query return 0 records expected 1 -- ${sql}"))
            case Some(v) =>
              ZIO.succeed(v)
          }


      override def fetch: Task[Option[A]] =
        stream
          .take(1)
          .run(ZSink.last)

    }
  }

}


trait Query[A] { query =>
  val sql: CompiledSql
  val reader: RowReader[A]
  def select: Task[Iterable[A]]
  def unique: Task[A]
  def fetch: Task[Option[A]]
}

