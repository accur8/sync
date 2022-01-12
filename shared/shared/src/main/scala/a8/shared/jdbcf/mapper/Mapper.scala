package a8.shared.jdbcf.mapper

import a8.shared.SharedImports._
import a8.shared.jdbcf.{Conn, RowReader, SqlString, TableName}

object Mapper {

}

trait Mapper[A] extends RowReader[A] {

  val tableName: TableName

  def insertSql(record: A): SqlString
  def selectSql(whereClause: SqlString): SqlString

}
