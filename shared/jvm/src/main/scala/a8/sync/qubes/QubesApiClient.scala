package a8.sync.qubes


import a8.shared.{CompanionGen, StringValue}
import a8.shared.jdbcf.SqlString
import a8.shared.json.ast._
import a8.shared.json.{JsonCodec, ast}
import a8.shared.SharedImports._
import a8.shared.app.{Logging, LoggingF}
import a8.shared.jdbcf.SqlString.SqlStringer
import a8.sync.http.{Backend, Method, RequestProcessor, RetryConfig}
import a8.sync.http
import a8.sync.qubes.MxQubesApiClient._
import sttp.client3._
import sttp.client3.logging.LogLevel
import sttp.model.Uri
import zio.{durationInt => _, _}

import scala.concurrent.duration.FiniteDuration

object QubesApiClient extends LoggingF {

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
    maximumSimultaneousHttpConnections: Int = 5,
//    readTimeout: FiniteDuration = Config.twentySeconds,
//    connectTimeout: FiniteDuration = Config.fiveSeconds,
    retry: RetryConfig = Config.defaultRetryConfig,
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
      .asResource(retry = config.retry, maxConnections = config.maximumSimultaneousHttpConnections)
      .map(rp => QubesApiClient(config, rp))
  }

  def sttpLogger: sttp.client3.logging.Logger[Task] = {
    import sttp.client3.logging.{ LogLevel => SttpLevel }
    import wvlet.log.{ LogLevel => WvletLevel }
    new sttp.client3.logging.Logger[Task] {
      override def apply(sttpLevel: sttp.client3.logging.LogLevel, message: => String): Task[Unit] = {
        sttpLevel match {
          case LogLevel.Trace =>
            loggerF.trace(message)
          case LogLevel.Debug =>
            loggerF.debug(message)
          case LogLevel.Info =>
            loggerF.info(message)
          case LogLevel.Warn =>
            loggerF.warn(message)
          case LogLevel.Error =>
            loggerF.error(message)
        }
      }
      override def apply(sttpLevel: LogLevel, message: => String, t: Throwable): Task[Unit] = {
        sttpLevel match {
          case LogLevel.Trace =>
            loggerF.trace(message, t)
          case LogLevel.Debug =>
            loggerF.debug(message, t)
          case LogLevel.Info =>
            loggerF.info(message, t)
          case LogLevel.Warn =>
            loggerF.warn(message, t)
          case LogLevel.Error =>
            loggerF.error(message, t)
        }
      }
    }
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
        .execWithString { responseBodyStr =>
          ZIO.suspend {
            json.parseF(responseBodyStr)
              .map(jv => JsDoc(jv))
          }
        }
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

  def fullQuery[A : JsonCodec](query: SqlString): Task[Iterable[A]] = {
    executeA[QueryRequest,JsDoc](uri"api/query", QueryRequest(query = query.toString))
      .flatMap { jsonDoc =>
        jsonDoc("data").value.asF[Iterable[A]]
      }
  }

  def query[A : QubesMapper](whereClause: SqlString): Task[Iterable[A]] = {
    val qm = implicitly[QubesMapper[A]]
    import qm.codecA
    executeA[QueryRequest,JsDoc](uri"api/query", qm.queryReq(whereClause))
      .flatMap { jsonDoc =>
        jsonDoc("data").value.asF[Iterable[A]]
      }
  }

  def fetch[A,B : SqlStringer](key: B)(implicit qubesKeyedMapper: QubesKeyedMapper[A,B]): Task[A] = {
    implicit def implicitQubesApiClient = this
    qubesKeyedMapper.fetch(key)
  }

  def insert[A : QubesMapper](row: A): Task[Unit] =
    processResponse(
      "insert",
      row,
      lowlevel.insert(implicitly[QubesMapper[A]].insertReq(row))
    )

  def update[A : QubesMapper](row: A): Task[Unit] =
    processResponse(
      "update",
      row,
      lowlevel.update(implicitly[QubesMapper[A]].updateReq(row))
    )

  def upsert[A : QubesMapper](row: A): Task[Unit] =
    processResponse(
      "upsert",
      row,
      lowlevel.upsert(implicitly[QubesMapper[A]].updateReq(row))
    )

  def delete[A : QubesMapper](row: A): Task[Unit] =
    processResponse(
      "delete",
      row,
      lowlevel.delete(implicitly[QubesMapper[A]].deleteReq(row))
    )

  protected def processResponse[A : QubesMapper](context: String, row: A, f: Task[UpdateRowResponse]): Task[Unit] =
    f.flatMap(_.asF(context)).flatMap {
      case 1 =>
        ZIO.unit
      case i =>
        ZIO.fail(new RuntimeException(s"expected to ${context} 1 row but affected ${i} rows instead -- ${implicitly[QubesMapper[A]].qualifiedName} ${row}"))
    }

}
