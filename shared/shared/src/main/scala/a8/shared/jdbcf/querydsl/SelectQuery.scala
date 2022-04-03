package a8.shared.jdbcf.querydsl

import a8.shared.jdbcf.Conn
import a8.shared.jdbcf.querydsl.QueryDsl._


trait SelectQuery[F[_], T, U] {

  def where: Condition
  def orderBy: Iterable[OrderBy]
  def orderBy(order: U => OrderBy): SelectQuery[F,T,U]
  def orderBys(order: U => Iterable[OrderBy]): SelectQuery[F,T,U]
  def maxRows(count: Int): SelectQuery[F,T,U]

  def fetch(implicit conn: Conn[F]): F[T]
  def fetchOpt(implicit conn: Conn[F]): F[Option[T]]
  def select(implicit conn: Conn[F]): fs2.Stream[F,T]
  def streamingSelect(implicit conn: Conn[F]): fs2.Stream[F,T]
  def toVector(implicit conn: Conn[F]): F[Vector[T]]
  def asSql: String

}
