package a8.shared.jdbcf.mapper

import a8.shared.SharedImports.Async
import a8.shared.jdbcf.{Conn, SqlString}
import a8.shared.jdbcf.mapper.KeyedTableMapper.UpsertResult


object KeyedTableMapper {

  sealed trait UpsertResult
  object UpsertResult {
    case object Update extends UpsertResult
    case object Insert extends UpsertResult
  }

}

trait KeyedTableMapper[A, B] extends TableMapper[A] {

  val updateSqlPs: SqlString
  val deleteSqlPs: SqlString
  val fetchSqlPs: SqlString
  val fetchWherePs: SqlString
//  def fetchWhere(whereClause: SqlString): SqlString
  def key(row: A): B

  def applyUpdate(ps: java.sql.PreparedStatement, a: A): Unit
  def applyDelete(ps: java.sql.PreparedStatement, b: B): Unit = applyWhere(ps, b, 0)
  def applyWhere(ps: java.sql.PreparedStatement, b: B, parameterIndex: Int = 1): Unit

}
