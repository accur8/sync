package a8.shared.jdbcf.querydsl


import a8.shared.jdbcf.{Conn, SqlString}
import a8.shared.jdbcf.mapper.{Mapper, TableMapper}
import a8.shared.jdbcf.querydsl.QueryDsl.{ComponentJoin, Condition, Join, JoinImpl, PathCompiler, Path, OrderBy}

import scala.language.implicitConversions
import scala.language.existentials
import a8.shared.SharedImports._
import SqlString._
import zio._

case class SelectQueryImpl[T,U](
  tableDsl: U,
  outerMapper: TableMapper[T],
  where: Condition,
  orderBy: List[OrderBy],
  maxRows: Int = -1
)
  extends SelectQuery[T,U]
{

  implicit def implicitMapper: TableMapper[T] = outerMapper

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
          .toSome
      }

    lazy val fieldExprs: IndexedSeq[QueryDsl.FieldExpr[?]] = QueryDsl.fieldExprs(where)


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

    lazy val whereSqlNoAlias: SqlString = {
      val w = QueryDsl.asSql(where)(PathCompiler.empty)
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

  override def orderBy(orderFn: U=>OrderBy): SelectQuery[T,U] =
    copy(orderBy=List(orderFn(tableDsl)))

  override def orderBys(orderFn: U=>Iterable[OrderBy]): SelectQuery[T,U] =
    copy(orderBy=orderFn(tableDsl).toList)

  override def maxRows(count: Int): SelectQuery[T, U] =
    copy(maxRows=count)

  override def fetch(implicit conn: Conn): Task[T] =
    fetchOpt
      .flatMap {
        case None =>
          ZIO.fail(new RuntimeException(s"expected 1 record and got 0 -- ${sqlForErrorMessage}"))
        case Some(t) =>
          ZIO.succeed(t)
      }

  override def fetchOpt(implicit conn: Conn): Task[Option[T]] =
    select
      .flatMap {
        case Vector() =>
          ZIO.succeed(None)
        case Vector(t) =>
          ZIO.succeed(Some(t))
        case v =>
          ZIO.fail(new RuntimeException(s"expected 0 or 1 records and got ${v.size} -- ${sqlForErrorMessage} -- ${v}"))
      }

  override def select(implicit conn: Conn): Task[Vector[T]] =
    conn
      .query[T](sqlString)
      .select
      .map(_.toVector)

  override def streamingSelect(implicit conn: Conn): XStream[ T] =
    conn
      .streamingQuery[T](sqlString)
      .run

  def sqlForErrorMessage(implicit conn: Conn): String = {
    import conn._
    sqlString
      .compile
      .value
  }

}

