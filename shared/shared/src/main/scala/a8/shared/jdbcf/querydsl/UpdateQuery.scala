package a8.shared.jdbcf.querydsl


import a8.shared.jdbcf.Conn
import a8.shared.jdbcf.querydsl.QueryDsl.Expr

import scala.language.implicitConversions


trait UpdateQuery[F[_],TableDsl] {

  def where(whereFn: TableDsl => QueryDsl.Condition): UpdateQuery[F,TableDsl]

  def execute(implicit conn: Conn[F]): F[Int]

  def asSql(implicit conn: Conn[F]): String

}

object UpdateQuery {

  case class Assignment[T](left: Expr[T], right: Expr[T])

  sealed trait UpdateSetClause

  implicit def liftToIterable[T](assignment: Assignment[T]): Iterable[Assignment[_]] =
    List(assignment)

}