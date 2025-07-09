package a8.sync.http

import a8.common.logging.{Level, Logging}
import a8.shared.SharedImports.{*, given}
import a8.shared.ZString.ZStringer
import a8.shared.json.JsonReader.{JsonReaderOptions, JsonSource, ReadResult}
import a8.shared.json.ast.JsVal
import a8.shared.json.{JsonCodec, JsonReader, ReadError}
import a8.shared.zreplace.{Chunk, Resource, XStream}
import a8.shared.{CompanionGen, SharedImports, StringValue, ZString}
import Mxhttp.*
import a8.sync.Semaphore
import cats.data.Chain
import ox.scheduling.Jitter.Equal
import sttp.model.{StatusCode, Uri}

import java.net.URLEncoder
import java.nio.charset.Charset
import scala.concurrent.duration
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.*
import a8.shared.zreplace.Chunk.toByteBuffer
import a8.sync.http.impl.RequestProcessorImpl

object RetryConfig extends MxRetryConfig {
  val noRetries: RetryConfig = RetryConfig(0, 1.second, 1.minute)
  def toOxSchedule(rc: RetryConfig): ox.scheduling.Schedule = {
    ox.scheduling.Schedule.exponentialBackoff(rc.initialBackoff)
      .maxInterval(rc.maxBackoff)
      .maxRetries(rc.maxRetries)
      .jitter(Equal)
  }
}

sealed trait HttpError

case class HttpFailure(th: Throwable) extends Exception(th) with HttpError
case class HttpException(message: String) extends Exception(message) with HttpError


sealed trait Result[A] {
  def toEither: Either[Throwable, A] =
    this match {
      case Result.Success(value) =>
        Right(value)
      case Result.Failure(message, error) =>
        Left(error.getOrElse(new Exception(message.getOrElse("Unknown error"))))
    }
}

object Result {
  case class Success[A](value: A) extends Result[A]
  case class Failure[A](message: Option[String] = None, error: Option[Throwable] = None) extends Result[A]
}

@CompanionGen
case class RetryConfig(
  maxRetries: Int = 5,
  initialBackoff: FiniteDuration = 1.second,
  maxBackoff: FiniteDuration = 1.minute,
) {
}

object RequestProcessorConfig extends MxRequestProcessorConfig {
  val noRetries: RequestProcessorConfig = RequestProcessorConfig(0, 1.second, 1.minute)
  val default: RequestProcessorConfig = RequestProcessorConfig()
}

@CompanionGen
case class RequestProcessorConfig(
  maxRetries: Int = 5,
  initialBackoff: FiniteDuration = 1.second,
  maxBackoff: FiniteDuration = 1.minute,
  maxConnections: Int = 50,
  jitterFactor: Double = 0.2, // 0.0 is no jitter
  backoffFactor: Double = 1.2, // 2.0 doubles (i.e. squares) backoff after each retry
  connectionTimeout: FiniteDuration = 15.seconds,
  readTimeout: FiniteDuration = 30.seconds,
) {
  lazy val retryConfig: RetryConfig = RetryConfig(maxRetries, initialBackoff, maxBackoff)
}

/**
 * The backend is responsible for the actual going out into the real world and making the http request.
 */
trait Backend {
  def runSingleRequest(request: Request): Result[Response]
  def shutdown(): Unit
}


sealed trait ResponseAction[A] {
  def isSuccess: Boolean =
    this match {
      case ResponseAction.Success(_) =>
        true
      case _ =>
        false
    }
  def isRetry: Boolean =
    this match {
      case ResponseAction.Retryable(_) =>
        true
      case _ =>
        false
    }
}
object ResponseAction {
  case class Success[A](value: A) extends ResponseAction[A]
//      case class Redirect[A](location: Uri) extends ResponseAction[A]
  case class Fail[A](context: String, responseInfo: Option[ResponseInfo]) extends ResponseAction[A]
  case class Retryable[A](context: String) extends ResponseAction[A]
}

case class InvalidHttpResponseStatusCode(statusCode: StatusCode, statusText: String, responseBody: String)
  extends Exception(s"${statusCode.code} ${statusText} -- ${responseBody}")


object JsonResponseOptions {
  implicit val default: JsonResponseOptions = JsonResponseOptions()
}
case class JsonResponseOptions(
  logJsonResponseBody: Boolean = true,
  retryJsonParseErrors: Boolean = false,
  retryJsonCodecErrors: Boolean = false,
  responseValidator: JsVal => ResponseAction[JsVal] = jsv => ResponseAction.Success(jsv),
  jsonWarningsLogLevel: Level = Level.Debug,
)

object ResponseInfo extends MxResponseInfo
@CompanionGen
case class ResponseInfo(
  metadata: ResponseMetadata,
  responseBody: Option[String],
)

object Request {
  def apply(baseUri: Uri): Request =
    impl.RequestImpl(baseUri)
}

trait Request {

  val uri: Uri
  val body: Body
  val method: Method
  val headers: Map[String,String]
  val followRedirects: Boolean
  val retryConfig: Option[RetryConfig]

  def subPath(subPath: Uri): Request
  def appendPath(parts: ZString*): Request =
    updateUri(_.addPath(parts.map(_.toString)))

  def withRetryConfig(retryConfig: RetryConfig): Request

  def jsonBody[A : JsonCodec](a: A): Request = jsonBody(a.toJsVal)
  def jsonBody(json: JsVal): Request
  def addHeader(name: String, value: String): Request
  def addHeader[A: ZStringer](name: String, value: A): Request
  def addQueryParm(name: String, value: String): Request
  def addQueryParm[A: ZStringer](name: String, value: A): Request
  def body[A](a: A)(implicit toBodyFn: A => Body): Request
  def method(m: Method): Request
  def uri(uri: Uri): Request
  def updateUri(updateFn: Uri=>Uri): Request

  def noFollowRedirects: Request
  def applyEffect(fn: Request=>Request): Request

  def formBody(fields: Iterable[(String,String)]): Request

  def execWithStringResponse(using RequestProcessor, Logger): String
  def execWithJsonResponse[A : JsonCodec](using RequestProcessor, JsonResponseOptions, Logger): A
  def execWithResponseActionFn[A](responseFn: Response=>ResponseAction[A])(using RequestProcessor, Logger): A

  def curlCommand: String

  def streamingRequestBody(requestBody: XStream[Byte]): Request

}

case class Response(
  request: Request,
  responseMetadata: ResponseMetadata,
  responseBody: Chunk[Byte],
) {

  lazy val charset: Charset =
    responseMetadata
      .contentType
      .flatMap(impl.charsetFromContentType)
      .map(impl.sanitizeCharset)
      .map(Charset.forName)
      .getOrElse(Utf8Charset)

  def raiseResponseErrors: Option[Throwable] =
      if ( responseMetadata.statusCode.isSuccess ) {
        None
      } else {
        Some(asInvalidHttpResponseStatusCode)
      }

  def jsonResponseBody[A : JsonCodec](implicit jsonReaderOptions: JsonReaderOptions): A = {
    val jsonSource: JsonSource = responseBodyAsString
    JsonReader[A].read(jsonSource.withContext(s"response from ${request.uri}".some))
  }

  def responseBodyAsString: String = {
    charset
      .decode(responseBody.toByteBuffer)
      .toString
  }

  def asInvalidHttpResponseStatusCode: InvalidHttpResponseStatusCode =
    InvalidHttpResponseStatusCode(responseMetadata.statusCode, responseMetadata.statusText, responseBodyAsString)
}

object Method extends StringValue.Companion[Method] {
  val DELETE: Method = Method("DELETE")
  val GET: Method = Method("GET")
  val HEAD: Method = Method("HEAD")
  val MOVE: Method = Method("MOVE")
  val OPTIONS: Method = Method("OPTIONS")
  val PATCH: Method = Method("PATCH")
  val POST: Method = Method("POST")
  val PUT: Method = Method("PUT")
}

case class Method(
  value: String
) extends StringValue

object ResponseMetadata extends MxResponseMetadata {
}

@CompanionGen
case class ResponseMetadata(
  statusCodeInt: Int,
  statusText: String,
  headers: Vector[(String,String)],
) {

  lazy val statusCode: StatusCode = StatusCode(statusCodeInt)

  lazy val headersByName: Map[CIString,String] =
    headers
      .map(h => CIString(h._1) -> h._2)
      .toMap

  lazy val contentType: Option[String] = headersByName.get(impl.contentTypeHeaderName)

}

/**
 * A RequestProcessor is responsible for executing requests and returning responses.
 * It may handle retries, connection management, and other aspects of request execution.
 *
 * It does not do the actual real world request execution, that is the responsibility of the Backend.
 *
 * Generally we want the backend doing the one task of executing a single request in isolation and the
 * RequestProcessor to be the one that manages the retry logic, connection management, etc.
 *
 */
trait RequestProcessor {

  val backend: Backend

//  def exec(request: Request)(using Logger): String

  def execWithStringResponseBody[A](request: Request, responseBodyFn: String => A)(using Logger): A

  def execWithJsonResponse[A: JsonCodec](request: Request)(using JsonResponseOptions, Logger): A

  //  def execWithResponseFn[A](request: Request, responseFn: Response=>Result[A])(using Logger): A

  /**
   * Will exec the request and map the response allowing for error's in the map'ing to
   * trigger a retry.
   *
   * This lifts the mapFn to run inside the scope for the retry so you can do things
   * like self manage recovery and further validating the response (like the response
   * may be json but is it the json you actually want).
   *
   */
  def execWithResponseActionFn[A](request: Request, responseFn: Either[Throwable,Response]=>ResponseAction[A])(using Logger): A

}

object RequestProcessor {

  def asResource(config: RequestProcessorConfig): Resource[RequestProcessor] = {
    def acquire: RequestProcessorImpl = {
      val sttpBackend = sttp.client4.DefaultSyncBackend(sttp.client4.BackendOptions.Default.connectionTimeout(config.connectionTimeout))
      val semaphore = Semaphore(config.maxConnections)
      RequestProcessorImpl(config, SttpBackend(sttpBackend, config.readTimeout.some, config.retryConfig), semaphore)
    }
    Resource.acquireRelease(acquire)(_.shutdown())
  }

  def asResource(retry: RetryConfig = RetryConfig.noRetries, maxConnections: Int = 50): Resource[RequestProcessor] = {
    asResource(RequestProcessorConfig(retry.maxRetries, retry.initialBackoff, retry.maxBackoff, maxConnections))
  }

}

sealed trait Body {
  def isStream: Boolean = false
}
object Body {
  case object Empty extends Body
  case class StringBody(content: String) extends Body
  case class JsonBody(content: JsVal) extends Body
  case class BytesBody(content: Chunk[Byte]) extends Body
  case class StreamingBody(stream: XStream[Byte]) extends Body {
    override def isStream: Boolean = true
  }
  given [A <: Body, B <: Body]: CanEqual[A,B] = CanEqual.derived
}
