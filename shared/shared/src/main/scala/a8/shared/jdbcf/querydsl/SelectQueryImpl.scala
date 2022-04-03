package a8.shared.jdbcf.querydsl


import a8.shared.Chord
import a8.shared.jdbcf.Conn
import a8.shared.jdbcf.mapper.{Mapper, TableMapper}
import a8.shared.jdbcf.querydsl.QueryDsl.{ComponentJoin, Condition, Join, JoinImpl, Linker, OrderBy}
import cats.effect.Async

import scala.language.implicitConversions
import scala.language.existentials
import a8.shared.SharedImports._

case class SelectQueryImpl[F[_]: Async, T,U](tableDsl: U, outerMapper: TableMapper[T], where: Condition, orderBy: List[OrderBy], maxRows: Int = -1) extends SelectQuery[F,T,U] {

  def queryResolver =
    new QueryResolver

  class QueryResolver {

//    val resolvedMapper: ResolvedMapper[T, _] =
//      outerMapper.resolve
//
//    val mapperInternal = resolvedMapper.asMapperInternal

    lazy val orderBySql: Option[Chord] =
      if ( orderBy.isEmpty ) {
        None
      } else {
        Chord.str(orderBy.map(_.asSql).mkString(", ")).some
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

    lazy val aliasesByJoin: Map[Join, Chord] =
      joins
        .iterator
        .zipWithIndex
        .map { case (j, i) =>
          (j, Chord.str(('a' + i).toChar.toString * 2))
        }
        .toMap

    implicit def joinToAliasMapper(l: Linker): Chord = {
      l match {
        case c: ComponentJoin => aliasesByJoin(c.baseJoin) ~ "." ~ Chord.str(c.path.mkString)
        case j: Join => aliasesByJoin(j) ~ "."
      }
    }

    lazy val joinSql: Option[Chord] =
      aliasesByJoin
        .filter(_._1.depth > 0)
        .toIterable
        .toNonEmpty
        .map {
          _.iterator
            .collect { case (ji: JoinImpl, i) => ji -> i }
            .map { case (join, alias) =>
              val joinExpr = QueryDsl.asSql(join.joinExpr)(joinToAliasMapper)
              s"left join ${join.toTableMapper.tableName} ${alias} on ${joinExpr}"
            }
            .mkString("\n")
        }
        .map(Chord.str)

    lazy val whereSql: Chord = QueryDsl.asSql(where)(joinToAliasMapper)

//    def runSelect(maxRows: Int, streamingFetchSize: Option[Int] = None): Iterator[T] =
//      mapperInternal
//        .extendedQuery(
//          Some("aa"),
//          joinExpr = joinSql,
//          whereExpr = Some(whereSql),
//          orderByExpr = orderBySql,
//          maxRows = maxRows,
//          streamingFetchSize
//        )
//
    lazy val extendedQuerySql = (
      Chord.str(outerMapper.selectFieldsSqlPs("aa").toString)
        * "from" * outerMapper.tableName.asString * "as" * "aa"
        * joinSql.map(_ ~ " ").getOrElse(Chord.empty)
        ~ "where" * whereSql
        * orderBySql.map(Chord.str("order by") * _).getOrElse(Chord.empty)
    )

  }

//  override def fetchBox(implicit conn: ManagedConnection): Box[T] = {
//    val qr = queryResolver
//    qr.runSelect(1).nextOpt() ?~ s"no records returned from -- ${qr.extendedQuerySql}"
//  }
//
//  override def select(implicit conn: ManagedConnection): Iterator[T] =
//    queryResolver.runSelect(maxRows = maxRows)
//
//  override def streamingSelect(implicit conn: ManagedConnection): Iterator[T] =
//    queryResolver.runSelect(maxRows = -1, streamingFetchSize = Some(10000))
//
  override def asSql =
    queryResolver.extendedQuerySql.toString

  override def orderBy(orderFn: U=>OrderBy): SelectQuery[F,T,U] =
    copy(orderBy=List(orderFn(tableDsl)))

  override def orderBys(orderFn: U=>Iterable[OrderBy]): SelectQuery[F,T,U] =
    copy(orderBy=orderFn(tableDsl).toList)

  override def maxRows(count: Int): SelectQuery[F,T, U] =
    copy(maxRows=count)

  override def fetch(implicit conn: Conn[F]): F[T] = ???

  override def fetchOpt(implicit conn: Conn[F]): F[Option[T]] = ???

  override def select(implicit conn: Conn[F]): fs2.Stream[F, T] = ???

  override def streamingSelect(implicit conn: Conn[F]): fs2.Stream[F, T] = ???

  override def toVector(implicit conn: Conn[F]): F[Vector[T]] = ???

}
