package a8.shared.jdbcf.querydsl


import a8.shared.SharedImports._
import a8.shared.jdbcf.{Conn, SqlString}
import a8.shared.jdbcf.querydsl.QueryDsl._
import zio.stream.UStream
import zio._

trait SelectQuery[T, U] {

  def where: Condition
  def orderBy: Iterable[OrderBy]
  def orderBy(order: U => OrderBy): SelectQuery[T,U]
  def orderBys(order: U => Iterable[OrderBy]): SelectQuery[T,U]
  def maxRows(count: Int): SelectQuery[T,U]

  def fetch(implicit conn: Conn): Task[T]
  def fetchOpt(implicit conn: Conn): Task[Option[T]]
  def streamingSelect(implicit conn: Conn): XStream[T]
  def select(implicit conn: Conn): Task[Vector[T]]
  def sqlString: SqlString

}
