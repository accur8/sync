package a8.shared.jdbcf.mapper

import a8.shared.SharedImports._
import a8.shared.jdbcf.{Conn, RowReader, RowWriter, SqlString, TableName}

object TableMapper {

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
  def materialize[F[_]: Async](implicit conn: Conn[F]): F[self.type]

}
