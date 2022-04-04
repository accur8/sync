package a8.shared.jdbcf.mapper

import a8.shared.SharedImports._
import a8.shared.jdbcf.{Conn, RowReader, RowWriter, SqlString, TableName}

object TableMapper {

}

trait TableMapper[A] extends ComponentMapper[A] {

  val tableName: TableName

  def insertSql(row: A): SqlString
  def selectFieldsSql(alias: String): SqlString
  def selectSql(whereClause: SqlString): SqlString

}
