package a8.sync.qubes


import a8.shared.{CompanionGen, StringValue}
import a8.shared.jdbcf.SqlString
import a8.shared.json.ast._
import a8.shared.json.{JsonCodec, ast}
import a8.shared.SharedImports._
import a8.shared.app.{Logging, LoggingF}
import a8.shared.jdbcf.SqlString.SqlStringer
import a8.shared.json.ZJsonReader.ZJsonReaderOptions
import a8.sync.http.{Backend, Method, RequestProcessor, RequestProcessorConfig, RetryConfig}
import a8.sync.http
import a8.sync.qubes.MxQubesApiClient._
import sttp.client3._
import sttp.client3.logging.LogLevel
import sttp.model.Uri
import zio.{& => _, durationInt => _, _}

import scala.concurrent.duration.FiniteDuration

object QubesApiClient extends LoggingF {

  lazy val layer = ZLayer(constructor)

  lazy val constructor: ZIO[Scope & Config, Throwable, QubesApiClient] = {
    for {
      config <- zservice[Config]
      client <- asResource(config)
    } yield client
  }

  object Config extends MxConfig {
    val fiveSeconds = 5.seconds
    val twentySeconds = 20.seconds
    val defaultRetryConfig =
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
  }
  @CompanionGen()
  case class UpdateRowRequest(
    cube: String,
    fields: JsObj,
    parameters: Vector[JsDoc] = Vector.empty,
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
    def asF(ctx: String): Task[Int] = {
      if (success) {
        ZIO.succeed(numberOfRowsUpdated)
      } else {
        validationFailures match {
          case Some(jv) =>
            ZIO.fail(new RuntimeException(s"${ctx} - Qubes crud validation error: " + jv.compactJson))
          case None =>
            ZIO.fail(new RuntimeException(s"${ctx} - Qubes crud error: " + errorMessage.getOrError("None")))
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

    implicit lazy val implicitRequestProcessor = requestProcessor
    lazy val baseRequest = http.Request(config.uri).addHeader("X-SESS", config.authToken.value)

    def executeA[A: JsonCodec, B: JsonCodec](subPath: Uri, requestBody: A): Task[B] = {
      ZIO.suspend {

        val request =
          baseRequest
            .subPath(subPath)
            .method(Method.POST)
            .jsonBody(requestBody.toJsVal)


        request.execWithJsonResponse[B]
      }
    }

    def execute[A: JsonCodec](subPath: Uri, requestBody: A): Task[JsDoc] = {
      val request =
        baseRequest
          .subPath(subPath)
          .jsonBody(requestBody.toJsVal)

      loggerF.trace(s"${request.method.value} ${request.uri}") *>
        request
          .execWithJsonResponse[JsDoc]
    }

  }
  import impl._

  object lowlevel {

    def query(request: QueryRequest): Task[JsDoc] =
      execute(uri"api/query", request)

    def insert(request: UpdateRowRequest): Task[UpdateRowResponse] =
      impl.executeA[UpdateRowRequest, UpdateRowResponse](uri"api/insert", request)

    def update(request: UpdateRowRequest): Task[UpdateRowResponse] =
      executeA[UpdateRowRequest, UpdateRowResponse](uri"api/updateRow", request)

    def upsert(request: UpdateRowRequest): Task[UpdateRowResponse] =
      executeA[UpdateRowRequest, UpdateRowResponse](uri"api/upsertRow", request)

    def delete(request: UpdateRowRequest): Task[UpdateRowResponse] =
      executeA[UpdateRowRequest, UpdateRowResponse](uri"api/delete", request)

  }

  def fullQuery[A : JsonCodec](query: SqlString)(implicit jsonReaderOptions: ZJsonReaderOptions): Task[Iterable[A]] = {
    executeA[QueryRequest,JsDoc](uri"api/query", QueryRequest(query = query.toString))
      .flatMap { jsonDoc =>
        jsonDoc("data").value.asF[Iterable[A]]
      }
  }

  def query[A : QubesMapper](whereClause: SqlString)(implicit jsonReaderOptions: ZJsonReaderOptions): Task[Iterable[A]] = {
    val qm = implicitly[QubesMapper[A]]
    import qm.codecA
    executeA[QueryRequest,JsDoc](uri"api/query", qm.queryReq(whereClause))
      .flatMap { jsonDoc =>
        jsonDoc("data").value.asF[Iterable[A]]
      }
  }

  def fetch[A,B : SqlStringer](key: B)(implicit qubesKeyedMapper: QubesKeyedMapper[A,B], jsonReaderOptions: ZJsonReaderOptions): Task[A] = {
    implicit def implicitQubesApiClient = this
    qubesKeyedMapper.fetch(key)
  }

  def insert[A : QubesMapper](row: A): Task[A] =
    processResponse(
      "insert",
      row,
      lowlevel.insert(implicitly[QubesMapper[A]].insertReq(row))
    ).as(row)

  def update[A : QubesMapper](row: A): Task[A] =
    processResponse(
      "update",
      row,
      lowlevel.update(implicitly[QubesMapper[A]].updateReq(row))
    ).as(row)

  def upsert[A : QubesMapper](row: A): Task[A] =
    processResponse(
      "upsert",
      row,
      lowlevel.upsert(implicitly[QubesMapper[A]].updateReq(row))
    ).as(row)

  def delete[A : QubesMapper](row: A): Task[A] =
    processResponse(
      "delete",
      row,
      lowlevel.delete(implicitly[QubesMapper[A]].deleteReq(row))
    ).as(row)

  protected def processResponse[A : QubesMapper](context: String, row: A, f: Task[UpdateRowResponse]): Task[Unit] =
    f.flatMap(_.asF(context)).flatMap {
      case 1 =>
        ZIO.unit
      case i =>
        ZIO.fail(new RuntimeException(s"expected to ${context} 1 row but affected ${i} rows instead -- ${implicitly[QubesMapper[A]].qualifiedName} ${row}"))
    }

}
