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
    val name: String
    val columnCount: Int
    val ordinal: Int
    val columnNames: Iterable[ColumnName]
    def columnNames(columnNamePrefix: ColumnName): Iterable[ColumnName]
    def values(a: A): Vector[QueryDsl.Constant[_]]
    def fields(path: QueryDsl.Path, columnNameResolver: ColumnNameResolver): Vector[QueryDsl.FieldExpr[_]]
    def rawRead(row: Row, index: Int): (Any, Int)
    def booleanOp(linker: QueryDsl.Path, a: A, columnNameResolver: ColumnNameResolver)(implicit alias: PathCompiler): QueryDsl.Condition
    def materialize(columnNamePrefix: ColumnName, conn: Conn, resolvedJdbcTable: JdbcMetadata.ResolvedJdbcTable): Task[Parm[A]]

  }

  case class FromCaseClassParm[A,B : FieldHandler](parm: CaseClassParm[A,B], ordinal: Int) extends Parm[A] {

    override def pairs(columnNamePrefix: ColumnName, row: A): Iterable[(ColumnName, SqlString)] = fieldHandler.pairs(columnNamePrefix ~ ColumnName(name), parm.lens(row))
    val fieldHandler = FieldHandler[B]
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
          FromCaseClassParm[A,B](parm, ordinal)(materializedFieldHandler)
        }
    }

    override def booleanOp(linker: Path, a: A, columnNameResolver: ColumnNameResolver)(implicit alias: PathCompiler): QueryDsl.Condition =
      fieldHandler.booleanOp(linker, name, parm.lens(a), columnNameResolver)

    def booleanOpB(linker: QueryDsl.Path, b: B, columnNameResolver: ColumnNameResolver)(implicit alias: PathCompiler): QueryDsl.Condition =
      fieldHandler.booleanOp(linker, name, b, columnNameResolver)

    override def values(a: A): Vector[QueryDsl.Constant[_]] =
      fieldHandler.values(parm.lens(a))

    override def fields(path: Path, columnNameResolver: ColumnNameResolver): Vector[QueryDsl.FieldExpr[_]] =
      fieldHandler.fieldExprs(path, name, columnNameResolver)

  }

  case class MapperBuilderImpl[A : ClassTag,B,PK](
    generator: Generator[A,B],
    primaryKey: Option[PrimaryKey[A,PK]] = None,
    fields: Vector[Parm[A]] = Vector.empty,
    tableName: Option[TableName] = None
  ) extends MapperBuilder[A,B,PK] {

    override def tableName(tableName: String): MapperBuilder[A, B, PK] =
      copy(tableName = Some(TableName(tableName)))


    override def singlePrimaryKey[PK1: FieldHandler](fn: B => CaseClassParm[A, PK1]): MapperBuilder[A, B, PK1] = {
      val parm = FromCaseClassParm(fn(generator.caseClassParameters), fields.size)
      copy(primaryKey = Some(SinglePrimaryKey(parm)))
    }

    override def compositePrimaryKey2[PK1: FieldHandler, PK2: FieldHandler](fn: B => (CaseClassParm[A, PK1], CaseClassParm[A, PK2])): MapperBuilder[A, B, (PK1, PK2)] = {
      val parm0 = FromCaseClassParm(fn(generator.caseClassParameters)._1, fields.size)
      val parm1 = FromCaseClassParm(fn(generator.caseClassParameters)._2, fields.size)
      copy(primaryKey = Some(CompositePrimaryKey(parm0, parm1)))
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

  case class CompositePrimaryKey[A, B1, B2](
    parm0: FromCaseClassParm[A,B1],
    parm1: FromCaseClassParm[A,B2],
  )
    extends PrimaryKey[A,(B1,B2)]
  {
    override def key(a: A): (B1,B2) = parm0.parm.lens(a) -> parm1.parm.lens(a)
    override val columnNames = parm0.columnNames ++ parm1.columnNames
    override def whereClause(key: (B1, B2), columnNameResolver: ColumnNameResolver): SqlString = ???
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
//  def primaryKey2[PK1: RowReader, PK2: RowReader](fn1: B => CaseClassParm[A,PK1], fn2: B => CaseClassParm[A,PK1]): MapperBuilder[A,B,(PK1,PK2)]
//  def primaryKey3[PK1: RowReader, PK2: RowReader, PK3: RowReader](fn1: B => CaseClassParm[A,PK1], fn2: B => CaseClassParm[A,PK1], fn3: B => CaseClassParm[A,PK3]): MapperBuilder[A,B,(PK1,PK2,PK3)]
  def buildMapper: ComponentMapper[A]
  def buildTableMapper(implicit auditProvider: AuditProvider[A]): TableMapper[A]
  def buildKeyedTableMapper(implicit auditProvider: AuditProvider[A]): KeyedTableMapper[A,PK]
}
