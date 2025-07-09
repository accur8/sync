package a8.shared.jdbcf

import a8.shared.SharedImports.*
import a8.shared.app.Ctx
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.SqlString.CompiledSql

object Query {

  def create[A : RowReader](conn: ConnInternal, sql: SqlString): Query[A] = {

    val sql0 = conn.compile(sql)

    new Query[A] {

      override val sql: CompiledSql = sql0

      override val reader: RowReader[A] = implicitly[RowReader[A]]

      def stream: zio.XStream[A] = {
        withSqlCtx(sql) {
          conn.withInternalConn { jdbcConn =>
            // no cleanup since the result set will break out of the scope of this function
            val st = jdbcConn.createStatement()
            val rs = st.executeQuery(sql.value)
            resultSetToStream(rs)
              .map(reader.read)
          }
        }
      }

      override def select: Iterable[A] = {
        given Ctx = conn.ctx
        stream.runCollect()
      }

      override def fetchOpt: Option[A] = {
        given Ctx = conn.ctx
        select.headOption
      }

    }
  }

}


/**
 * a lazy representation of a query where select, fetchOpt, and fetch will actually run the query
 */
trait Query[A] { query =>
  val sql: CompiledSql
  val reader: RowReader[A]
  def select: Iterable[A]
  def fetchOpt: Option[A]
  def fetch: A =
    fetchOpt match {
      case None =>
        throw new java.sql.SQLException(s"query return 0 records expected 1 -- ${sql}")
      case Some(v) =>
        v
    }
}

