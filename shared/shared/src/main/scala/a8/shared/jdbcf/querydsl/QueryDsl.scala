package a8.shared.jdbcf.querydsl

import a8.shared.Chord
import a8.shared.jdbcf.{RowWriter, SqlString}
import a8.shared.jdbcf.mapper.{ComponentMapper, Mapper, TableMapper}

import scala.language.{existentials, implicitConversions}
import Chord._
import a8.shared.jdbcf.SqlString.SqlStringer
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

  object ch {
    val Space = Chord.str(" ")
    val Underscore = Chord.str("_")
    val LeftParen = Chord.str("(")
    val RightParen = Chord.str(")")
    val IsNotNull = Chord.str("is not null")
    val IsNull = Chord.str("is null")
    val OneEqOne = Chord.str("1 = 1")
    val OneNeOne = Chord.str("1 <> 1")
    val And = Chord.str("and")
    val Or = Chord.str("or")
    val In = Chord.str("in")
    val Null = Chord.str("null")
    val Concat = Chord.str("||")
    val NullWithParens = Chord.str("(null)")
//    val QuestionMark = Chord.str("?")
    val Comma = Chord.str(",")
    val CommaSpace = Chord.str(", ")
    val Equal = Chord.str("=")
    val From = Chord.str("from")
    val Update = Chord.str("update")
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

  case class StructuralEquality[A: ComponentMapper](linker: Linker, component: Component[A], value: A) extends Condition {
    val mapper = implicitly[ComponentMapper[A]]
  }

  case class BooleanOperation[T](left: Expr[T], op: BooleanOperator, right: Expr[T]) extends Condition

  case class Constant[T: SqlStringer](value: T) extends Expr[T] {
    val sqlStringer = SqlStringer[T]
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
    def join: Linker
  }

  case class In[T: SqlStringer](left: Expr[T], set: Iterable[Constant[T]]) extends Condition

  case class Field[T: SqlStringer](name: String, join: Linker) extends FieldExpr[T]
  case class NumericField[T: SqlStringer](name: String, join: Linker) extends NumericExpr[T] with FieldExpr[T]

  case class Concat(left: Expr[_], right: Expr[_]) extends Expr[String]

  case class UnaryOperation[T: SqlStringer](op: UnaryOperator, value: Expr[T]) extends Expr[T]
  case class NumericOperation[T: SqlStringer](left: Expr[T], op: NumericOperator, right: Expr[T]) extends NumericExpr[T]

  sealed trait BooleanOperator {
    val asSql: Chord
  }
  abstract class AbstractOperator(sqlStr: String) {
    val asSql: Chord = Chord.str(sqlStr)
  }

  sealed trait NumericOperator {
    val asSql: Chord
  }

  sealed trait UnaryOperator {
    val asSql: Chord
  }

  sealed trait BinaryOperator {
    val asSql: Chord
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
        fieldExprs(generateStructuralComparison(se)(_ => Chord.empty))
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

  sealed trait Linker {
    def baseJoin: Join
  }

  sealed trait Join extends Linker {
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
  }

  case class JoinImpl(parent: Join, name: String, toTableMapper: TableMapper[_], joinExprFn: ()=>QueryDsl.Condition) extends Join {
    lazy val joinExpr = joinExprFn()
    override def chain = this :: parent.chain
    def depth = parent.depth + 1
  }

  case class ComponentJoin(name: String, parent: Linker) extends Linker {
    def path = name :: parentPath
    def parentPath: List[String] =
      parent match {
        case pj: ComponentJoin => pj.path
        case _ => Nil
      }
    lazy val baseJoin: Join =
      parent match {
        case pj: ComponentJoin => pj.baseJoin
        case j: Join => j
      }
  }

  def createJoin[A,B](
    parent: Join,
    name: String,
    fromTableDsl: A,
    toTableDsl: ()=>B,
    toTableMapper: TableMapper[_]
  ) (
    joinExprFn: (A, B) => QueryDsl.Condition
  ): Join = {
    JoinImpl(parent, name, toTableMapper, ()=>joinExprFn(fromTableDsl, toTableDsl()))
  }


  def field[T: SqlStringer](name: String, join: Linker = RootJoin): Field[T] =
    Field[T](name, join)

  def numericField[T: SqlStringer](name: String, join: Linker = RootJoin): NumericField[T] =
    NumericField[T](name, join)

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

  def parens(cond: Condition)(implicit alias: Linker => Chord = _ => Chord.empty): Chord = {
    if ( cond.isComposite )
      ch.LeftParen ~ asSql(cond) ~ ch.RightParen
    else
      asSql(cond)
  }

  def noAliasAliasMapper(j: Linker): Chord = Chord.empty

  trait StructuralProperty[A] {
    def booleanOp(linker: QueryDsl.Linker, a: A): QueryDsl.Condition
  }

  def generateStructuralComparison[A](structuralEquality: StructuralEquality[A])(implicit alias: Linker => Chord): Condition = {
    structuralEquality
      .mapper
      .structuralEquality(structuralEquality.linker, structuralEquality.value)
  }

  def asSql(cond: Condition)(implicit alias: Linker => Chord): Chord =
    cond match {
      case se@ StructuralEquality(_, _, _) =>
        asSql(generateStructuralComparison(se))
      case BooleanOperation(l, ops.ne, OptionConstant(None)) =>
        exprAsSql(l) * ch.IsNotNull
      case BooleanOperation(l, ops.eq, OptionConstant(None)) =>
        exprAsSql(l) * ch.IsNull
      case Condition.TRUE =>
        ch.OneEqOne
      case Condition.FALSE =>
        ch.OneNeOne
      case and: And =>
        asSql(and.left) ~*~ ch.And ~*~ asSql(and.right)
      case or: Or =>
        asSql(or.left) ~*~ ch.Or ~*~ asSql(or.right)
      case op: BooleanOperation[_] =>
        exprAsSql(op.left) ~*~ op.op.asSql ~*~ exprAsSql(op.right)
      case is: IsNull =>
        exprAsSql(is.left) ~*~ (if (is.not) ch.IsNotNull else ch.IsNull)
      case in: In[_] if in.set.isEmpty =>
        exprAsSql(in.left) ~*~ ch.In ~*~ "(null)"
      case in: In[_] =>
        exprAsSql(in.left) ~*~ ch.In ~*~ ch.LeftParen ~ in.set.map(exprAsSql).mkChord(ch.Comma) ~ ")"
    }

  def exprAsSql[T](expr: Expr[T])(implicit alias: Linker => Chord): Chord = expr match {
    case fe: FieldExpr[T] =>
      val a = alias(fe.join)
      if ( fe.join.isInstanceOf[ComponentJoin] ) a ~ fe.name
      else a ~ fe.name
    case OptionConstant(Some(c)) =>
      exprAsSql(c)
    case constant: Constant[T] =>
      Chord.str(constant.sqlStringer.toSqlString(constant.value).toString)
    case c: Concat =>
      exprAsSql(c.left) ~*~ "||" ~*~ exprAsSql(c.right)
    case no: NumericOperation[T] =>
      exprAsSql(no.left) ~*~ no.op.asSql ~*~ exprAsSql(no.right)
    case OptionConstant(None) =>
      ch.Null
    case UnaryOperation(op, e) =>
      op.asSql ~ exprAsSql(e)
  }

  object OrderBy {
    implicit def exprToOrderBy[T](f: Expr[T]) = OrderBy(f)
  }

  case class OrderBy(expr: Expr[_], ascending: Boolean = true) {
    def asc = copy(ascending=true)
    def desc = copy(ascending=false)
    def asSql(implicit alias: Linker => Chord) = QueryDsl.exprAsSql(expr) ~*~ (if (ascending) "ASC" else "DESC")
  }

  abstract class Component[A](join: QueryDsl.Linker) {
    def ===(right: A)(implicit mapper: ComponentMapper[A]): Condition =
      StructuralEquality(join, this, right)
  }

}



class QueryDsl[T, TableDsl](
  val mapper: TableMapper[T],
  val tableDsl: TableDsl
) {

  def query[F[_]: Async](whereFn: TableDsl => QueryDsl.Condition): SelectQuery[F, T, TableDsl] =
    SelectQueryImpl(tableDsl, mapper, whereFn(tableDsl), Nil)

  def update[F[_]: Async](set: TableDsl => Iterable[UpdateQuery.Assignment[_]]): UpdateQuery[F, TableDsl] =
    UpdateQueryImpl(
      tableDsl = tableDsl,
      outerMapper = mapper,
      assignments = set(tableDsl),
      where = Condition.TRUE
    )

}

