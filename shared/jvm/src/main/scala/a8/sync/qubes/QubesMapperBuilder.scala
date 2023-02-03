package a8.sync.qubes

import a8.shared.Meta.{CaseClassParm, Generator}
import a8.shared.SharedImports._
import a8.shared.jdbcf.SqlString.SqlStringer
import a8.shared.jdbcf.{ColumnName, SqlString, TableName}
import a8.shared.json.ast._
import a8.shared.json.{JsonCodec, JsonObjectCodec, JsonTypedCodec}

import scala.reflect.ClassTag

object QubesMapperBuilder {

  def apply[A: ClassTag,B](generator: Generator[A,B])(implicit jsonTypedCodec: JsonTypedCodec[A, JsObj]): QubesMapperBuilder[A,B,Unit] =
    QubesMapperBuilderImpl[A,B,Unit](generator)


  sealed trait Parm[A] {
    val name: String
    val columnName: ColumnName
    val ordinal: Int
    def toJson(a: A): JsVal
  }

  case class CaseClassParmParm[A,B : JsonCodec](caseClassParm: CaseClassParm[A,B]) extends Parm[A] {
    val name = caseClassParm.name
    val columnName = ColumnName(name)
    val ordinal = caseClassParm.ordinal
    def toJson(a: A) = caseClassParm.lens(a).toJsVal
  }

  sealed trait PrimaryKey[A,B] {
    def whereClause(b: B): SqlString
  }

  case class SinglePrimaryKey[A,B : JsonCodec : SqlStringer](
    parm: CaseClassParm[A,B],
  ) extends PrimaryKey[A,B] {
    import SqlString._
    val sqlStringer = implicitly[SqlStringer[B]]
    def whereClause(primaryKey: B): SqlString = {
      sql"${parm.name.identifier} = ${sqlStringer.toSqlString(primaryKey)}"
    }
  }

  case class CompositePrimaryKey2[A,PK1 : JsonCodec : SqlStringer, PK2 : JsonCodec : SqlStringer](
    parm1: CaseClassParm[A,PK1],
    parm2: CaseClassParm[A,PK2],
  ) extends PrimaryKey[A,(PK1,PK2)] {
    import SqlString._
    val sqlStringer1 = implicitly[SqlStringer[PK1]]
    val sqlStringer2 = implicitly[SqlStringer[PK2]]
    override def whereClause(key: (PK1, PK2)): SqlString = {
      sql"${parm1.name.identifier} = ${sqlStringer1.toSqlString(key._1)} and ${parm2.name.identifier} = ${sqlStringer2.toSqlString(key._2)}"
    }
  }

  case class CompositePrimaryKey3[A,PK1 : JsonCodec : SqlStringer, PK2 : JsonCodec : SqlStringer, PK3 : JsonCodec : SqlStringer](
    parm1: CaseClassParm[A,PK1],
    parm2: CaseClassParm[A,PK2],
    parm3: CaseClassParm[A,PK3],
  ) extends PrimaryKey[A,(PK1,PK2,PK3)] {
    import SqlString._
    val sqlStringer1 = implicitly[SqlStringer[PK1]]
    val sqlStringer2 = implicitly[SqlStringer[PK2]]
    val sqlStringer3 = implicitly[SqlStringer[PK3]]
    override def whereClause(key: (PK1, PK2, PK3)): SqlString = {
      sql"${parm1.name.identifier} = ${sqlStringer1.toSqlString(key._1)} and ${parm2.name.identifier} = ${sqlStringer2.toSqlString(key._2)} and ${parm3.name.identifier} = ${sqlStringer3.toSqlString(key._3)}"
    }
  }

  case class CompositePrimaryKey4[A,PK1 : JsonCodec : SqlStringer, PK2 : JsonCodec : SqlStringer, PK3 : JsonCodec : SqlStringer, PK4 : JsonCodec : SqlStringer](
    parm1: CaseClassParm[A,PK1],
    parm2: CaseClassParm[A,PK2],
    parm3: CaseClassParm[A,PK3],
    parm4: CaseClassParm[A,PK4],
  ) extends PrimaryKey[A,(PK1,PK2,PK3,PK4)] {
    import SqlString._
    val sqlStringer1 = implicitly[SqlStringer[PK1]]
    val sqlStringer2 = implicitly[SqlStringer[PK2]]
    val sqlStringer3 = implicitly[SqlStringer[PK3]]
    val sqlStringer4 = implicitly[SqlStringer[PK4]]
    override def whereClause(key: (PK1, PK2, PK3, PK4)): SqlString = {
      sql"${parm1.name.identifier} = ${sqlStringer1.toSqlString(key._1)} and ${parm2.name.identifier} = ${sqlStringer2.toSqlString(key._2)} and ${parm3.name.identifier} = ${sqlStringer3.toSqlString(key._3)} and ${parm4.name.identifier} = ${sqlStringer4.toSqlString(key._4)}"
    }
  }
  case class QubesMapperBuilderImpl[A : ClassTag, B, PK](
    generator: Generator[A,B],
    cubeName: Option[String] = None,
    appSpace: Option[String] = None,
    parms: Vector[Parm[A]] = Vector.empty,
    primaryKey: Option[PrimaryKey[A,PK]] = None,
  )(
    implicit
      jsonTypedCodec: JsonTypedCodec[A, JsObj]
  )
    extends QubesMapperBuilder[A,B,PK]
  {
    
    override def cubeName(cubeName: String): QubesMapperBuilder[A, B, PK] =
      copy(cubeName = Some(cubeName))

    override def appSpace(appSpace: String): QubesMapperBuilder[A, B, PK] =
      copy(appSpace = Some(appSpace))

    override def addField[C : JsonCodec](fn: B => CaseClassParm[A, C]): QubesMapperBuilder[A, B, PK] =
      copy(parms = parms :+ CaseClassParmParm[A,C](fn(generator.caseClassParameters)))

    override def compositePrimaryKey2[PK1: JsonCodec : SqlStringer, PK2: JsonCodec: SqlStringer](fn: B => (CaseClassParm[A, PK1], CaseClassParm[A, PK2])): QubesMapperBuilder[A, B, (PK1, PK2)] = {
      val t = fn(generator.caseClassParameters)
      copy(primaryKey = Some(CompositePrimaryKey2(t._1, t._2)))
    }

    override def compositePrimaryKey3[PK1: JsonCodec : SqlStringer, PK2: JsonCodec : SqlStringer, PK3: JsonCodec : SqlStringer](fn: B => (CaseClassParm[A, PK1], CaseClassParm[A, PK2], CaseClassParm[A, PK3])): QubesMapperBuilder[A, B, (PK1, PK2, PK3)] = {
      val t = fn(generator.caseClassParameters)
      copy(primaryKey = Some(CompositePrimaryKey3(t._1, t._2, t._3)))
    }

    override def compositePrimaryKey4[PK1: JsonCodec : SqlStringer, PK2: JsonCodec : SqlStringer, PK3: JsonCodec : SqlStringer, PK4: JsonCodec : SqlStringer](fn: B => (CaseClassParm[A, PK1], CaseClassParm[A, PK2], CaseClassParm[A, PK3], CaseClassParm[A, PK4])): QubesMapperBuilder[A, B, (PK1, PK2, PK3, PK4)] = {
      val t = fn(generator.caseClassParameters)
      copy(primaryKey = Some(CompositePrimaryKey4(t._1, t._2, t._3, t._4)))
    }

    override def singlePrimaryKey[C: JsonCodec : SqlStringer](fn: B => CaseClassParm[A, C]): QubesMapperBuilder[A, B, C] =
      copy(primaryKey = Some(SinglePrimaryKey[A,C](fn(generator.caseClassParameters))))

    override def build: QubesKeyedMapper[A,PK] =
      QubesMapper.QubesMapperImpl[A,PK](
        appSpace = appSpace.getOrElse(sys.error("must supply an appSpace")),
        cubeName = cubeName.map(TableName.apply).getOrElse(sys.error("must supply a cubeName")),
        rawParms = parms,
        primaryKey = primaryKey.getOrElse(sys.error("must supply a primaryKey")),
      )

  }

}



trait QubesMapperBuilder[A,B,PK] {

  def cubeName(cubeName: String): QubesMapperBuilder[A,B,PK]
  def appSpace(appSpace: String): QubesMapperBuilder[A,B,PK]
  def addField[C : JsonCodec](fn: B => CaseClassParm[A,C]): QubesMapperBuilder[A,B,PK]
  def singlePrimaryKey[PK : JsonCodec : SqlStringer](fn: B => CaseClassParm[A,PK]): QubesMapperBuilder[A,B,PK]
  def compositePrimaryKey2[PK1 : JsonCodec : SqlStringer, PK2 : JsonCodec : SqlStringer](fn: B => (CaseClassParm[A,PK1], CaseClassParm[A,PK2])): QubesMapperBuilder[A,B,(PK1,PK2)]
  def compositePrimaryKey3[PK1 : JsonCodec : SqlStringer, PK2 : JsonCodec : SqlStringer, PK3 : JsonCodec : SqlStringer](fn: B => (CaseClassParm[A,PK1], CaseClassParm[A,PK2], CaseClassParm[A,PK3])): QubesMapperBuilder[A,B,(PK1,PK2,PK3)]
  def compositePrimaryKey4[PK1 : JsonCodec : SqlStringer, PK2 : JsonCodec : SqlStringer, PK3 : JsonCodec : SqlStringer, PK4 : JsonCodec : SqlStringer](fn: B => (CaseClassParm[A,PK1], CaseClassParm[A,PK2], CaseClassParm[A,PK3], CaseClassParm[A,PK4])): QubesMapperBuilder[A,B,(PK1,PK2,PK3,PK4)]
  def build: QubesKeyedMapper[A,PK]

}
