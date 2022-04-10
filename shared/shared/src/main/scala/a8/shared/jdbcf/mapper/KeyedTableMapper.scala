package a8.shared.jdbcf.mapper

import a8.shared.SharedImports._
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

  def updateSql(row: A): SqlString
  def deleteSql(key: B): SqlString
  def fetchSql(key: B): SqlString
  def key(row: A): B

  override def materializeTableMapper[F[_] : Async](implicit conn: Conn[F]): F[TableMapper[A]] =
    materializeKeyedTableMapper[F]
      .map {
        case tm: TableMapper[A] =>
          tm
      }

  def materializeKeyedTableMapper[F[_]: Async](implicit conn: Conn[F]): F[KeyedTableMapper[A,B]]

}
