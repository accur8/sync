package a8.sync.qubes

import a8.shared.{CompanionGen, StringValue}
import a8.shared.jdbcf.SqlString
import a8.shared.json.ast._
import a8.shared.json.{JsonCodec, ast}
import a8.shared.SharedImports._
import a8.shared.app.Logging
import a8.shared.jdbcf.SqlString.SqlStringer
import a8.sync.http.{Backend, Method, RequestProcessor, RetryConfig}
import a8.sync.http
import a8.sync.qubes.MxQubesApiClient._
import cats.effect.kernel.Deferred
import org.asynchttpclient.AsyncHttpClient
import sttp.client3._
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.client3.logging.LogLevel
import sttp.model.Uri
import wvlet.log.LazyLogger

import scala.concurrent.duration.FiniteDuration

object QubesApiClient extends Logging {

  def apply[F[_] : QubesApiClient]: QubesApiClient[F] =
    implicitly[QubesApiClient[F]]

  object Config extends MxConfig {
    val fiveSeconds = 5.seconds
    val twentySeconds = 20.seconds
    val defaultRetryConfig =
      RetryConfig(
        count = 0,
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
    readTimeout: FiniteDuration = Config.twentySeconds,
    connectTimeout: FiniteDuration = Config.fiveSeconds,
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
//    def asF[F[_] : Async]: F[Int] = {
//      val F = Async[F]
//      if (success) {
//        F.pure(numberOfRowsUpdated)
//      } else {
//        validationFailures match {
//          case Some(jv) =>
//            F.raiseError(new RuntimeException("Qubes crud validation error: " + jv.compactJson))
//          case None =>
//            F.raiseError(new RuntimeException("Qubes crud error: " + errorMessage.getOrError("None")))
//        }
//      }
//    }
    def asF[F[_] : Async](ctx: String): F[Int] = {
      val F = Async[F]
      if (success) {
        F.pure(numberOfRowsUpdated)
      } else {
        validationFailures match {
          case Some(jv) =>
            F.raiseError(new RuntimeException(s"${ctx} - Qubes crud validation error: " + jv.compactJson))
          case None =>
            F.raiseError(new RuntimeException(s"${ctx} - Qubes crud error: " + errorMessage.getOrError("None")))
        }
      }
    }

  }

  def asResource[F[_] : Async](config: Config): Resource[F,QubesApiClient[F]] = {
    val F = Async[F]

    def acquire: F[AsyncHttpClient] =
      F.delay {
        import org.asynchttpclient.Dsl
        Dsl.asyncHttpClient(
          Dsl.config()
            .setConnectTimeout(config.connectTimeout.toMillis.toInt)
            .setReadTimeout(config.readTimeout.toMillis.toInt)
            .setRequestTimeout(config.readTimeout.toMillis.toInt)
            .setMaxConnections(config.maximumSimultaneousHttpConnections)
            .build()
        )
      }

    val asyncHttpClientR = Resource.make(acquire)(client => F.delay(client.close()))

    for {
      asyncHttpClient <- asyncHttpClientR
      maxConnectionSemaphore <- Resource.eval(Semaphore[F](config.maximumSimultaneousHttpConnections))
      dispatcher <- Dispatcher[F]
    } yield {
      import sttp.client3.logging._
      val sttpBackend = AsyncHttpClientFs2Backend.usingClient[F](asyncHttpClient, dispatcher)
      val sttpLogger = QubesApiClient.sttpLogger[F]
      val loggingBackend = LoggingBackend(delegate = sttpBackend, logger = sttpLogger, beforeCurlInsteadOfShow = true)
      new QubesApiClient[F](config, Backend(loggingBackend), maxConnectionSemaphore)
    }
  }

  def sttpLogger[F[_] : Async]: sttp.client3.logging.Logger[F] = {
    import sttp.client3.logging.{ LogLevel => SttpLevel }
    import wvlet.log.{ LogLevel => WvletLevel }
    new sttp.client3.logging.Logger[F] {
      override def apply(sttpLevel: sttp.client3.logging.LogLevel, message: => String): F[Unit] = {
        Async[F].blocking {
          sttpLevel match {
            case LogLevel.Trace =>
              logger.trace(message)
            case LogLevel.Debug =>
              logger.debug(message)
            case LogLevel.Info =>
              logger.info(message)
            case LogLevel.Warn =>
              logger.warn(message)
            case LogLevel.Error =>
              logger.error(message)
          }
        }
      }
      override def apply(sttpLevel: LogLevel, message: => String, t: Throwable): F[Unit] = {
        Async[F].blocking {
          sttpLevel match {
            case LogLevel.Trace =>
              logger.trace(message, t)
            case LogLevel.Debug =>
              logger.debug(message, t)
            case LogLevel.Info =>
              logger.info(message, t)
            case LogLevel.Warn =>
              logger.warn(message, t)
            case LogLevel.Error =>
              logger.error(message, t)
          }
        }
      }
    }
  }


}

class QubesApiClient[F[_] : Async](
  config: QubesApiClient.Config,
  val backend: Backend[F],
  maxConnectionSemaphore: Semaphore[F],
) extends LazyLogger {

  import QubesApiClient._

  object impl {
    val F = Async[F]

    implicit lazy val requestProcessor: RequestProcessor[F] = RequestProcessor(config.retry, backend, maxConnectionSemaphore)
    lazy val baseRequest = http.Request(config.uri).addHeader("X-SESS", config.authToken.value)

    def executeA[A: JsonCodec, B: JsonCodec](subPath: Uri, requestBody: A): F[B] =
      F.defer {

        val request =
          baseRequest
            .subPath(subPath)
            .method(Method.POST)
            .jsonBody(requestBody.toJsVal)


        request.execWithJsonResponse[F,B]
      }

    def execute[A: JsonCodec](subPath: Uri, requestBody: A): F[JsDoc] = {
      val request =
        baseRequest
          .subPath(subPath)
          .jsonBody(requestBody.toJsVal)
      logger.trace(s"${request.method.value} ${request.uri}")

      request.execWithString { responseBodyStr =>
          F.defer {
            json.parseF(responseBodyStr)
              .map(jv => JsDoc(jv))
          }
        }
    }

  }
  import impl._

  object lowlevel {

    def query(request: QueryRequest): F[JsDoc] =
      execute(uri"api/query", request)

    def insert(request: UpdateRowRequest): F[UpdateRowResponse] =
      impl.executeA(uri"api/insert", request)

    def update(request: UpdateRowRequest): F[UpdateRowResponse] =
      executeA(uri"api/updateRow", request)

    def upsert(request: UpdateRowRequest): F[UpdateRowResponse] =
      executeA(uri"api/upsertRow", request)

    def delete(request: UpdateRowRequest): F[UpdateRowResponse] =
      executeA(uri"api/delete", request)

  }

  def fullQuery[A : JsonCodec](query: SqlString): F[Iterable[A]] = {
    executeA[QueryRequest,JsDoc](uri"api/query", QueryRequest(query = query.toString))
      .flatMap { jsonDoc =>
        jsonDoc("data").value.asF[F,Iterable[A]]
      }
  }

  def query[A : QubesMapper](whereClause: SqlString): F[Iterable[A]] = {
    val qm = implicitly[QubesMapper[A]]
    import qm.codecA
    executeA[QueryRequest,JsDoc](uri"api/query", qm.queryReq(whereClause))
      .flatMap { jsonDoc =>
        jsonDoc("data").value.asF[F,Iterable[A]]
      }
  }

  def fetch[A,B : SqlStringer](key: B)(implicit qubesKeyedMapper: QubesKeyedMapper[A,B]): F[A] = {
    implicit def implicitQubesApiClient = this
    qubesKeyedMapper.fetch(key)
  }

  def insert[A : QubesMapper](row: A): F[Unit] =
    processResponse(
      "insert",
      row,
      lowlevel.insert(implicitly[QubesMapper[A]].insertReq(row))
    )

  def update[A : QubesMapper](row: A): F[Unit] =
    processResponse(
      "update",
      row,
      lowlevel.update(implicitly[QubesMapper[A]].updateReq(row))
    )

  def upsert[A : QubesMapper](row: A): F[Unit] =
    processResponse(
      "upsert",
      row,
      lowlevel.upsert(implicitly[QubesMapper[A]].updateReq(row))
    )

  def delete[A : QubesMapper](row: A): F[Unit] =
    processResponse(
      "delete",
      row,
      lowlevel.delete(implicitly[QubesMapper[A]].deleteReq(row))
    )

  protected def processResponse[A : QubesMapper](context: String, row: A, f: F[UpdateRowResponse]): F[Unit] =
    f.flatMap(_.asF(context)).flatMap {
      case 1 =>
        F.unit
      case i =>
        F.raiseError[Unit](new RuntimeException(s"expected to ${context} 1 row but affected ${i} rows instead -- ${implicitly[QubesMapper[A]].qualifiedName} ${row}"))
    }

}
