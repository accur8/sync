package a8.shared.jdbcf.mapper

import a8.shared.SharedImports._
import a8.shared.jdbcf.{Conn, RowReader, RowWriter, SqlString, TableName}

object TableMapper {

}

trait TableMapper[A] extends Mapper[A] {

  val tableName: TableName

  def insertSqlPs: SqlString
  def selectFieldsSqlPs(alias: String): SqlString
  def selectSql(whereClause: SqlString): SqlString

  def applyInsert(ps: java.sql.PreparedStatement, a: A): Unit

}
