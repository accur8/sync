package a8.shared.jdbcf.querydsl


import a8.shared.jdbcf.{Conn, SqlString}
import a8.shared.jdbcf.mapper.{Mapper, TableMapper}
import a8.shared.jdbcf.querydsl.QueryDsl.{ComponentJoin, Condition, Join, JoinImpl, PathCompiler, Path, OrderBy}
import cats.effect.Async

import scala.language.implicitConversions
import scala.language.existentials
import a8.shared.SharedImports._
import SqlString._

case class SelectQueryImpl[F[_]: Async, T,U](
  tableDsl: U,
  outerMapper: TableMapper[T],
  where: Condition,
  orderBy: List[OrderBy],
  maxRows: Int = -1
)
  extends SelectQuery[F,T,U]
{

  implicit def implicitMapper = outerMapper

  lazy val queryResolver =
    new QueryResolver

  class QueryResolver {

    lazy val orderBySql: Option[SqlString] =
      if ( orderBy.isEmpty ) {
        None
      } else {
        orderBy
          .map(_.asSql)
          .mkSqlString(CommaSpace)
          .some
      }

    lazy val fieldExprs = QueryDsl.fieldExprs(where)


    lazy val joins: Iterable[Join] = {
      val temp =
        fieldExprs
          .iterator
          .flatMap(_.join.baseJoin.chain)
          .toSet
          .toIndexedSeq
      temp.sortBy(_.depth)
    }

    lazy val aliasesByJoin: Map[Join, SqlString] =
      joins
        .iterator
        .zipWithIndex
        .map { case (j, i) =>
          (j, SqlString.keyword(('a' + i).toChar.toString * 2))
        }
        .toMap

    implicit val linkCompiler: PathCompiler =
      new PathCompiler {
        override def alias(linker: Path): SqlString = {
          linker match {
            case c: ComponentJoin =>
              aliasesByJoin(c.baseJoin) ~ QueryDsl.ss.Period
            case j: Join =>
              aliasesByJoin(j) ~ QueryDsl.ss.Period
          }
        }
      }

    lazy val joinSql: Option[SqlString] =
      aliasesByJoin
        .filter(_._1.depth > 0)
        .toIterable
        .toNonEmpty
        .map {
          _.iterator
            .collect { case (ji: JoinImpl, i) => ji -> i }
            .map { case (join, alias) =>
              val joinExpr = QueryDsl.asSql(join.joinExpr)(linkCompiler)
              sql"left join ${join.toTableMapper.tableName} ${alias} on ${joinExpr}"
            }
            .mkSqlString(keyword("\n"))
        }

    lazy val whereSql: SqlString = {
      val w = QueryDsl.asSql(where)
      w
    }

    lazy val extendedQuerySql: SqlString = {
      val selectFields = outerMapper.selectFieldsSql("aa")
      val join = joinSql.map(js => sql"${js} ").getOrElse(SqlString.Empty)
      val orderBy = orderBySql.map(ob => sql" order by ${ob}").getOrElse(SqlString.Empty)
      sql"""${selectFields} from ${outerMapper.tableName} as aa ${join}where ${whereSql}${orderBy}"""
    }

  }


  override def sqlString: SqlString =
    queryResolver.extendedQuerySql

  override def orderBy(orderFn: U=>OrderBy): SelectQuery[F,T,U] =
    copy(orderBy=List(orderFn(tableDsl)))

  override def orderBys(orderFn: U=>Iterable[OrderBy]): SelectQuery[F,T,U] =
    copy(orderBy=orderFn(tableDsl).toList)

  override def maxRows(count: Int): SelectQuery[F,T, U] =
    copy(maxRows=count)

  override def fetch(implicit conn: Conn[F]): F[T] =
    fetchOpt
      .flatMap {
        case None =>
          Async[F].raiseError(new RuntimeException(s"expected 1 record and got 0 -- ${sqlForErrorMessage}"))
        case Some(t) =>
          Async[F].pure(t)
      }

  override def fetchOpt(implicit conn: Conn[F]): F[Option[T]] =
    select
      .flatMap {
        case Vector() =>
          Async[F].pure(None)
        case Vector(t) =>
          Async[F].pure(t.some)
        case v =>
          Async[F].raiseError(new RuntimeException(s"expected 0 or 1 records and got ${v.size} -- ${sqlForErrorMessage} -- ${v}"))
      }

  override def select(implicit conn: Conn[F]): F[Vector[T]] =
    conn
      .query[T](sqlString)
      .select
      .map(_.toVector)

  override def streamingSelect(implicit conn: Conn[F]): fs2.Stream[F, T] =
    conn
      .streamingQuery[T](sqlString)
      .run

  def sqlForErrorMessage(implicit conn: Conn[F]): String = {
    import conn._
    sqlString
      .compile
      .value
  }

}

