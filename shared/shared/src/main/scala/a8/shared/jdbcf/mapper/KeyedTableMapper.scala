package a8.shared.jdbcf.mapper


import a8.shared.SharedImports._
import a8.shared.jdbcf.{Conn, SqlString}
import a8.shared.jdbcf.mapper.KeyedTableMapper.UpsertResult
import zio._

object KeyedTableMapper {

  sealed trait UpsertResult
  object UpsertResult {
    case object Update extends UpsertResult
    case object Insert extends UpsertResult
  }

}

trait KeyedTableMapper[A, PK] extends TableMapper[A] {

  def keyToWhereClause(key: PK): SqlString
  def updateSql(row: A, extraWhere: Option[SqlString] = None): SqlString
  def deleteSql(key: PK): SqlString
  def fetchSql(key: PK): SqlString
  def key(row: A): PK

  override def materializeTableMapper(implicit conn: Conn): Task[TableMapper[A]] =
    materializeKeyedTableMapper
      .map {
        case tm: TableMapper[A] =>
          tm
      }

  def materializeKeyedTableMapper(implicit conn: Conn): Task[KeyedTableMapper[A,PK]]

}
