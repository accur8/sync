package a8.shared.jdbcf.mapper

import a8.shared.SharedImports.Async
import a8.shared.jdbcf.{Conn, SqlString}
import a8.shared.jdbcf.mapper.KeyedMapper.UpsertResult


object KeyedMapper {

  sealed trait UpsertResult
  object UpsertResult {
    case object Update extends UpsertResult
    case object Insert extends UpsertResult
  }

}

trait KeyedMapper[A, B] extends Mapper[A] {

  def updateSql(record: A): SqlString
  def deleteSql(record: A): SqlString
  def fetchSql(key: B): SqlString
  def fetchWhere(key: B): SqlString
  def key(row: A): B

}
