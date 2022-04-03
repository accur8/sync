package a8.shared.jdbcf.mapper


import a8.shared.Meta._
import a8.shared.SharedImports._
import a8.shared.jdbcf.SqlString._
import a8.shared.jdbcf._
import a8.shared.jdbcf.mapper.CaseClassMapper.And
import a8.shared.jdbcf.mapper.KeyedTableMapper.UpsertResult
import a8.shared.jdbcf.querydsl.QueryDsl
import a8.shared.jdbcf.querydsl.QueryDsl.BooleanOperation

import java.sql.PreparedStatement
import scala.reflect.{ClassTag, classTag}

object MapperBuilder {

  object impl {

    def explodingPrimaryKey[A,B]: PrimaryKey[A,B] =
      new PrimaryKey[A,B] {
        override def key(a: A): B = sys.error("no primary key defined")
        override def columnNames: Iterable[ColumnName] = sys.error("no primary key defined")
        override def applyParameters(ps: PreparedStatement, b: B, parameterIndex: Int): Unit = sys.error("no primary key defined")
      }

  }

  def apply[A : ClassTag,B](generator: Generator[A,B]): MapperBuilder[A,B,Unit] =
    MapperBuilderImpl[A,B,Unit](generator, None)

  sealed trait Parm[A] {
    val name: String
    lazy val columnNamePrefix: ColumnName = ColumnName(name)
    val columnNames: Iterable[ColumnName]
    val columnCount: Int
    val ordinal: Int
    def rawRead(row: Row, index: Int): (Any, Int)
    def booleanOp(linker: QueryDsl.Linker, a: A): QueryDsl.Condition
    def applyParameters(ps: PreparedStatement, a: A, parameterIndexOffset: Int): Unit
//    def sqlString(a: A): SqlString
  }

  case class FromCaseClassParm[A,B : RowWriter](parm: CaseClassParm[A,B], rowReader: RowReader[B], ordinal: Int) extends Parm[A] {
//    val sqlStringer: SqlStringer[B] = implicitly[SqlStringer[B]]
//    def sqlString(a: A): SqlString = sqlStringer.toSqlString(parm.lens(a))
    lazy val name = parm.name
    lazy val columnNames = rowWriterB.columnNames(columnNamePrefix)
    lazy val columnCount: Int = columnNames.size
    lazy val rowWriterB = RowWriter[B]
    def rawRead(row: Row, index: Int): (Any, Int) =
      rowReader.rawRead(row, index)
    def applyParameters(ps: PreparedStatement, a: A, parameterIndex: Int): Unit =
      rowWriterB.applyParameters(ps, parm.lens(a), parameterIndex)

    override def booleanOp(linker: QueryDsl.Linker, a: A): QueryDsl.Condition = {
      import QueryDsl._
      BooleanOperation(Field(name, linker), ops.eq, Constant[B](parm.lens(a)))
    }

  }

  case class MapperBuilderImpl[A : ClassTag,B,PK](
    generator: Generator[A,B],
    primaryKey: Option[PrimaryKey[A,PK]] = None,
    fields: Vector[Parm[A]] = Vector.empty,
    tableName: Option[TableName] = None
  ) extends MapperBuilder[A,B,PK] {

    override def tableName(tableName: String): MapperBuilder[A, B, PK] =
      copy(tableName = Some(TableName(tableName)))

    override def singlePrimaryKey[PK1: RowReader : RowWriter](fn: B => CaseClassParm[A, PK1]): MapperBuilder[A, B, PK1] = {
      val parm = FromCaseClassParm(fn(generator.caseClassParameters), RowReader[PK1], fields.size)
      copy(primaryKey = Some(SinglePrimaryKey(parm)))
    }

    override def compositePrimaryKey2[PK1: RowReader : RowWriter, PK2: RowReader : RowWriter](fn: B => (CaseClassParm[A, PK1], CaseClassParm[A, PK2])): MapperBuilder[A, B, (PK1, PK2)] = {
      val parm0 = FromCaseClassParm(fn(generator.caseClassParameters)._1, RowReader[PK1], fields.size)
      val parm1 = FromCaseClassParm(fn(generator.caseClassParameters)._2, RowReader[PK2], fields.size)
      copy(primaryKey = Some(CompositePrimaryKey(parm0, parm1)))
    }

    override def addField[C: RowReader : RowWriter](fn: B => CaseClassParm[A, C]): MapperBuilder[A,B,PK] = {
      val parm = FromCaseClassParm(fn(generator.caseClassParameters), RowReader[C], fields.size)
      copy(fields = fields :+ parm)
    }

    override def buildMapper: Mapper[A] =
      buildKeyedTableMapper

    override def buildTableMapper: TableMapper[A] =
      buildKeyedTableMapper

    override def buildKeyedTableMapper: KeyedTableMapper[A,PK] = {
      if ( fields.size != generator.constructors.expectedFieldCount ) {
        sys.error(s"field mis match builder has ${fields.size} fields and constructor expects ${generator.constructors.expectedFieldCount} fields")
      }
      val tn = tableName.getOrElse(TableName(classTag.runtimeClass.shortName))
      CaseClassMapper[A,PK](fields, generator.constructors.iterRawConstruct, primaryKey.getOrElse(impl.explodingPrimaryKey[A,PK]), tn)
    }

  }

  trait PrimaryKey[A,B] {
    def columnNames: Iterable[ColumnName]
//    def whereClause(b: B): SqlString
    def applyParameters(ps: java.sql.PreparedStatement, b: B, parameterIndex: Int): Unit
    def key(a: A): B

    lazy val whereClausePs: SqlString =
      columnNames.map(cn => sql"${cn} = ?").mkSqlString(And)

  }


  case class SinglePrimaryKey[A,B : RowWriter](
    parm: FromCaseClassParm[A,B],
  )
    extends PrimaryKey[A,B]
  {
    val rowWriterB = RowWriter[B]
    override def key(a: A): B = parm.parm.lens(a)
    override val columnNames = parm.columnNames

    override def applyParameters(ps: PreparedStatement, b: B, parameterIndex: Int): Unit =
      rowWriterB.applyParameters(ps, b, parameterIndex)

  }

  case class CompositePrimaryKey[A,B1 : RowWriter, B2 : RowWriter](
    parm0: FromCaseClassParm[A,B1],
    parm1: FromCaseClassParm[A,B2],
  )
    extends PrimaryKey[A,(B1,B2)]
  {
    override def key(a: A): (B1,B2) = parm0.parm.lens(a) -> parm1.parm.lens(a)
    override val columnNames = parm0.columnNames ++ parm1.columnNames
    override def applyParameters(ps: PreparedStatement, b: (B1, B2), parameterIndex: Int): Unit = {
      parm0.rowWriterB.applyParameters(ps, b._1, parameterIndex)
      parm1.rowWriterB.applyParameters(ps, b._2, parameterIndex + parm0.columnCount)
    }
  }


}


trait MapperBuilder[A,B,PK] {
  def tableName(tableName: String): MapperBuilder[A,B,PK]
  def addField[F: RowReader : RowWriter](fn: B => CaseClassParm[A,F]): MapperBuilder[A,B,PK]
//  def addField[F: Mapper](fn: B => CaseClassParm[A,F]): MapperBuilder[A,B,PK]
  def singlePrimaryKey[PK1: RowReader : RowWriter](fn: B => CaseClassParm[A,PK1]): MapperBuilder[A,B,PK1]
  def compositePrimaryKey2[PK1: RowReader : RowWriter, PK2: RowReader : RowWriter](fn: B => (CaseClassParm[A,PK1], CaseClassParm[A,PK2])): MapperBuilder[A,B,(PK1,PK2)]
//  def primaryKey2[PK1: RowReader, PK2: RowReader](fn1: B => CaseClassParm[A,PK1], fn2: B => CaseClassParm[A,PK1]): MapperBuilder[A,B,(PK1,PK2)]
//  def primaryKey3[PK1: RowReader, PK2: RowReader, PK3: RowReader](fn1: B => CaseClassParm[A,PK1], fn2: B => CaseClassParm[A,PK1], fn3: B => CaseClassParm[A,PK3]): MapperBuilder[A,B,(PK1,PK2,PK3)]
  def buildMapper: Mapper[A]
  def buildTableMapper: TableMapper[A]
  def buildKeyedTableMapper: KeyedTableMapper[A,PK]
}
