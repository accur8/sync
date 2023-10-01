package a8.sync.qubes

import a8.shared.SharedImports.*
import a8.shared.jdbcf.SqlString.*
import a8.shared.jdbcf.{SqlString, TableName}
import a8.shared.json.ZJsonReader.ZJsonReaderOptions
import a8.shared.json.{JsonObjectCodec, JsonTypedCodec}
import a8.shared.json.ast.JsObj
import a8.sync.qubes.QubesApiClient.{QueryRequest, UpdateRowRequest}
import a8.sync.qubes.QubesMapperBuilder.{Parm, PrimaryKey}
import zio.*

object QubesMapper {

  implicit object QubesEscaper extends Escaper {

    override def unsafeSqlEscapeStringValue(value: String): String =
      "'" + value.replace("'","''") + "'"

    override def unsafeSqlQuotedIdentifier(identifier: String): String =
      identifier

  }

  case class QubesMapperImpl[A,B](
    cubeName: TableName,
    appSpace: String,
    rawParms: Vector[Parm[A]],
    primaryKey: PrimaryKey[A,B],
  )(
    implicit
      jsonTypedCodec: JsonTypedCodec[A, JsObj]
  )
    extends QubesKeyedMapper[A,B]
  {

    val codecA: JsonTypedCodec[A,JsObj] = implicitly[JsonTypedCodec[A, JsObj]]


    lazy val parms: Vector[Parm[A]] = rawParms.sortBy(_.ordinal)

    // validate ordinals
    parms.zipWithIndex.find(t => t._1.ordinal != t._2) match {
      case Some(parm) =>
        sys.error(s"ordinal mismatch at ${parm}")
      case None =>
      // success
    }


    override def fetch(key: B)(implicit sqlStringer: SqlStringer[B], qubesApiClient: QubesApiClient, jsonReaderOptions: ZJsonReaderOptions): Task[A] =
      fetchOpt(key)
        .flatMap {
          case None =>
            ZIO.fail(new RuntimeException(s"no record ${key} found in ${cubeName}"))
          case Some(i) =>
            ZIO.succeed(i)
        }

    override def fetchOpt(key: B)(implicit sqlStringer: SqlStringer[B], qubesApiClient: QubesApiClient, jsonReaderOptions: ZJsonReaderOptions): Task[Option[A]] = {
      implicit def qm: QubesMapperImpl[A, B] = this
      import SqlString._
      qubesApiClient
        .query[A](primaryKey.whereClause(key))
        .map(_.headOption)
    }

    override def queryReq(whereClause: SqlString): QueryRequest =
      QueryRequest(
        query = sql"from ${cubeName} select ${parms.map(p => sql"${p.columnName} as ${p.columnName}").mkSqlString(SqlString.Comma)} where ${whereClause}".compile.value,
        dataFormat = QueryRequest.verbose,
        appSpace = Some(appSpace),
      )

    override def insertReq(row: A, parameters: Seq[UpdateRowRequest.Parm] = Vector()): UpdateRowRequest =
      updateRowRequest(row, parameters)

    override def updateReq(row: A, parameters: Seq[UpdateRowRequest.Parm] = Vector()): UpdateRowRequest =
      updateRowRequest(row, parameters)

    override def deleteReq(row: A, parameters: Seq[UpdateRowRequest.Parm] = Vector()): UpdateRowRequest =
      updateRowRequest(row, parameters)

    def updateRowRequest(row: A, parameters: Seq[UpdateRowRequest.Parm] = Vector()): UpdateRowRequest =
      UpdateRowRequest(
        cube = cubeName.asString,
        appSpace = Some(appSpace),
        fields = codecA.write(row).asObject.getOrElse(sys.error("this will never happen")),
        parameters = parameters,
      )

  }

}

/**

 needs to have the apps space and cube name

*/
trait QubesMapper[A] {

  implicit val codecA: JsonTypedCodec[A, JsObj]
  val cubeName: TableName
  val appSpace: String

  def qualifiedName: String = appSpace + "/" + cubeName.asString

  def queryReq(whereClause: SqlString): QueryRequest
  def insertReq(a: A, parameters: Seq[UpdateRowRequest.Parm] = Vector.empty[UpdateRowRequest.Parm]): UpdateRowRequest
  def updateReq(a: A, parameters: Seq[UpdateRowRequest.Parm] = Vector.empty[UpdateRowRequest.Parm]): UpdateRowRequest
  def deleteReq(a: A, parameters: Seq[UpdateRowRequest.Parm] = Vector.empty[UpdateRowRequest.Parm]): UpdateRowRequest

}