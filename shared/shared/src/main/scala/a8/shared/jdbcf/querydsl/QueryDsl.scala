package a8.shared.jdbcf.querydsl


import a8.shared.jdbcf.{ColumnName, Conn, SqlString}
import a8.shared.jdbcf.mapper.{ComponentMapper, KeyedTableMapper, TableMapper}

import scala.language.{existentials, implicitConversions}
import a8.shared.jdbcf.SqlString.{DialectQuotedIdentifier, SqlStringer}
import a8.shared.jdbcf.querydsl.QueryDsl.Condition
import a8.shared.json.ast.JsVal
import cats.data.Chain

import a8.shared.zreplace._

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
    val Period: SqlString = SqlString.keyword(".")
    val Space: SqlString = SqlString.keyword(" ")
    val Underscore: SqlString = SqlString.keyword("_")
    val LeftParen: SqlString = SqlString.keyword("(")
    val RightParen: SqlString = SqlString.keyword(")")
    val IsNotNull: SqlString = SqlString.keyword("is not null")
    val IsNull: SqlString = SqlString.keyword("is null")
    val OneEqOne: SqlString = SqlString.keyword("1 = 1")
    val OneNeOne: SqlString = SqlString.keyword("1 <> 1")
    val And: SqlString = SqlString.keyword("and")
    val Or: SqlString = SqlString.keyword("or")
    val In: SqlString = SqlString.keyword("in")
    val Null: SqlString = SqlString.keyword("null")
    val Concat: SqlString = SqlString.keyword("||")
    val NullWithParens: SqlString = SqlString.keyword("(null)")
//    val QuestionMark = SqlString.keyword("?")
    val Comma: SqlString = SqlString.keyword(",")
    val CommaSpace: SqlString = SqlString.keyword(", ")
    val Equal: SqlString = SqlString.keyword("=")
    val From: SqlString = SqlString.keyword("from")
    val Update: SqlString = SqlString.keyword("update")
    val Asc: SqlString = SqlString.keyword("asc")
    val Desc: SqlString = SqlString.keyword("desc")
    val Set: SqlString = SqlString.keyword("set")
    val Where: SqlString = SqlString.keyword("where")
    val NewLine: SqlString = SqlString.keyword("\n")
  }

  val TRUE: Condition = Condition.TRUE
  val FALSE: Condition = Condition.FALSE

  case class And(left: Condition, right: Condition) extends Condition {
    override def isComposite: Boolean = true
  }
  case class Or(left: Condition, right: Condition) extends Condition {
    override def isComposite: Boolean = true
  }

  case class IsNull(left: Expr[?], not: Boolean) extends Condition

  case class StructuralEquality[A: ComponentMapper](linker: Path, component: Component[A], values: Iterable[A]) extends Condition {
    val mapper: ComponentMapper[A] = implicitly[ComponentMapper[A]]
  }

  case class InClause(left: IndexedSeq[Expr[?]], right: IndexedSeq[IndexedSeq[Expr[?]]]) extends Condition

  case class StructuralInClause[A: ComponentMapper](linker: Path, component: Component[A], values: IndexedSeq[A]) extends Condition {
    val mapper: ComponentMapper[A] = implicitly[ComponentMapper[A]]
  }

  case class BooleanOperation[T](left: Expr[T], op: BooleanOperator, right: Expr[T]) extends Condition

  case class Constant[T: SqlStringer](value: T) extends Expr[T] {
    val sqlStringer: SqlStringer[T] = SqlStringer[T]
    val sqlString: SqlString = sqlStringer.toSqlString(value)
//    def applyRowWriter = QueryDsl.applyRowWriter(value, RowWriter[T])
//    def applyRowWriterChain = Chain.one(applyRowWriter)
  }

  case class OptionConstant[T: SqlStringer](value: Option[Constant[T]]) extends Expr[T]

  implicit def optionToConstant[T: SqlStringer](value: Option[T]): OptionConstant[T] = {
    val oc = value.map(v => valueToConstant(v))
    OptionConstant(oc)
  }

  implicit def valueToConstant[T: SqlStringer](value: T): Constant[T] = Constant(value)

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
  }
  case class NumericField[T: SqlStringer](name: String, join: Path, resolvedComponentName: Boolean) extends NumericExpr[T] with FieldExpr[T]

  case class Concat(left: Expr[?], right: Expr[?]) extends Expr[String]

  case class Opt[A : SqlStringer](expr: Expr[A]) extends Expr[Option[A]]

  case class UnaryOperation[T: SqlStringer](op: UnaryOperator, value: Expr[T]) extends Expr[T]
  case class NumericOperation[T: SqlStringer](left: Expr[T], op: NumericOperator, right: Expr[T]) extends NumericExpr[T]

  object BooleanOperator {
    given [A <: BooleanOperator, B <: BooleanOperator]: CanEqual[A,B] = CanEqual.derived
  }
  sealed trait BooleanOperator {
    val asSql: SqlString
  }
  object AbstractOperator {
    given [A <: AbstractOperator, B <: AbstractOperator]: CanEqual[A,B] = CanEqual.derived
  }
  abstract class AbstractOperator(sqlStr: String) {
    val asSql: SqlString = SqlString.keyword(sqlStr)
  }

  object NumericOperator {
    given[A <: NumericOperator, B <: NumericOperator]: CanEqual[A, B] = CanEqual.derived
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

  object Ops {

    object Equal extends AbstractOperator("=") with BooleanOperator
    object NotEqual extends AbstractOperator("<>") with BooleanOperator
    object GreaterThan extends AbstractOperator(">") with BooleanOperator
    object GreaterThanOrEqual extends AbstractOperator(">=") with BooleanOperator
    object LesserThan extends AbstractOperator("<") with BooleanOperator
    object LesserThanOrEqual extends AbstractOperator("<=") with BooleanOperator

    object Negate extends AbstractOperator("-") with UnaryOperator

    object Concat extends AbstractOperator("||") with BinaryOperator

    object Add extends AbstractOperator("+") with NumericOperator
    object Subtract extends AbstractOperator("-") with NumericOperator
    object Mult extends AbstractOperator("*") with NumericOperator
    object Divide extends AbstractOperator("/") with NumericOperator

  }

  object Condition {
    object TRUE extends Condition
    object FALSE extends Condition
    given [A <: Condition, B <: Condition]: CanEqual[A,B] = CanEqual.derived
  }

  case class Parens(inside: Condition) extends Condition

  def fieldExprs(cond: Condition): IndexedSeq[FieldExpr[?]] =
    cond match {
      case ic@ InClause(left, right) =>
        left.flatMap(fieldExprs) ++ right.flatMap(_.flatMap(fieldExprs))
      case Parens(c) =>
        fieldExprs(c)
      case se@ StructuralEquality(_, _, _) =>
        fieldExprs(generateStructuralComparison(se)(using PathCompiler.empty))
      case sic@ StructuralInClause(_, _, _) =>
        fieldExprs(generateStructuralInClause(sic)(using PathCompiler.empty))
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

  def fieldExprs(expr: Expr[?]): IndexedSeq[FieldExpr[?]] =
    expr match {
      case Opt(e) =>
        fieldExprs(e)
      case fe: FieldExpr[?] =>
        IndexedSeq(fe)
      case _: Constant[?] =>
        IndexedSeq.empty
      case _: OptionConstant[?] =>
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

    def isAnd: Boolean = this.isInstanceOf[And]
    def isOr: Boolean= this.isInstanceOf[Or]

    infix def and(right: Condition): Condition =
      And(safeParens(this, true), safeParens(right, true))

    infix def or(right: Condition): Condition =
      Or(safeParens(this, false), safeParens(right, false))

  }

  private def safeParens(cond: Condition, and: Boolean): Condition =
    if ( cond.isOr && and ) {
      Parens(cond)
    } else if ( cond.isAnd && !and ) {
      Parens(cond)
    } else {
      cond
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

  object Path {
    given [A <: Path, B <: Path]: CanEqual[A,B] = CanEqual.derived
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

  case class JoinImpl(parent: Join, name: String, toTableMapper: TableMapper[?], joinExprFn: Join=>QueryDsl.Condition) extends Join {
    lazy val joinExpr: Condition = joinExprFn(this)
    override def chain: List[Join] = this :: parent.chain
    def depth: Int = parent.depth + 1
    def columnName(suffix: ColumnName) = suffix
  }

  case class ComponentJoin(name: String, parent: Path) extends Path {
    val nameAsColumnName: ColumnName = ColumnName(name)
    lazy val path: Vector[String] = name +: parentPath
    lazy val parentPath: Vector[String] =
      parent match {
        case pj: ComponentJoin =>
          pj.path
        case _ =>
          Vector.empty[String]
      }
    lazy val baseJoin: Join =
      parent match {
        case pj: ComponentJoin => pj.baseJoin
        case j: Join => j
      }
    def columnName(suffix: ColumnName): ColumnName =
      parent.columnName(nameAsColumnName ~ suffix)
  }

  def createJoin[A,B](
    parent: Join,
    name: String,
    fromTableDsl: A,
    toTableDsl: Join=>B,
    toTableMapper: TableMapper[?]
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

    def ||(value: Expr[?]): Expr[String] =
      Concat(this, value)

    def ===(value: Expr[T]): Condition =
      BooleanOperation(this, Ops.Equal, value)

    def =:=(value: Expr[Option[T]]): Condition =
      BooleanOperation(Opt(this), Ops.Equal, value)

    def eq_(value: Expr[T]): Condition =
      BooleanOperation(this, Ops.Equal, value)

    def <>(value: Expr[T]): Condition =
      BooleanOperation(this, Ops.NotEqual, value)

    def >(value: Expr[T]): Condition =
      BooleanOperation(this, Ops.GreaterThan, value)

    def >=(value: Expr[T]): Condition =
      BooleanOperation(this, Ops.GreaterThanOrEqual, value)

    def <(value: Expr[T]): Condition =
      BooleanOperation(this, Ops.LesserThan, value)

    def <=(value: Expr[T]): Condition =
      BooleanOperation(this, Ops.LesserThanOrEqual, value)

    def is_null: Condition =
      IsNull(this, false)

    def is_not_null: Condition =
      IsNull(this, true)

    def in(set: Iterable[T]): In[T] =
      In(this, set.map(i=>Constant(i)))

    def asc: OrderBy = OrderBy(this, true)
    def desc: OrderBy = OrderBy(this, false)

    def opt: Expr[Option[T]] =
      Opt(this)

  }


  sealed abstract class NumericExpr[T: SqlStringer] extends Expr[T] {

    def unary_- : Expr[T] =
      UnaryOperation(Ops.Negate, this)

    def +(value: Expr[T]): NumericExpr[T] =
      NumericOperation(this, Ops.Add, value)

    def -(value: Expr[T]): Expr[T] =
      NumericOperation(this, Ops.Subtract, value)

    def *(value: Expr[T]): Expr[T] =
      NumericOperation(this, Ops.Mult, value)

    def /(value: Expr[T]): Expr[T] =
      NumericOperation(this, Ops.Divide, value)

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

  def generateStructuralComparison[A](structuralEquality: StructuralEquality[A])(implicit alias: PathCompiler): Condition =
    structuralEquality
      .mapper
      .structuralEquality(structuralEquality.linker, structuralEquality.values)

  def generateStructuralInClause[A](inClause: StructuralInClause[A])(implicit alias: PathCompiler): InClause =
    inClause
      .mapper
      .inClause(inClause.linker, inClause.values)

  def asSql(cond: Condition)(implicit alias: PathCompiler): SqlString = {
    import SqlString._
    cond match {
      case InClause(left, right) =>
        def tupleup(values: IndexedSeq[Expr[?]]): SqlString =
          values.size match {
            case 1 =>
              exprAsSql(values.head)
            case _ =>
              ss.LeftParen ~ values.map(l => exprAsSql(l.asInstanceOf[Expr[Any]])).mkSqlString(ss.CommaSpace) ~ ss.RightParen
          }
        tupleup(left) * ss.In * ss.LeftParen ~ right.map(tupleup).mkSqlString(CommaSpace) ~ ss.RightParen
      case Parens(c) =>
        ss.LeftParen ~ asSql(c) ~ ss.RightParen
      case se@ StructuralEquality(_, _, _) =>
        asSql(generateStructuralComparison(se))
      case sic@ StructuralInClause(_, _, _) =>
        asSql(generateStructuralInClause(sic))
      case BooleanOperation(l, Ops.NotEqual, OptionConstant(None)) =>
        exprAsSql(l) * ss.IsNotNull
      case BooleanOperation(l, Ops.Equal, OptionConstant(None)) =>
        exprAsSql(l) * ss.IsNull
      case Condition.TRUE =>
        ss.OneEqOne
      case Condition.FALSE =>
        ss.OneNeOne
      case and: And =>
        asSql(and.left) ~*~ ss.And ~*~ asSql(and.right)
      case or: Or =>
        asSql(or.left) ~*~ ss.Or ~*~ asSql(or.right)
      case op: BooleanOperation[?] =>
        exprAsSql(op.left) ~*~ op.op.asSql ~*~ exprAsSql(op.right)
      case is: IsNull =>
        exprAsSql(is.left) ~*~ (if (is.not) ss.IsNotNull else ss.IsNull)
      case in: In[?] if in.set.isEmpty =>
        exprAsSql(in.left) ~*~ ss.In ~*~ ss.NullWithParens
      case in: In[?] =>
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
    case Opt(e) =>
      exprAsSql(e)
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
    implicit def exprToOrderBy[T](f: Expr[T]): OrderBy = OrderBy(f)
  }

  case class OrderBy(expr: Expr[?], ascending: Boolean = true) {
    def asc: OrderBy = copy(ascending=true)
    def desc: OrderBy = copy(ascending=false)
    def asSql(implicit alias: PathCompiler): SqlString =
      QueryDsl.exprAsSql(expr) ~*~ (if (ascending) ss.Asc else ss.Desc)
  }

  abstract class Component[A](join: QueryDsl.Path) {
    def ===(right: A)(implicit mapper: ComponentMapper[A]): Condition =
      StructuralEquality(join, this, Iterable(right))
    infix def in(right: Iterable[A])(implicit mapper: ComponentMapper[A]): Condition =
      StructuralInClause(join, this, right.toVector)
    infix def in2(right: Iterable[A])(implicit mapper: ComponentMapper[A]): Condition =
      StructuralEquality(join, this, right)
  }

}



class QueryDsl[T, TableDsl, K](
  val keyedTableMapper: KeyedTableMapper[T, K],
  val tableDsl: TableDsl
) {

  implicit def keyedTableMapper0: KeyedTableMapper[T, K] = keyedTableMapper

  def query(whereFn: TableDsl => QueryDsl.Condition): SelectQuery[T, TableDsl] =
    SelectQueryImpl(tableDsl, keyedTableMapper, whereFn(tableDsl), Nil)

  def update(set: TableDsl => Iterable[UpdateQuery.Assignment[?]]): UpdateQuery[TableDsl] =
    UpdateQueryImpl(
      tableDsl = tableDsl,
      outerMapper = keyedTableMapper,
      assignments = set(tableDsl),
      where = Condition.TRUE
    )

  def updateRow(row: T)(where: TableDsl => QueryDsl.Condition)(implicit conn: Conn): Task[Option[T]] = {
    val selectQuery = SelectQueryImpl(tableDsl, keyedTableMapper, where(tableDsl), Nil)
    conn
      .updateRowWhere(row)(selectQuery.queryResolver.whereSqlNoAlias)
  }

}

