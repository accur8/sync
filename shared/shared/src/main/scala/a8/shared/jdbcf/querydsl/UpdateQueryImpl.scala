package a8.shared.jdbcf.querydsl


import a8.shared.jdbcf.{Conn, SqlString}
import a8.shared.jdbcf.mapper.{Mapper, TableMapper}
import QueryDsl.{Condition, PathCompiler, ss}
import cats.effect.Async

import scala.language.existentials

case class UpdateQueryImpl[F[_]: Async, T,U](
  tableDsl: U,
  outerMapper: TableMapper[T],
  assignments: Iterable[UpdateQuery.Assignment[_]],
  where: Condition
)
  extends UpdateQuery[F,U]
{

  val delegate = SelectQueryImpl(tableDsl, outerMapper, where, Nil)

  lazy val sqlString: SqlString = {
    import SqlString._

    val qr = delegate.queryResolver

    val from = qr.joinSql

    val assignmentSql: SqlString =
      assignments
        .map { assignment =>
          val left = QueryDsl.exprAsSql(assignment.left)(PathCompiler.empty)
          val right = QueryDsl.exprAsSql(assignment.right)(qr.linkCompiler)
          left * ss.Equal * right
        }
        .mkSqlString(ss.CommaSpace)

    val joinSql: Option[SqlString] = qr.joinSql.map(ss.From * _)

    ss.Update * outerMapper.tableName * keyword("as aa") *
       ss.Set * assignmentSql *
       joinSql.map(ss.Space ~ _).getOrElse(SqlString.Empty) ~
       keyword("where") * qr.whereSql

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

  override def execute(implicit conn: Conn[F]): F[Int] =
    conn.update(sqlString)
}
