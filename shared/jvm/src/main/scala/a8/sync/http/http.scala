package a8.sync.http

import a8.common.logging.{Level, Logging}
import a8.shared.SharedImports.{*, given}
import a8.shared.ZString.ZStringer
import a8.shared.json.JsonReader.{JsonReaderOptions, JsonSource, ReadResult}
import a8.shared.json.ast.JsVal
import a8.shared.json.{JsonCodec, JsonReader, ReadError}
import a8.shared.zreplace.{Chunk, Resource, XStream}
import a8.shared.{CompanionGen, SharedImports, StringValue, ZString}
import a8.sync.Mxhttp.*
import a8.sync.http.http.Body.StringBody
import a8.sync.http.http.Request.ResponseAction
import a8.sync.http.http.impl.{RequestImpl, RequestProcessorImpl, standardResponseActionProcessor, standardResponseProcessor}
import a8.sync.http.http.{Body, Response}
import a8.sync.{Semaphore, SttpBackend}
import cats.data.Chain
import ox.scheduling.Jitter.Equal
import sttp.model.{StatusCode, Uri}

import java.net.URLEncoder
import java.nio.charset.Charset
import scala.concurrent.duration
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.*

object http extends Logging {

  object RetryConfig extends MxRetryConfig {
    val noRetries: RetryConfig = RetryConfig(0, 1.second, 1.minute)
    def toOxSchedule(rc: RetryConfig): ox.scheduling.Schedule = {
      ox.scheduling.Schedule.exponentialBackoff(rc.initialBackoff)
        .maxInterval(rc.maxBackoff)
        .maxRetries(rc.maxRetries)
        .jitter(Equal)
    }
  }

  object Attempt {
    case class Success[A](value: A) extends Attempt[A]
    case class Failure[A](throwable: Throwable) extends Attempt[A]
    case class Error[A](error: String) extends Attempt[A]
  }

  sealed trait Attempt[A]

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

  trait Backend {
    def runSingleRequest(request: RequestImpl): Attempt[Response]
  }


  case class InvalidHttpResponseStatusCode(statusCode: StatusCode, statusText: String, responseBody: String)
    extends Exception(s"${statusCode.code} ${statusText} -- ${responseBody}")

  object Request {
    def apply(baseUri: Uri): Request =
      impl.RequestImpl(baseUri)

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
          case ResponseAction.Retry(_) =>
            true
          case _ =>
            false
        }
    }
    object ResponseAction {
      case class Success[A](value: A) extends ResponseAction[A]
//      case class Redirect[A](location: Uri) extends ResponseAction[A]
      case class Fail[A](context: String, responseInfo: Option[ResponseInfo]) extends ResponseAction[A]
      case class Retry[A](context: String) extends ResponseAction[A]
    }
  }

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

  sealed trait Request {

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

    def execWithStringResponse(implicit processor: RequestProcessor, trace: Trace, logger: Logger): Attempt[String] =
      processor.exec(this)

    def execWithJsonResponse[A : JsonCodec](implicit processor: RequestProcessor, jsonResponseOptions: JsonResponseOptions, logger: Logger): A = {

      implicit val jsonReaderOptions: JsonReaderOptions = {
        if ( jsonResponseOptions.jsonWarningsLogLevel.equals(Level.Off) ) {
          JsonReaderOptions.NoLogWarnings
        } else {
          JsonReaderOptions.LogWarnings(jsonResponseOptions.jsonWarningsLogLevel, trace, logger)
        }
      }

      // some mildly unruly code because all of the JsonResponseOptions come together here
      def responseEffect(response: Response): ResponseAction[A] = {
        val responseBodyStr = response.responseBodyAsString

        def nonSuccessfulResponse(retry: Boolean, error: ReadError): ResponseAction[A] = {
          if (jsonResponseOptions.retryJsonParseErrors) {
            ResponseAction.Retry(error.prettyMessage)
          } else {
            ResponseAction.Fail(error.prettyMessage, ResponseInfo(response.responseMetadata, responseBodyStr.some).some)
          }
        }

        val responseAction: ResponseAction[A] =
          json.parse(responseBodyStr) match {
            case Left(parseError) =>
              nonSuccessfulResponse(jsonResponseOptions.retryJsonParseErrors, parseError)
            case Right(unvalidatedJsv) =>
              jsonResponseOptions.responseValidator(unvalidatedJsv) match {
                case r@ ResponseAction.Retry(_) =>
                  ResponseAction.Retry(r.context)
                case f@ ResponseAction.Fail(_, _) =>
                  ResponseAction.Fail(f.context, f.responseInfo)
                case ResponseAction.Success(validatedJsv) =>
                  JsonReader[A].readResult(validatedJsv.toRootDoc) match {
                    case rre: ReadResult.Error[?] =>
                      nonSuccessfulResponse(jsonResponseOptions.retryJsonCodecErrors, rre.readError)
                    case ReadResult.Success(a, _, _, _) =>
                      // happy path yay we made it
                      ResponseAction.Success(a)
                  }
              }
          }
        if (jsonResponseOptions.logJsonResponseBody) {
          logger.debug(s"response body --\n${responseBodyStr}")
        }
        responseAction
      }

      execWithEffect(standardResponseActionProcessor(responseEffect))
    }

    def execWithResponse[A](responseFn: Response=>Attempt[A])(using RequestProcessor, Logger): Attempt[A] =
      summon[RequestProcessor].execWithResponse[A](this, responseFn)

    def execWithString[A](fn: String=>Attempt[A])(using RequestProcessor, Logger): Attempt[A] =
      summon[RequestProcessor].execWithStringResponse(this, fn)

    /**
     * caller must supply a responseEffect that is responsible for things like checking http status codes, etc, etc
     */
    def execWithEffect[A](responseActionFn: Attempt[Response]=>ResponseAction[A])(using processor: RequestProcessor, logger: Logger): Attempt[A] =
      processor.execWithEffect[A](this, responseActionFn)

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
      !!!
//      if ( responseMetadata.statusCode.isSuccess ) {
//        zunit
//      } else {
//        asInvalidHttpResponseStatusCode
//      }

    def jsonResponseBody[A : JsonCodec](implicit jsonReaderOptions: JsonReaderOptions): Task[A] = {
      val jsonSource: JsonSource = responseBodyAsString
      JsonReader[A].read(jsonSource.withContext(s"response from ${request.uri}".some))
    }

    def responseBodyAsString: String = {
      charset
        .decode(responseBody.toByteBuffer)
        .toString
    }

    def asInvalidHttpResponseStatusCode[A]: Task[A] = {
      !!!
//      responseBodyAsString
//        .flatMap { body =>
//          zfail(InvalidHttpResponseStatusCode(responseMetadata.statusCode, responseMetadata.statusText, body))
//        }
    }
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

  sealed trait RequestProcessor {

    val backend: Backend

    def exec(request: Request)(implicit trace: Trace, logger: Logger): Attempt[String] =
      execWithStringResponse(request, s => zsucceed(s))

    /**
     * Will exec the request and map the response allowing for error's in the map'ing to
     * trigger a retry.
     *
     * This lifts the mapFn to run inside the scope for the retry so you can do things
     * like self manage recovery and further validating the response (like the response
     * may be json but is it the json you actually want).
     *
     */
    def execWithStringResponse[A](request: Request, responseBodyFn: String => Attempt[A])(using logger: Logger): Attempt[A] = {
      def responseEffect(response: Response): Attempt[A] = {
        response
          .raiseResponseErrors
          .map(Left(_))
          .getOrElse {
            responseBodyFn(response.responseBodyAsString)
          }
      }
      execWithEffect(request, standardResponseProcessor(responseEffect))
    }

    def execWithResponse[A](request: Request, responseFn: Response=>Attempt[A])(implicit trace: Trace, logger: Logger): Attempt[A] = {
      def wrappedResponseFn(response: Response): Attempt[A] = {
        response.raiseResponseErrors *> responseEffect(response)
      }
      execWithEffect(request, standardResponseProcessor(wrappedResponseEffect))
    }

    def execWithEffect[A](request: Request, responseEffect: Attempt[Response]=>Task[ResponseAction[A]])(implicit trace: Trace, logger: Logger): Task[A]

  }

  object RequestProcessor {

    def asResource(config: RequestProcessorConfig): Resource[RequestProcessor] = {
      def acquire: RequestProcessor = {
        val sttpBackend = sttp.client4.DefaultSyncBackend(sttp.client4.BackendOptions.Default.connectionTimeout(config.connectionTimeout))
        val semaphore = Semaphore(config.maxConnections)
        RequestProcessorImpl(config, SttpBackend(sttpBackend, config.readTimeout.some, config.retryConfig), semaphore)
      }
      !!!
      ???
    }

    def asResource(retry: RetryConfig = RetryConfig.noRetries, maxConnections: Int = 50): Resource[RequestProcessor] = {
      asResource(RequestProcessorConfig(retry.maxRetries, retry.initialBackoff, retry.maxBackoff, maxConnections))
    }

  }

  object impl {

    def standardResponseProcessor[A](responseProcessor: Response=>Either[Throwable,A])(implicit trace: Trace, logger: Logger): Either[Throwable,Response]=>ResponseAction[A] =
      { response =>
        standardResponseProcessorImpl(response, responseProcessor(response))
      }

    def standardResponseActionProcessor[A](effect: Response => ResponseAction[A])(implicit trace: Trace, logger: Logger): Either[Throwable, Response] => Task[ResponseAction[A]] = { responseE =>
      standardResponseProcessorImpl(responseE, effect)
    }

    lazy val retryableStatusCodes: Set[Int] = Set(429, 500, 502, 503, 504)

    def standardResponseProcessorImpl[A](responseE: Either[Throwable,Response], effect: Response=>Task[ResponseAction[A]])(implicit trace: Trace, logger: Logger): Task[ResponseAction[A]] = {
      responseE match {
        case Left(th) =>
          logger.debug("throwable during request", th)
          ResponseAction.Retry[A]("retry from throwable")
        case Right(response) =>
          response.responseMetadata.statusCode match {
            case sc if sc.isSuccess =>
              effect(response)
            case sc if retryableStatusCodes(sc.code) =>
              zsucceed(ResponseAction.Retry[A](s"http response ${sc.code} status received -- ${response.responseMetadata.compactJson}"))
            case sc =>
              val responseInfo = ResponseInfo(response.responseMetadata, Some(response.responseBodyAsString))
              ResponseAction.Fail[A](s"unable to process response ${responseInfo.compactJson}", responseInfo.some)
          }
      }
    }

    val contentTypeHeaderName: CIString = CIString("Content-Type")

    /** Removes quotes surrounding the charset.
     */
    def sanitizeCharset(charset: String): String = {
      val c2 = charset.trim()
      val begin = if (c2.startsWith("\"")) 1 else 0
      val end = if (c2.endsWith("\"")) (c2.length - 1) else c2.length
      c2.substring(begin, end)
    }

    def charsetFromContentType(contentType: String): Option[String] =
      contentType
        .split(";")
        .map(_.trim.toLowerCase)
        .collectFirst {
          case s if s.startsWith("charset=") && s.substring(8).trim != "" =>
            s.substring(8).trim
        }

    case class RequestProcessorImpl(
      config: RequestProcessorConfig,
      backend: Backend,
      maxConnectionSemaphore: Semaphore
    )
      extends RequestProcessor
    {

      import config.{maxConnections, retryConfig}

      override def execWithEffect[A](request: Request, responseEffect: Either[Throwable, Response] => ResponseAction[A])(using logger: Logger): Task[A] = {
        request match {
          case r0: RequestImpl =>
            runWithRetry(r0, responseEffect)
        }
      }

      def runWithRetry[A](request: RequestImpl, responseTestFn: Either[Throwable,Response]=>ResponseAction[A])(using logger: Logger): Attemp[A] = {

        val resolvedRetry = request.retryConfig.getOrElse(retryConfig)
        val schedule = RetryConfig.toOxSchedule(resolvedRetry)

        val resultPolicy =
          ox.resilience.ResultPolicy[Throwable,Response](
            isSuccess = responseTestFn(Right(_)).isSuccess,
            isWorthRetrying = responseTestFn(_).isRetry,
          )

        def afterAttempt(attempt: Int, response: Either[Throwable,Response]): Unit = {
          response match {
            case Left(th) =>
              logger.debug(s"retry attempt ${attempt} failed with throwable", th)
            case Right(response) =>
              responseTestFn(response) match {
                case ResponseAction.Success(_) =>
                  logger.debug(s"retry attempt ${attempt} succeeded with response ${response.responseMetadata.statusCode.code} -- ${response.responseMetadata.statusText}")
                case ResponseAction.Retry(_) =>
                  logger.debug(s"retry attempt ${attempt} will retry response ${response.responseMetadata.statusCode.code} -- ${response.responseMetadata.statusText}")
                case f@ ResponseAction.Fail(_, _) =>
                  logger.debug(s"retry attempt ${attempt} failed with response ${response.responseMetadata.statusCode.code} -- ${response.responseMetadata.statusText} -- context: ${f.context} -- responseInfo: ${f.responseInfo.map(_.compactJson).getOrElse("none")}")
              }
          }
        }

        val oxRetryConfig =
          ox.resilience.RetryConfig(
            schedule,
            resultPolicy,
            afterAttempt,
          )

        ox.resilience.retryEither[Throwable,Response](oxRetryConfig)(runSingleRequest(request))

      }

      def runSingleRequest[A](rawRequest: RequestImpl)(using logger: Logger): Either[Throwable,Response[A]] = {
        val rawRequestNoEffects: Request = rawRequest.copy(effects = Vector.empty)
        val resolvedRequest: Request =
          rawRequest
            .effects
            .foldLeft(rawRequestNoEffects)((r, fn) =>
              fn(r)
            )
        maxConnectionSemaphore
          .withPermit {
            backend.runSingleRequest(resolvedRequest.asInstanceOf[RequestImpl])
          }
      }

    }

    case class RequestImpl(
      uri: Uri,
      body: Body = Body.Empty,
      headers: Map[String, String] = Map.empty,
      method: Method = Method.GET,
      retryConfig: Option[RetryConfig] = None,
      effects: Vector[Request=>Request] = Vector.empty[Request=>Request],
      followRedirects: Boolean = true,
    ) extends Request {

      override def streamingRequestBody(requestBody: zio.XStream[Byte]): Request =
        copy(body = Body.StreamingBody(requestBody))

      def uri(uri: Uri): RequestImpl =
        copy(uri = uri)

      override def updateUri(updateFn: Uri => Uri): Request =
        copy(uri = updateFn(uri))

      override def noFollowRedirects: Request =
        copy(followRedirects = false)

      override def applyEffect(fn: Request => Request): Request =
        copy(effects = effects :+ fn)

      override def addHeader[A: ZStringer](name: String, value: A): Request =
        addHeader(name, ZStringer[A].toZString(value).toString)

      override def addHeader(name: String, value: String): RequestImpl =
        copy(headers = headers + (name -> value))

      override def body[A](a: A)(implicit toBodyFn: A => Body): RequestImpl =
        copy(body = toBodyFn(a))

      override def withRetryConfig(retryConfig: RetryConfig): Request =
        copy(retryConfig = retryConfig.some)

      override def subPath(subPath: Uri): Request =
        uri(uri.addPathSegments(subPath.pathSegments.segments))

      override def jsonBody(json: JsVal): Request =
        addHeader("Content-Type", "application/json")
          .copy(
            body = Body.JsonBody(json),
          )

      override def addQueryParm[A: ZStringer](name: String, value: A): Request =
        addQueryParm(name, ZStringer[A].toZString(value).toString)

      override def addQueryParm(name: String, value: String): Request =
        uri(uri.addParam(name, value))

      override def method(m: Method): Request =
        copy(method = m)

      override lazy val curlCommand: String = {
        Chain[String]("curl")
          .concat(Chain.fromSeq(headers.toVector.map(h => s"  -H '${h._1}: ${h._2}'")))
          .concat(
            body match {
              case Body.Empty | Body.StreamingBody(_) =>
                Chain.empty[String]
              case Body.StringBody(b) =>
                Chain.one(s"  --data '${b}'")
              case Body.JsonBody(b) =>
                Chain.one(s"  --data '${b.compactJson}'")
            }
          )
          .append(s"  -X ${method.value}")
          .append(s"  '${uri.toString()}'")
          .toVector
          .mkString(" \\\n")
      }

      override def formBody(fields: Iterable[(String, String)]): Request = {
        def encode(text: String): String = URLEncoder.encode(text, "UTF-8")
        val encodedFormBody =
          fields
            .map(t => encode(t._1) + "=" + encode(t._2))
            .mkString("&")
        copy(
          body = StringBody(encodedFormBody),
          headers = headers + ("Content-Type" -> "application/x-www-form-urlencoded"),
        )
      }
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

}
