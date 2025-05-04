package a8.shared.jdbcf.mapper


import a8.shared.{Chord, SharedImports}
import a8.shared.Meta._
import a8.shared.SharedImports._
import a8.shared.jdbcf.JdbcMetadata.ResolvedJdbcTable
import a8.shared.jdbcf.SqlString._
import a8.shared.jdbcf._
import a8.shared.jdbcf.mapper.CaseClassMapper.{And, ColumnNameResolver}
import a8.shared.jdbcf.mapper.KeyedTableMapper.UpsertResult
import a8.shared.jdbcf.mapper.Mapper.FieldHandler
import a8.shared.jdbcf.mapper.MapperBuilder.AuditProvider
import a8.shared.jdbcf.querydsl.QueryDsl
import a8.shared.jdbcf.querydsl.QueryDsl.{BooleanOperation, ComponentJoin, Join, Path, PathCompiler, field, fieldExprs}

import java.sql.PreparedStatement
import scala.reflect.{ClassTag, classTag}
import language.implicitConversions
import zio._

object MapperBuilder {

  object impl {

    def explodingPrimaryKey[A,B]: PrimaryKey[A,B] =
      new PrimaryKey[A,B] {
        override def key(a: A): B = sys.error("no primary key defined")
        override def columnNames: Iterable[ColumnName] = sys.error("no primary key defined")
        override def whereClause(key: B, columnNameResolver: ColumnNameResolver): SqlString = sys.error("no primary key defined")
      }

  }

  def apply[A : ClassTag,B](generator: Generator[A,B]): MapperBuilder[A,B,Unit] =
    MapperBuilderImpl[A,B,Unit](generator, None)

  sealed trait Parm[A] {
    def pairs(columnNamePrefix: ColumnName, row: A): Iterable[(ColumnName, SqlString)]
    lazy val name: String
    lazy val columnCount: Int
    val ordinal: Int
    lazy val columnNames: Iterable[ColumnName]
    def columnNames(columnNamePrefix: ColumnName): Iterable[ColumnName]
    def values(a: A): Vector[QueryDsl.Constant[?]]
    def fields(path: QueryDsl.Path, columnNameResolver: ColumnNameResolver): Vector[QueryDsl.FieldExpr[?]]
    def rawRead(row: Row, index: Int): (Any, Int)
    def booleanOp(linker: QueryDsl.Path, a: A, columnNameResolver: ColumnNameResolver)(implicit alias: PathCompiler): QueryDsl.Condition
    def materialize(columnNamePrefix: ColumnName, conn: Conn, resolvedJdbcTable: JdbcMetadata.ResolvedJdbcTable): Task[Parm[A]]

  }

  case class FromCaseClassParm[A,B : FieldHandler](parm: CaseClassParm[A,B], ordinal: Int) extends Parm[A] {

    override def pairs(columnNamePrefix: ColumnName, row: A): Iterable[(ColumnName, SqlString)] = fieldHandler.pairs(columnNamePrefix ~ ColumnName(name), parm.lens(row))
    val fieldHandler: FieldHandler[B] = FieldHandler[B]
//    val sqlStringer: SqlStringer[B] = implicitly[SqlStringer[B]]
//    def sqlString(a: A): SqlString = sqlStringer.toSqlString(parm.lens(a))
    lazy val name = parm.name
    lazy val columnNames = fieldHandler.columnNames(ColumnName(name))
    override def columnNames(columnNamePrefix: ColumnName): Iterable[ColumnName] = fieldHandler.columnNames(columnNamePrefix)
    lazy val columnCount: Int = fieldHandler.columnCount
    def rawRead(row: Row, index: Int): (Any, Int) =
      fieldHandler.rowReader.rawRead(row, index)

    override def materialize(columnNamePrefix: ColumnName, conn: Conn, resolvedJdbcTable: ResolvedJdbcTable): Task[Parm[A]] = {
      val columnName = ColumnName(columnNamePrefix.asString + parm.name)
      fieldHandler
        .materialize(columnName, conn, resolvedJdbcTable)
        .map { (materializedFieldHandler: FieldHandler[B]) =>
          given FieldHandler[B] = materializedFieldHandler
          FromCaseClassParm[A,B](parm, ordinal)
        }
    }

    override def booleanOp(linker: Path, a: A, columnNameResolver: ColumnNameResolver)(implicit alias: PathCompiler): QueryDsl.Condition =
      fieldHandler.booleanOp(linker, name, parm.lens(a), columnNameResolver)

    def booleanOpB(linker: QueryDsl.Path, b: B, columnNameResolver: ColumnNameResolver)(implicit alias: PathCompiler): QueryDsl.Condition =
      fieldHandler.booleanOp(linker, name, b, columnNameResolver)

    override def values(a: A): Vector[QueryDsl.Constant[?]] =
      fieldHandler.values(parm.lens(a))

    override def fields(path: Path, columnNameResolver: ColumnNameResolver): Vector[QueryDsl.FieldExpr[?]] =
      fieldHandler.fieldExprs(path, name, columnNameResolver)

  }

  case class MapperBuilderImpl[A : ClassTag,B,PK](
    generator: Generator[A,B],
    primaryKey: Option[PrimaryKey[A,PK]] = None,
    fields: Vector[Parm[A]] = Vector.empty[Parm[A]],
    tableName: Option[TableName] = None
  ) extends MapperBuilder[A,B,PK] {

    override def tableName(tableName: String): MapperBuilder[A, B, PK] =
      copy(tableName = Some(TableName(tableName)))


    override def singlePrimaryKey[PK1: FieldHandler](fn: B => CaseClassParm[A, PK1]): MapperBuilder[A, B, PK1] = {
      val parm = FromCaseClassParm(fn(generator.caseClassParameters), fields.size)
      copy(primaryKey = Some(SinglePrimaryKey(parm)))
    }

    override def compositePrimaryKey2[PK1: FieldHandler, PK2: FieldHandler](fn: B => (CaseClassParm[A, PK1], CaseClassParm[A, PK2])): MapperBuilder[A, B, (PK1, PK2)] = {
      val parm1 = FromCaseClassParm(fn(generator.caseClassParameters)._1, fields.size)
      val parm2 = FromCaseClassParm(fn(generator.caseClassParameters)._2, fields.size)
      copy(primaryKey = Some(CompositePrimaryKey2(parm1, parm2)))
    }

    override def compositePrimaryKey3[PK1: FieldHandler, PK2: FieldHandler, PK3: FieldHandler](fn: B => (CaseClassParm[A, PK1], CaseClassParm[A, PK2], CaseClassParm[A, PK3])): MapperBuilder[A, B, (PK1, PK2, PK3)] = {
      val parm1 = FromCaseClassParm(fn(generator.caseClassParameters)._1, fields.size)
      val parm2 = FromCaseClassParm(fn(generator.caseClassParameters)._2, fields.size)
      val parm3 = FromCaseClassParm(fn(generator.caseClassParameters)._3, fields.size)
      copy(primaryKey = Some(CompositePrimaryKey3(parm1, parm2, parm3)))
    }

    override def compositePrimaryKey4[PK1: FieldHandler, PK2: FieldHandler, PK3: FieldHandler, PK4: FieldHandler](fn: B => (CaseClassParm[A, PK1], CaseClassParm[A, PK2], CaseClassParm[A, PK3], CaseClassParm[A, PK4])): MapperBuilder[A, B, (PK1, PK2, PK3, PK4)] = {
      val parm1 = FromCaseClassParm(fn(generator.caseClassParameters)._1, fields.size)
      val parm2 = FromCaseClassParm(fn(generator.caseClassParameters)._2, fields.size)
      val parm3 = FromCaseClassParm(fn(generator.caseClassParameters)._3, fields.size)
      val parm4 = FromCaseClassParm(fn(generator.caseClassParameters)._4, fields.size)
      copy(primaryKey = Some(CompositePrimaryKey4(parm1, parm2, parm3, parm4)))
    }

    override def addField[F: FieldHandler](fn: B => CaseClassParm[A, F]): MapperBuilder[A, B, PK] = {
      val parm = FromCaseClassParm(fn(generator.caseClassParameters), fields.size)
      copy(fields = fields :+ parm)
    }

    override def buildMapper: ComponentMapper[A] =
      buildKeyedTableMapper


    override def buildTableMapper(implicit auditProvider: AuditProvider[A]): TableMapper[A] =
      buildKeyedTableMapper


    override def buildKeyedTableMapper(implicit auditProvider: AuditProvider[A]): KeyedTableMapper[A, PK] = {
      if ( fields.size != generator.constructors.expectedFieldCount ) {
        sys.error(s"field mis match builder has ${fields.size} fields and constructor expects ${generator.constructors.expectedFieldCount} fields")
      }
      val tn = tableName.getOrElse(TableName(classTag.runtimeClass.shortName))
      CaseClassMapper[A,PK](
        fields,
        generator.constructors.iterRawConstruct,
        primaryKey.getOrElse(impl.explodingPrimaryKey[A,PK]),
        tn,
        auditProvider,
      )
    }

  }

  trait PrimaryKey[A,B] {
    def columnNames: Iterable[ColumnName]
    def key(a: A): B
    def whereClause(key: B, columnNameResolver: ColumnNameResolver): SqlString
  }

  object implicits {
    implicit val linkCompiler: PathCompiler =
      new PathCompiler {
        override def alias(linker: Path): SqlString = {
          linker match {
            case c: ComponentJoin =>
              alias(c.parent)
            case QueryDsl.RootJoin =>
              SqlString.Empty
            case j: Join =>
              sys.error("not supported")
          }
        }
      }
  }

  case class SinglePrimaryKey[A,B](
    parm: FromCaseClassParm[A,B],
  )
    extends PrimaryKey[A,B]
  {
    override def key(a: A): B = parm.parm.lens(a)
    override val columnNames = parm.columnNames

    override def whereClause(key: B, columnNameResolver: ColumnNameResolver): SqlString = {
      import implicits._
      val condition = parm.booleanOpB(QueryDsl.RootJoin, key, columnNameResolver)
      val ss = QueryDsl.asSql(condition)
      ss
    }
  }

  case class CompositePrimaryKey2[A, PK1, PK2](
    parm1: FromCaseClassParm[A,PK1],
    parm2: FromCaseClassParm[A,PK2],
  )
    extends PrimaryKey[A,(PK1,PK2)]
  {
    override def key(a: A): (PK1,PK2) = parm1.parm.lens(a) -> parm2.parm.lens(a)
    override val columnNames: Iterable[ColumnName] = parm1.columnNames ++ parm2.columnNames
    override def whereClause(key: (PK1, PK2), columnNameResolver: ColumnNameResolver): SqlString = {
      import implicits._
      val cond1 = parm1.booleanOpB(QueryDsl.RootJoin, key._1, columnNameResolver)
      val cond2 = parm2.booleanOpB(QueryDsl.RootJoin, key._2, columnNameResolver)
      val condition = QueryDsl.And(cond1, cond2)
      val ss = QueryDsl.asSql(condition)
      ss
    }
  }

  case class CompositePrimaryKey3[A, PK1, PK2, PK3](
    parm1: FromCaseClassParm[A,PK1],
    parm2: FromCaseClassParm[A,PK2],
    parm3: FromCaseClassParm[A,PK3],
  )
    extends PrimaryKey[A,(PK1,PK2,PK3)]
  {
    override def key(a: A): (PK1,PK2,PK3) = (parm1.parm.lens(a), parm2.parm.lens(a), parm3.parm.lens(a))
    override val columnNames: Iterable[ColumnName] = parm1.columnNames ++ parm2.columnNames ++ parm3.columnNames
    override def whereClause(key: (PK1, PK2, PK3), columnNameResolver: ColumnNameResolver): SqlString = {
      import implicits._
      val cond1 = parm1.booleanOpB(QueryDsl.RootJoin, key._1, columnNameResolver)
      val cond2 = parm2.booleanOpB(QueryDsl.RootJoin, key._2, columnNameResolver)
      val cond3 = parm3.booleanOpB(QueryDsl.RootJoin, key._3, columnNameResolver)
      val condition = QueryDsl.And(cond1, QueryDsl.And(cond2, cond3))
      val ss = QueryDsl.asSql(condition)
      ss
    }
  }

  case class CompositePrimaryKey4[A, PK1, PK2, PK3, PK4](
    parm1: FromCaseClassParm[A,PK1],
    parm2: FromCaseClassParm[A,PK2],
    parm3: FromCaseClassParm[A,PK3],
    parm4: FromCaseClassParm[A,PK4],
  )
    extends PrimaryKey[A,(PK1,PK2,PK3,PK4)]
  {
    override def key(a: A): (PK1,PK2,PK3,PK4) = (parm1.parm.lens(a), parm2.parm.lens(a), parm3.parm.lens(a), parm4.parm.lens(a))
    override val columnNames: Iterable[ColumnName] = parm1.columnNames ++ parm2.columnNames ++ parm3.columnNames ++ parm4.columnNames
    override def whereClause(key: (PK1, PK2, PK3, PK4), columnNameResolver: ColumnNameResolver): SqlString = {
      import implicits._
      val cond1 = parm1.booleanOpB(QueryDsl.RootJoin, key._1, columnNameResolver)
      val cond2 = parm2.booleanOpB(QueryDsl.RootJoin, key._2, columnNameResolver)
      val cond3 = parm3.booleanOpB(QueryDsl.RootJoin, key._3, columnNameResolver)
      val cond4 = parm4.booleanOpB(QueryDsl.RootJoin, key._4, columnNameResolver)
      val condition = QueryDsl.And(cond1, QueryDsl.And(cond2, QueryDsl.And(cond3, cond4)))
      val ss = QueryDsl.asSql(condition)
      ss
    }
  }

  object AuditProvider {
    implicit def noop[A]: AuditProvider[A] =
      new AuditProvider[A] {
        override def onUpdate(a: A): A = a
        override def onInsert(a: A): A = a
      }
  }

  /**
   * generally as a design choice the AuditProvider should be minimallistic.  As the
   * design choice should be to expose most of the code at the model layer.  This is here
   * to support things like updating created and last changed timestamp's on tables.
   * @tparam A
   */
  trait AuditProvider[A] {
    def onUpdate(a: A): A
    def onInsert(a: A): A
  }

}


trait MapperBuilder[A,B,PK] {
  def tableName(tableName: String): MapperBuilder[A,B,PK]
  def addField[F: FieldHandler](fn: B => CaseClassParm[A,F]): MapperBuilder[A,B,PK]
//  def addField[F: Mapper](fn: B => CaseClassParm[A,F]): MapperBuilder[A,B,PK]
  def singlePrimaryKey[PK1: FieldHandler](fn: B => CaseClassParm[A,PK1]): MapperBuilder[A,B,PK1]
  def compositePrimaryKey2[PK1: FieldHandler, PK2: FieldHandler](fn: B => (CaseClassParm[A,PK1], CaseClassParm[A,PK2])): MapperBuilder[A,B,(PK1,PK2)]
  def compositePrimaryKey3[PK1: FieldHandler, PK2: FieldHandler, PK3: FieldHandler](fn: B => (CaseClassParm[A,PK1], CaseClassParm[A,PK2], CaseClassParm[A,PK3])): MapperBuilder[A,B,(PK1,PK2,PK3)]
  def compositePrimaryKey4[PK1: FieldHandler, PK2: FieldHandler, PK3: FieldHandler, PK4: FieldHandler](fn: B => (CaseClassParm[A,PK1], CaseClassParm[A,PK2], CaseClassParm[A,PK3], CaseClassParm[A,PK4])): MapperBuilder[A,B,(PK1,PK2,PK3,PK4)]
  def buildMapper: ComponentMapper[A]
  def buildTableMapper(implicit auditProvider: AuditProvider[A]): TableMapper[A]
  def buildKeyedTableMapper(implicit auditProvider: AuditProvider[A]): KeyedTableMapper[A,PK]
}
