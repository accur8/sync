package a8.shared.jdbcf.mapper

import a8.shared.SharedImports._
import a8.shared.jdbcf.mapper.MapperBuilder.AuditProvider
import a8.shared.jdbcf.{Conn, RowReader, RowWriter, SqlString, TableName}

object TableMapper {
  def apply[A: TableMapper]: TableMapper[A] = implicitly[TableMapper[A]]
}

trait TableMapper[A] extends ComponentMapper[A] { self =>

  val tableName: TableName

  def insertSql(row: A): SqlString
  def selectFieldsSql(alias: String): SqlString
  def selectSql(whereClause: SqlString): SqlString

  /**
   * materialize takes a connection and can return a mapper refined to the supplied connection.
   *
   * For example given a mysql connection it may do mysql specific keyword quoting.
   *
   * @param conn
   * @tparam F
   * @return
   */
  def materializeTableMapper(implicit conn: Conn): zio.Task[TableMapper[A]]

  def auditProvider: AuditProvider[A]

}
