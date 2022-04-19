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

  case class CompositePrimaryKey2[A,B : JsonCodec : SqlStringer, C : JsonCodec : SqlStringer](
    parmB: CaseClassParm[A,B],
    parmC: CaseClassParm[A,C],
  ) extends PrimaryKey[A,(B,C)] {
    import SqlString._
    val sqlStringerB = implicitly[SqlStringer[B]]
    val sqlStringerC = implicitly[SqlStringer[C]]
    override def whereClause(key: (B, C)): SqlString = {
      sql"${parmB.name.identifier} = ${sqlStringerB.toSqlString(key._1)} and ${parmC.name.identifier} = ${sqlStringerC.toSqlString(key._2)}"
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


    override def compositePrimaryKey2[C: JsonCodec : SqlStringer, D: JsonCodec: SqlStringer](fn: B => (CaseClassParm[A, C], CaseClassParm[A, D])): QubesMapperBuilder[A, B, (C, D)] = {
      val t = fn(generator.caseClassParameters)
      copy(primaryKey = Some(CompositePrimaryKey2(t._1, t._2)))
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
  def singlePrimaryKey[C : JsonCodec : SqlStringer](fn: B => CaseClassParm[A,C]): QubesMapperBuilder[A,B,C]
  def compositePrimaryKey2[C : JsonCodec : SqlStringer, D : JsonCodec : SqlStringer](fn: B => (CaseClassParm[A,C], CaseClassParm[A,D])): QubesMapperBuilder[A,B,(C,D)]
  def build: QubesKeyedMapper[A,PK]

}
