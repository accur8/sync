package a8.sync.qubes


import a8.shared.{CompanionGen, StringValue}
import a8.shared.jdbcf.SqlString
import a8.shared.json.ast.*
import a8.shared.json.{JsonCodec, ast}
import a8.shared.SharedImports.*
import a8.common.logging.Logging
import a8.shared.jdbcf.SqlString.SqlStringer
import a8.shared.json.JsonReader.JsonReaderOptions
import a8.shared.zreplace.Resource
import a8.sync.http
import a8.sync.http.{Backend, Method, RequestProcessor, RequestProcessorConfig, RetryConfig}
import a8.sync.qubes.MxQubesApiClient.*
import a8.sync.qubes.QubesApiClient.UpdateRowRequest.Parm
import sttp.client4.*
import sttp.model.*

import scala.concurrent.duration.FiniteDuration

object QubesApiClient extends Logging {

  def constructor(config: Config): Resource[QubesApiClient] =
    asResource(config)

  object Config extends MxConfig {
    val fiveSeconds: FiniteDuration = 5.seconds
    val twentySeconds: FiniteDuration = 20.seconds
    val defaultRetryConfig: RetryConfig =
      RetryConfig(
        maxRetries = 0,
        initialBackoff = 2.seconds,
        maxBackoff = 10.seconds,
      )
  }

  object AuthToken extends StringValue.Companion[AuthToken] {
  }
  case class AuthToken(value: String) extends StringValue

  @CompanionGen()
  case class Config(
    uri: Uri,
    authToken: AuthToken,
    requestProcessor: RequestProcessorConfig = RequestProcessorConfig.default,
  )

  object QueryRequest extends MxQueryRequest {
    val verbose = "verbose"
    val concise = "concise"
  }

  @CompanionGen()
  case class QueryRequest(
    query: String,
    dataFormat: String = QueryRequest.verbose,
    appSpace: Option[String] = None,
  )

  object UpdateRowRequest extends MxUpdateRowRequest {
    object Parm extends MxParm
    @CompanionGen()
    case class Parm(
      dataType: Option[String] = None,
      cube: Option[String] = None,
      field: Option[String] = None,
      value: String
    )
  }
  @CompanionGen()
  case class UpdateRowRequest(
    cube: String,
    fields: JsObj,
    parameters: Iterable[Parm] = Iterable(),
    where: Option[String] = None,
    appSpace: Option[String] = None,
  )

  object UpdateRowResponse extends MxUpdateRowResponse {
  }
  @CompanionGen()
  case class UpdateRowResponse(
    success: Boolean,
    validationFailures: Option[JsObj] = None,
    errorMessage: Option[String] = None,
    serverStackTrace: Option[String] = None,
    numberOfRowsUpdated: Int = 0,
    keys: JsObj = JsObj.empty,
  ) {
    def as(ctx: String): Int = {
      if (success) {
        numberOfRowsUpdated
      } else {
        validationFailures match {
          case Some(jv) =>
            throw new RuntimeException(s"${ctx} - Qubes crud validation error: " + jv.compactJson)
          case None =>
            throw new RuntimeException(s"${ctx} - Qubes crud error: " + errorMessage.getOrError("None"))
        }
      }
    }

  }

  def asResource(config: QubesApiClient.Config): Resource[QubesApiClient] = {
    RequestProcessor
      .asResource(config.requestProcessor)
      .map(rp => QubesApiClient(config, rp))
  }

}

case class QubesApiClient(
  config: QubesApiClient.Config,
  requestProcessor: RequestProcessor,
) extends Logging {

  import QubesApiClient._

  object impl {

    implicit lazy val implicitRequestProcessor: RequestProcessor = requestProcessor
    lazy val baseRequest: http.Request = http.Request(config.uri).addHeader("X-SESS", config.authToken.value)

    def executeA[A: JsonCodec, B: JsonCodec](subPath: Uri, requestBody: A): B = {

      val request =
        baseRequest
          .subPath(subPath)
          .method(Method.POST)
          .jsonBody(requestBody.toJsVal)


      request.execWithJsonResponse[B]

    }

    def execute[A: JsonCodec](subPath: Uri, requestBody: A): JsDoc = {
      val request =
        baseRequest
          .subPath(subPath)
          .jsonBody(requestBody.toJsVal)

      logger.trace(s"${request.method.value} ${request.uri}")

      request
        .execWithJsonResponse[JsDoc]

    }

  }
  import impl._

  object lowlevel {

    def query(request: QueryRequest): JsDoc =
      execute(uri"api/query", request)

    def insert(request: UpdateRowRequest): UpdateRowResponse =
      impl.executeA[UpdateRowRequest, UpdateRowResponse](uri"api/insert", request)

    def update(request: UpdateRowRequest): UpdateRowResponse =
      executeA[UpdateRowRequest, UpdateRowResponse](uri"api/updateRow", request)

    def upsert(request: UpdateRowRequest): UpdateRowResponse =
      executeA[UpdateRowRequest, UpdateRowResponse](uri"api/upsertRow", request)

    def delete(request: UpdateRowRequest): UpdateRowResponse =
      executeA[UpdateRowRequest, UpdateRowResponse](uri"api/delete", request)

  }

  def fullQuery[A : JsonCodec](query: SqlString)(implicit jsonReaderOptions: JsonReaderOptions): Iterable[A] = {
    val jsDoc = executeA[QueryRequest,JsDoc](uri"api/query", QueryRequest(query = query.toString))
    jsDoc("data").value.unsafeAs[Iterable[A]]
  }

  def query[A : QubesMapper](whereClause: SqlString)(implicit jsonReaderOptions: JsonReaderOptions): Iterable[A] = {
    val qm = implicitly[QubesMapper[A]]
    import qm.codecA
    val jsDoc = executeA[QueryRequest,JsDoc](uri"api/query", qm.queryReq(whereClause))
    jsDoc("data").value.unsafeAs[Iterable[A]]
  }

  def fetch[A,B : SqlStringer](key: B)(implicit qubesKeyedMapper: QubesKeyedMapper[A,B], jsonReaderOptions: JsonReaderOptions): A = {
    implicit def implicitQubesApiClient: QubesApiClient = this
    qubesKeyedMapper.fetch(key)
  }

  def insert[A : QubesMapper](row: A, parameters: Parm*): A = {
    processResponse(
      "insert",
      row,
      lowlevel.insert(implicitly[QubesMapper[A]].insertReq(row, parameters))
    )
    row
  }

  def update[A : QubesMapper](row: A, parameters: Parm*): A = {
    processResponse(
      "update",
      row,
      lowlevel.update(implicitly[QubesMapper[A]].updateReq(row, parameters))
    )
    row
  }

  def upsert[A : QubesMapper](row: A, parameters: Parm*): A = {
    processResponse(
      "upsert",
      row,
      lowlevel.upsert(implicitly[QubesMapper[A]].updateReq(row, parameters))
    )
    row
  }

  def delete[A : QubesMapper](row: A, parameters: Parm*): A = {
    processResponse(
      "delete",
      row,
      lowlevel.delete(implicitly[QubesMapper[A]].deleteReq(row, parameters))
    )
    row
  }

  protected def processResponse[A : QubesMapper](context: String, row: A, updateRowResponse: UpdateRowResponse): Unit =
    updateRowResponse.as(context) match {
      case 1 =>
        ()
      case i =>
        throw new RuntimeException(s"expected to ${context} 1 row but affected ${i} rows instead -- ${implicitly[QubesMapper[A]].qualifiedName} ${row}")
    }

}
