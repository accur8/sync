package a8.shared.jdbcf.mapper


import a8.shared.Meta._
import a8.shared.SharedImports._
import a8.shared.jdbcf.SqlString._
import a8.shared.jdbcf._
import a8.shared.jdbcf.mapper.KeyedMapper.UpsertResult

import scala.reflect.{ClassTag, classTag}

object MapperBuilder {

  object impl {

    def explodingPrimaryKey[A,B]: PrimaryKey[A,B] =
      new PrimaryKey[A,B] {
        override def key(a: A): B = sys.error("no primary key defined")
        override def columnNames: Iterable[ColumnName] = sys.error("no primary key defined")
        override def whereClause(b: B): SqlString = sys.error("no primary key defined")
      }

  }

  def apply[A : ClassTag,B](generator: Generator[A,B]): MapperBuilder[A,B,Unit] =
    MapperBuilderImpl[A,B,Unit](generator, None)

  sealed trait Parm[A] {
    val name: String
    lazy val columnName = ColumnName(name)
    val ordinal: Int
    def rawRead(row: Row, index: Int): (Any, Int)
    def sqlString(a: A): SqlString
  }

  case class FromCaseClassParm[A,B : SqlStringer](parm: CaseClassParm[A,B], rowReader: RowReader[B], ordinal: Int) extends Parm[A] {
    val sqlStringer: SqlStringer[B] = implicitly[SqlStringer[B]]
    def sqlString(a: A): SqlString = sqlStringer.toSqlString(parm.lens(a))
    val name = parm.name
    def rawRead(row: Row, index: Int): (Any, Int) =
      rowReader.rawRead(row, index)
  }

  case class MapperBuilderImpl[A : ClassTag,B,PK](
    generator: Generator[A,B],
    primaryKey: Option[PrimaryKey[A,PK]] = None,
    fields: Vector[Parm[A]] = Vector.empty,
    tableName: Option[TableName] = None
  ) extends MapperBuilder[A,B,PK] {

    override def tableName(tableName: String): MapperBuilder[A, B, PK] =
      copy(tableName = Some(TableName(tableName)))

    override def singlePrimaryKey[PK1: RowReader : SqlStringer](fn: B => CaseClassParm[A, PK1]): MapperBuilder[A, B, PK1] = {
      val parm = FromCaseClassParm(fn(generator.caseClassParameters), RowReader[PK1], fields.size)
      copy(primaryKey = Some(SinglePrimaryKey(parm)))
    }

    override def compositePrimaryKey2[PK1: RowReader : SqlStringer, PK2: RowReader : SqlStringer](fn: B => (CaseClassParm[A, PK1], CaseClassParm[A, PK2])): MapperBuilder[A, B, (PK1, PK2)] = {
      val parm0 = FromCaseClassParm(fn(generator.caseClassParameters)._1, RowReader[PK1], fields.size)
      val parm1 = FromCaseClassParm(fn(generator.caseClassParameters)._2, RowReader[PK2], fields.size)
      copy(primaryKey = Some(CompositePrimaryKey(parm0, parm1)))
    }

    override def addField[C: RowReader : SqlStringer](fn: B => CaseClassParm[A, C]): MapperBuilder[A,B,PK] = {
      val parm = FromCaseClassParm(fn(generator.caseClassParameters), RowReader[C], fields.size)
      copy(fields = fields :+ parm)
    }

    override def buildMapper: Mapper[A] =
      buildKeyedMapper

    override def buildKeyedMapper: KeyedMapper[A,PK] = {
      if ( fields.size != generator.constructors.expectedFieldCount ) {
        sys.error(s"field mis match builder has ${fields.size} fields and constructor expects ${generator.constructors.expectedFieldCount} fields")
      }
      val tn = tableName.getOrElse(TableName(classTag.runtimeClass.shortName))
      CaseClassMapper[A,PK](fields, generator.constructors.iterRawConstruct, primaryKey.getOrElse(impl.explodingPrimaryKey[A,PK]), tn)
    }

  }

  trait PrimaryKey[A,B] {
    def columnNames: Iterable[ColumnName]
    def whereClause(b: B): SqlString
    def key(a: A): B
  }


  case class SinglePrimaryKey[A,B : SqlStringer](
    parm: FromCaseClassParm[A,B],
  )
    extends PrimaryKey[A,B]
  {
    override def key(a: A): B = parm.parm.lens(a)
    override val columnNames = Iterable(parm.columnName)
    override def whereClause(b: B): SqlString = sql"${parm.columnName} = ${implicitly[SqlStringer[B]].toSqlString(b)}"
  }

  case class CompositePrimaryKey[A,B1 : SqlStringer, B2 : SqlStringer](
    parm0: FromCaseClassParm[A,B1],
    parm1: FromCaseClassParm[A,B2],
  )
    extends PrimaryKey[A,(B1,B2)]
  {
    override def key(a: A): (B1,B2) = parm0.parm.lens(a) -> parm1.parm.lens(a)
    override val columnNames = Iterable(parm0.columnName, parm1.columnName)

    override def whereClause(b: (B1,B2)): SqlString =
      sql"${parm0.columnName} = ${implicitly[SqlStringer[B1]].toSqlString(b._1)} and ${parm1.columnName} = ${implicitly[SqlStringer[B2]].toSqlString(b._2)}"
  }


}


trait MapperBuilder[A,B,PK] {
  def tableName(tableName: String): MapperBuilder[A,B,PK]
  def addField[F: RowReader : SqlStringer](fn: B => CaseClassParm[A,F]): MapperBuilder[A,B,PK]
  def singlePrimaryKey[PK1: RowReader : SqlStringer](fn: B => CaseClassParm[A,PK1]): MapperBuilder[A,B,PK1]
  def compositePrimaryKey2[PK1: RowReader : SqlStringer, PK2: RowReader : SqlStringer](fn: B => (CaseClassParm[A,PK1], CaseClassParm[A,PK2])): MapperBuilder[A,B,(PK1,PK2)]
//  def primaryKey2[PK1: RowReader, PK2: RowReader](fn1: B => CaseClassParm[A,PK1], fn2: B => CaseClassParm[A,PK1]): MapperBuilder[A,B,(PK1,PK2)]
//  def primaryKey3[PK1: RowReader, PK2: RowReader, PK3: RowReader](fn1: B => CaseClassParm[A,PK1], fn2: B => CaseClassParm[A,PK1], fn3: B => CaseClassParm[A,PK3]): MapperBuilder[A,B,(PK1,PK2,PK3)]
  def buildMapper: Mapper[A]
  def buildKeyedMapper: KeyedMapper[A,PK]
}
