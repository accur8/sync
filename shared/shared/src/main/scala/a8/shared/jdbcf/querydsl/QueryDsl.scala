package a8.shared.jdbcf.querydsl


import a8.shared.SharedImports._
import a8.shared.jdbcf.{ColumnName, Conn, RowWriter, SqlString}
import a8.shared.jdbcf.mapper.{ComponentMapper, KeyedTableMapper, Mapper, TableMapper}

import scala.language.{existentials, implicitConversions}
import a8.shared.jdbcf.SqlString.{DialectQuotedIdentifier, SqlStringer}
import a8.shared.jdbcf.querydsl.QueryDsl.Condition
import cats.data.Chain
import cats.effect.Async

/*

TODO support components

TODO support booleans for each database
  postgres as true | false
  iseries as ('Y','y','1','t','T') | ('N', 'n', '0', 'f', 'F')
  mysql as 1 | 0

TODO Unit tests, Unit tests, Unit tests

TODO support extended sql operations
  like
  exists
  support in with composable subqueries

TODO composable subqueries

TODO support joins in
  order by clause
  joins in joins

TODO support summation clauses

TODO support overriding the select fields

*/

object QueryDsl {

  object ss {
    val Period = SqlString.keyword(".")
    val Space = SqlString.keyword(" ")
    val Underscore = SqlString.keyword("_")
    val LeftParen = SqlString.keyword("(")
    val RightParen = SqlString.keyword(")")
    val IsNotNull = SqlString.keyword("is not null")
    val IsNull = SqlString.keyword("is null")
    val OneEqOne = SqlString.keyword("1 = 1")
    val OneNeOne = SqlString.keyword("1 <> 1")
    val And = SqlString.keyword("and")
    val Or = SqlString.keyword("or")
    val In = SqlString.keyword("in")
    val Null = SqlString.keyword("null")
    val Concat = SqlString.keyword("||")
    val NullWithParens = SqlString.keyword("(null)")
//    val QuestionMark = SqlString.keyword("?")
    val Comma = SqlString.keyword(",")
    val CommaSpace = SqlString.keyword(", ")
    val Equal = SqlString.keyword("=")
    val From = SqlString.keyword("from")
    val Update = SqlString.keyword("update")
    val Asc = SqlString.keyword("asc")
    val Desc = SqlString.keyword("desc")
    val Set = SqlString.keyword("set")
    val Where = SqlString.keyword("where")
    val NewLine = SqlString.keyword("\n")
  }

  val TRUE: Condition = Condition.TRUE
  val FALSE: Condition = Condition.FALSE

  case class And(left: Condition, right: Condition) extends Condition {
    override def isComposite: Boolean = true
  }
  case class Or(left: Condition, right: Condition) extends Condition {
    override def isComposite: Boolean = true
  }

  case class IsNull(left: Expr[_], not: Boolean) extends Condition

  case class StructuralEquality[A: ComponentMapper](linker: Path, component: Component[A], value: A) extends Condition {
    val mapper = implicitly[ComponentMapper[A]]
  }

  case class BooleanOperation[T](left: Expr[T], op: BooleanOperator, right: Expr[T]) extends Condition

  case class Constant[T: SqlStringer](value: T) extends Expr[T] {
    val sqlStringer = SqlStringer[T]
    val sqlString = sqlStringer.toSqlString(value)
//    def applyRowWriter = QueryDsl.applyRowWriter(value, RowWriter[T])
//    def applyRowWriterChain = Chain.one(applyRowWriter)
  }

  case class OptionConstant[T: SqlStringer](value: Option[Constant[T]]) extends Expr[T]

  implicit def optionToConstant[T: SqlStringer](value: Option[T]): OptionConstant[T] = {
    val oc = value.map(v => valueToConstant(v))
    OptionConstant(oc)
  }

  implicit def valueToConstant[T: SqlStringer](value: T) = Constant(value)

  implicit def byteToConstant(value: Byte): Constant[Byte] = valueToConstant(value)
  implicit def shortToConstant(value: Short): Constant[Short] = valueToConstant(value)
  implicit def intToConstant(value: Int): Constant[Int] = valueToConstant(value)
  implicit def longToConstant(value: Long): Constant[Long] = valueToConstant(value)

  implicit def floatToConstant(value: Float): Constant[Float] = valueToConstant(value)
  implicit def doubleToConstant(value: Double): Constant[Double] = valueToConstant(value)

  implicit def jlBigDecimalToConstant(value: java.math.BigDecimal): Constant[java.math.BigDecimal] = valueToConstant(value)
  implicit def bigDecimalToConstant(value: BigDecimal): Constant[BigDecimal] = valueToConstant(value)

  sealed trait FieldExpr[T] extends Expr[T] {
    def name: String
    def join: Path
    def resolvedComponentName: Boolean
  }

  case class In[T: SqlStringer](left: Expr[T], set: Iterable[Constant[T]]) extends Condition

  case class Field[T: SqlStringer](name: String, join: Path, resolvedComponentName: Boolean) extends FieldExpr[T] {
    if ( name.startsWith("addr") || name.startsWith("line") ) {
      toString
    }
  }
  case class NumericField[T: SqlStringer](name: String, join: Path, resolvedComponentName: Boolean) extends NumericExpr[T] with FieldExpr[T]

  case class Concat(left: Expr[_], right: Expr[_]) extends Expr[String]

  case class UnaryOperation[T: SqlStringer](op: UnaryOperator, value: Expr[T]) extends Expr[T]
  case class NumericOperation[T: SqlStringer](left: Expr[T], op: NumericOperator, right: Expr[T]) extends NumericExpr[T]

  sealed trait BooleanOperator {
    val asSql: SqlString
  }
  abstract class AbstractOperator(sqlStr: String) {
    val asSql: SqlString = SqlString.keyword(sqlStr)
  }

  sealed trait NumericOperator {
    val asSql: SqlString
  }

  sealed trait UnaryOperator {
    val asSql: SqlString
  }

  sealed trait BinaryOperator {
    val asSql: SqlString
  }

  object ops {

    object eq extends AbstractOperator("=") with BooleanOperator
    object ne extends AbstractOperator("<>") with BooleanOperator
    object gt extends AbstractOperator(">") with BooleanOperator
    object ge extends AbstractOperator(">=") with BooleanOperator
    object lt extends AbstractOperator("<") with BooleanOperator
    object le extends AbstractOperator("<=") with BooleanOperator

    object negate extends AbstractOperator("-") with UnaryOperator

    object concat extends AbstractOperator("||") with BinaryOperator

    object add extends AbstractOperator("+") with NumericOperator
    object subtract extends AbstractOperator("-") with NumericOperator
    object mult extends AbstractOperator("*") with NumericOperator
    object divide extends AbstractOperator("/") with NumericOperator

  }

  object Condition {
    object TRUE extends Condition
    object FALSE extends Condition
  }

  def fieldExprs(cond: Condition): IndexedSeq[FieldExpr[_]] =
    cond match {
      case se@ StructuralEquality(_, _, _) =>
        fieldExprs(generateStructuralComparison(se)(PathCompiler.empty))
      case Condition.TRUE =>
        IndexedSeq.empty
      case Condition.FALSE =>
        IndexedSeq.empty
      case And(l, r) =>
        fieldExprs(l) ++ fieldExprs(r)
      case Or(l,r) =>
        fieldExprs(l) ++ fieldExprs(r)
      case IsNull(e, _) =>
        fieldExprs(e)
      case In(e, _) =>
        fieldExprs(e)
      case BooleanOperation(l, _, r) =>
        fieldExprs(l) ++ fieldExprs(r)
    }

  def fieldExprs(expr: Expr[_]): IndexedSeq[FieldExpr[_]] =
    expr match {
      case fe: FieldExpr[_] =>
        IndexedSeq(fe)
      case _: Constant[_] =>
        IndexedSeq.empty
      case _: OptionConstant[_] =>
        IndexedSeq.empty
      case Concat(l, r) =>
        fieldExprs(l) ++ fieldExprs(r)
      case UnaryOperation(_, r) =>
        fieldExprs(r)
      case NumericOperation(l, _, r) =>
        fieldExprs(l) ++ fieldExprs(r)
    }

  sealed trait Condition {

    def isComposite: Boolean = false

    def and(right: Condition): Condition =
      And(this, right)

    def or(right: Condition): Condition =
      Or(this, right)

  }

  object PathCompiler {
    case object empty extends PathCompiler {
      override def alias(linker: Path): SqlString =
        SqlString.Empty
    }
  }


  trait PathCompiler {
    def alias(linker: Path): SqlString
    def prefix(linker: Path): String =
      linker match {
        case cj: ComponentJoin =>
          cj.path.mkString
        case _ =>
          ""
      }
  }

  sealed trait Path {
    def baseJoin: Join
    def columnName(suffix: ColumnName): ColumnName
  }

  sealed trait Join extends Path {
    /**
     * all joins in the chain including this one
     */
    def chain: List[Join]
    def depth: Int
    def baseJoin: Join = this
  }

  case object RootJoin extends Join {
    lazy val chain: List[Join] = List(this)
    def depth = 0
    def columnName(suffix: ColumnName) = suffix
  }

  case class JoinImpl(parent: Join, name: String, toTableMapper: TableMapper[_], joinExprFn: Join=>QueryDsl.Condition) extends Join {
    lazy val joinExpr = joinExprFn(this)
    override def chain = this :: parent.chain
    def depth = parent.depth + 1
    def columnName(suffix: ColumnName) = suffix
  }

  case class ComponentJoin(name: String, parent: Path) extends Path {
    val nameAsColumnName = ColumnName(name)
    lazy val path: Vector[String] = name +: parentPath
    lazy val parentPath: Vector[String] =
      parent match {
        case pj: ComponentJoin =>
          pj.path
        case _ =>
          Vector.empty
      }
    lazy val baseJoin: Join =
      parent match {
        case pj: ComponentJoin => pj.baseJoin
        case j: Join => j
      }
    def columnName(suffix: ColumnName) =
      parent.columnName(nameAsColumnName ~ suffix)
  }

  def createJoin[A,B](
    parent: Join,
    name: String,
    fromTableDsl: A,
    toTableDsl: Join=>B,
    toTableMapper: TableMapper[_]
  ) (
    joinExprFn: (A, B) => QueryDsl.Condition
  ): Join = {
    JoinImpl(parent, name, toTableMapper, join=>joinExprFn(fromTableDsl, toTableDsl(join)))
  }


  def field[T: SqlStringer](name: String, join: Path = RootJoin): Field[T] =
    Field[T](name, join, false)

  def numericField[T: SqlStringer](name: String, join: Path = RootJoin): NumericField[T] =
    NumericField[T](name, join, false)

  sealed abstract class Expr[T: SqlStringer] {

    def :=(value: Expr[T]): UpdateQuery.Assignment[T] =
      UpdateQuery.Assignment(this, value)

    def ||(value: Expr[_]): Expr[String] =
      Concat(this, value)

    def ===(value: Expr[T]): Condition =
      BooleanOperation(this, ops.eq, value)

    def eq_(value: Expr[T]): Condition =
      BooleanOperation(this, ops.eq, value)

    def <>(value: Expr[T]): Condition =
      BooleanOperation(this, ops.ne, value)

    def >(value: Expr[T]): Condition =
      BooleanOperation(this, ops.gt, value)

    def >=(value: Expr[T]): Condition =
      BooleanOperation(this, ops.ge, value)

    def <(value: Expr[T]): Condition =
      BooleanOperation(this, ops.lt, value)

    def <=(value: Expr[T]): Condition =
      BooleanOperation(this, ops.le, value)

    def is_null: Condition =
      IsNull(this, false)

    def is_not_null: Condition =
      IsNull(this, true)

    def in(set: Iterable[T]) =
      In(this, set.map(i=>Constant(i)))

    def asc = OrderBy(this, true)
    def desc = OrderBy(this, false)

  }


  sealed abstract class NumericExpr[T: SqlStringer] extends Expr[T] {

    def unary_- : Expr[T] =
      UnaryOperation(ops.negate, this)

    def +(value: Expr[T]): NumericExpr[T] =
      NumericOperation(this, ops.add, value)

    def -(value: Expr[T]): Expr[T] =
      NumericOperation(this, ops.subtract, value)

    def *(value: Expr[T]): Expr[T] =
      NumericOperation(this, ops.mult, value)

    def /(value: Expr[T]): Expr[T] =
      NumericOperation(this, ops.divide, value)

  }

  def parens(cond: Condition)(implicit alias: PathCompiler = PathCompiler.empty): SqlString = {
    if ( cond.isComposite )
      ss.LeftParen ~ asSql(cond) ~ ss.RightParen
    else
      asSql(cond)
  }

  def noAliasAliasMapper(j: Path): SqlString = SqlString.Empty

  trait StructuralProperty[A] {
    def booleanOp(linker: QueryDsl.Path, a: A): QueryDsl.Condition
  }

  def generateStructuralComparison[A](structuralEquality: StructuralEquality[A])(implicit alias: PathCompiler): Condition = {
    structuralEquality
      .mapper
      .structuralEquality(structuralEquality.linker, structuralEquality.value)
  }

  def asSql(cond: Condition)(implicit alias: PathCompiler): SqlString = {
    import SqlString._
    cond match {
      case se@ StructuralEquality(_, _, _) =>
        asSql(generateStructuralComparison(se))
      case BooleanOperation(l, ops.ne, OptionConstant(None)) =>
        exprAsSql(l) * ss.IsNotNull
      case BooleanOperation(l, ops.eq, OptionConstant(None)) =>
        exprAsSql(l) * ss.IsNull
      case Condition.TRUE =>
        ss.OneEqOne
      case Condition.FALSE =>
        ss.OneNeOne
      case and: And =>
        asSql(and.left) ~*~ ss.And ~*~ asSql(and.right)
      case or: Or =>
        asSql(or.left) ~*~ ss.Or ~*~ asSql(or.right)
      case op: BooleanOperation[_] =>
        exprAsSql(op.left) ~*~ op.op.asSql ~*~ exprAsSql(op.right)
      case is: IsNull =>
        exprAsSql(is.left) ~*~ (if (is.not) ss.IsNotNull else ss.IsNull)
      case in: In[_] if in.set.isEmpty =>
        exprAsSql(in.left) ~*~ ss.In ~*~ ss.NullWithParens
      case in: In[_] =>
        exprAsSql(in.left) ~*~ ss.In ~*~ ss.LeftParen ~ in.set.map(exprAsSql).mkSqlString(ss.Comma) ~ ss.RightParen
    }
  }

  def exprAsSql[T](expr: Expr[T])(implicit compiler: PathCompiler): SqlString = expr match {
    case fe: FieldExpr[T] =>
      val a = compiler.alias(fe.join)
      val name =
        if ( fe.resolvedComponentName )
          fe.name
        else
          compiler.prefix(fe.join) + fe.name
      a ~ DialectQuotedIdentifier(name)
    case OptionConstant(Some(c)) =>
      exprAsSql(c)
    case constant: Constant[T] =>
      constant.sqlString
    case c: Concat =>
      exprAsSql(c.left) ~*~ ss.Concat ~*~ exprAsSql(c.right)
    case no: NumericOperation[T] =>
      exprAsSql(no.left) ~*~ no.op.asSql ~*~ exprAsSql(no.right)
    case OptionConstant(None) =>
      ss.Null
    case UnaryOperation(op, e) =>
      op.asSql ~ exprAsSql(e)
  }

  object OrderBy {
    implicit def exprToOrderBy[T](f: Expr[T]) = OrderBy(f)
  }

  case class OrderBy(expr: Expr[_], ascending: Boolean = true) {
    def asc = copy(ascending=true)
    def desc = copy(ascending=false)
    def asSql(implicit alias: PathCompiler): SqlString =
      QueryDsl.exprAsSql(expr) ~*~ (if (ascending) ss.Asc else ss.Desc)
  }

  abstract class Component[A](join: QueryDsl.Path) {
    def ===(right: A)(implicit mapper: ComponentMapper[A]): Condition =
      StructuralEquality(join, this, right)
  }

}



class QueryDsl[T, TableDsl, K](
  val keyedTableMapper: KeyedTableMapper[T, K],
  val tableDsl: TableDsl
) {

  implicit def keyedTableMapper0 = keyedTableMapper

  def query[F[_]: Async](whereFn: TableDsl => QueryDsl.Condition): SelectQuery[F, T, TableDsl] =
    SelectQueryImpl(tableDsl, keyedTableMapper, whereFn(tableDsl), Nil)

  def update[F[_]: Async](set: TableDsl => Iterable[UpdateQuery.Assignment[_]]): UpdateQuery[F, TableDsl] =
    UpdateQueryImpl(
      tableDsl = tableDsl,
      outerMapper = keyedTableMapper,
      assignments = set(tableDsl),
      where = Condition.TRUE
    )

  def updateRow[F[_]: Async](row: T)(where: TableDsl => QueryDsl.Condition)(implicit conn: Conn[F]): F[Option[T]] = {
    val selectQuery = SelectQueryImpl(tableDsl, keyedTableMapper, where(tableDsl), Nil)
    conn
      .updateRowWhere(row)(selectQuery.queryResolver.whereSql)
  }



}

