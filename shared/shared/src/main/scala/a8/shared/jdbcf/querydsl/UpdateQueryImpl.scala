package a8.shared.jdbcf.querydsl


import a8.shared.Chord
import a8.shared.jdbcf.Conn
import a8.shared.jdbcf.mapper.{Mapper, TableMapper}
import a8.shared.jdbcf.querydsl.QueryDsl.Condition
import cats.effect.Async
import QueryDsl.ch
import scala.language.existentials

case class UpdateQueryImpl[F[_]: Async, T,U](tableDsl: U, outerMapper: TableMapper[T], assignments: Iterable[UpdateQuery.Assignment[_]], where: Condition) extends UpdateQuery[F,U] {

  val delegate = SelectQueryImpl(tableDsl, outerMapper, where, Nil)

  def asSql(implicit conn: Conn[F]): String = {

    val qr = delegate.queryResolver

    val from = qr.joinSql

    val assignmentSql: Chord =
      assignments
        .map { assignment =>
          val left = QueryDsl.exprAsSql(assignment.left)(_ => Chord.empty)
          val right = QueryDsl.exprAsSql(assignment.right)(qr.joinToAliasMapper)
          left * ch.Equal * right
        }
        .mkChord(ch.CommaSpace)

    val joinSql: Option[Chord] = qr.joinSql.map(ch.From * _)

    val sqlChord =  ch.Update * outerMapper.tableName.asString * "as aa" *
       "set" * assignmentSql *
       joinSql.map(ch.Space ~ _).getOrElse(Chord.empty) ~
       "where" * qr.whereSql

    sqlChord.toString()

  }

  def where(whereFn: U => QueryDsl.Condition): UpdateQuery[F,U] = {
    copy(
      where =
        where match {
          case Condition.TRUE =>
            whereFn(tableDsl)
          case _ =>
            whereFn(tableDsl).and(where)
        }
    )
  }

  override def execute(implicit conn: Conn[F]): F[Int] = ???
}
