package a8.shared.jdbcf

import a8.shared.SharedImports._
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.Conn.impl.withSqlCtx0
import a8.shared.jdbcf.SqlString.CompiledSql

import scala.language.higherKinds

object StreamingQuery {

  def create[A : RowReader](conn: ConnInternal, sql: SqlString): StreamingQuery[A] = {
    Impl(conn, conn.compile(sql), 1000)
  }

  case class Impl[A : RowReader](conn: ConnInternal, sql: CompiledSql, batchSize: Int) extends StreamingQuery[A] {

    override val reader: RowReader[A] = implicitly[RowReader[A]]

    override def run: UStream[A] = {
      conn
        .statement
        .flatMap { st =>
          st.getConnection.setAutoCommit(false)
          st.setFetchSize(batchSize)
          Managed.stream[java.sql.ResultSet](withSqlCtx0(sql)(st.executeQuery(sql.value)))
            .flatMap(rs => resultSetToStream(rs, batchSize))
            .map(reader.read)
        }
    }

    override def batchSize(size: Int): StreamingQuery[A] =
      copy(batchSize = size)

  }

}


trait StreamingQuery[A] {
  val sql: CompiledSql
  val reader: RowReader[A]
  def run: UStream[A]
  def batchSize(size: Int): StreamingQuery[A]
}
