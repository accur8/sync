package a8.shared.jdbcf

import a8.shared.SharedImports._
import a8.shared.jdbcf
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.SqlString.CompiledSql

object StreamingQuery {

  def create[A : RowReader](conn: ConnInternal, sql: SqlString): StreamingQuery[A] = {
    Impl(conn, conn.compile(sql), 1000)
  }

  case class Impl[A : RowReader](conn: ConnInternal, sql: CompiledSql, batchSize: Int) extends StreamingQuery[A] {

    override val reader: RowReader[A] = implicitly[RowReader[A]]

    override def run: zio.XStream[A] = {
      val effect =
        conn
          .statement
          .flatMap { st =>
            st.getConnection.setAutoCommit(false)
            st.setFetchSize(batchSize)
            val effect = Managed.scoped[java.sql.ResultSet](st.executeQuery(sql.value))
            withSqlCtxT(sql, effect)
              .map(rs =>
                resultSetToStream(rs, batchSize)
                  .map(reader.read)
              )
          }
      ZStream.unwrapScoped(effect)
    }

    override def batchSize(size: Int): StreamingQuery[A] =
      copy(batchSize = size)

  }

}


trait StreamingQuery[A] {
  val sql: CompiledSql
  val reader: RowReader[A]
  def run: zio.XStream[A]
  def batchSize(size: Int): StreamingQuery[A]
}
