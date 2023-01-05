package a8.sync


import a8.shared.{CompanionGen, SharedImports, StringValue, ZString}
import a8.shared.json.{JsonCodec, JsonReader, ReadError, ZJsonReader}
import a8.shared.json.ast.JsVal
import a8.sync.Mxhttp._
import sttp.model.{StatusCode, Uri}
import a8.shared.SharedImports._
import a8.shared.ZString.ZStringer
import a8.shared.app.{LoggerF, Logging, LoggingF}
import a8.shared.json.JsonReader.ReadResult
import a8.shared.json.ZJsonReader.{ZJsonReaderOptions, ZJsonSource}
import a8.sync.http.{Body, Response}
import a8.sync.http.Body.StringBody
import a8.sync.http.Request.ResponseAction
import a8.sync.http.impl.{RequestImpl, RequestProcessorImpl, standardResponseActionProcessor, standardResponseProcessor}
import cats.data.Chain
import sttp.capabilities.zio.ZioStreams
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.client3.internal.{charsetFromContentType, sanitizeCharset}
import sttp.{capabilities, client3}
import sttp.client3.{Identity, RequestT, ResponseAs, basicRequest}
import wvlet.log.LogLevel
import zio.Schedule.{WithState, succeed}
import zio.{LogLevel => _, durationInt => _, _}
import zio.stream.{ZPipeline, ZSink}

import java.net.URLEncoder
import java.nio.charset.Charset
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._

object http extends LoggingF {

  object RetryConfig extends MxRetryConfig {
    val noRetries = RetryConfig(0, 1.second, 1.minute)
  }

  @CompanionGen
  case class RetryConfig(
    maxRetries: Int,
    initialBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
  )

  object RequestProcessorConfig extends MxRequestProcessorConfig

  @CompanionGen
  case class RequestProcessorConfig(
    retry: RetryConfig,
    maxConnections: Int,
  )

  trait Backend {
    def rawSingleExec(request: RequestImpl): ZIO[Scope,Throwable, Response]
  }

  case class SttpBackend(
    sttpBackend: sttp.client3.SttpBackend[Task, ZioStreams],
//    replayableStreamFactory: ReplayableStream.Factory
  )
    extends Backend
  {
    override def rawSingleExec(request: RequestImpl): ZIO[Scope, Throwable, Response] = {

      type SttpRequest = RequestT[Identity, (XStream[Byte], ResponseMetadata), capabilities.Effect[Task] with ZioStreams]

      def requestWithBody(r0: SttpRequest): SttpRequest =
        request.body match {
          case Body.StreamingBody(stream) =>
            r0.streamBody(ZioStreams)(stream)
          case Body.Empty =>
            r0
          case Body.StringBody(content) =>
            r0.body(content)
          case Body.JsonBody(json) =>
            r0.body(json.compactJson)
        }

      def requestWithFollowRedirects(r0: SttpRequest): SttpRequest =
        r0.followRedirects(request.followRedirects)

      val responseAs: ResponseAs[(XStream[Byte], ResponseMetadata), ZioStreams] =
        client3
          .asStreamAlwaysUnsafe(ZioStreams)
          .mapWithMetadata { (stream, sttpResponseMetadata) =>
            stream -> ResponseMetadata(sttpResponseMetadata)
          }

      val request0: SttpRequest =
        basicRequest
          .method(sttp.model.Method(request.method.value), request.uri)
          .headers(request.headers)
          .response(responseAs)

      val requestUpdaters: Vector[SttpRequest=>SttpRequest] =
        Vector(requestWithBody, requestWithFollowRedirects)

      val resolvedSttpRequest: SttpRequest = requestUpdaters.foldLeft(request0)((r0,fn) => fn(r0))

      val startTime = java.lang.System.currentTimeMillis()

      resolvedSttpRequest
        .send(sttpBackend)
        .flatMap { response =>
//          replayableStreamFactory
//            .makeReplayable(response.body._1)
//            .map { replayableStream =>
//            }
//            .tap { _ =>
          loggerF.trace(s"${request.method.value} ${request.uri} completed in ${java.lang.System.currentTimeMillis() - startTime} ms -- ${response.code} ${response.statusText}")
            .as(
              Response(
                request,
                response.body._2,
                response.body._1,
              )
            )
        }
        .onError { error =>
           loggerF.debug(s"error with http request -- \n${request.curlCommand}", error)
        }
    }
  }

  case class InvalidHttpResponseStatusCode(statusCode: StatusCode, statusText: String, responseBody: String)
    extends Exception(s"${statusCode.code} ${statusText} -- ${responseBody}")

  object Request {
    def apply(baseUri: Uri): Request =
      impl.RequestImpl(baseUri)

    sealed trait ResponseAction[A]
    object ResponseAction {
      case class Success[A](value: A) extends ResponseAction[A]
//      case class Redirect[A](location: Uri) extends ResponseAction[A]
      case class Fail[A](context: String, responseInfo: Option[ResponseInfo]) extends ResponseAction[A]
      case class Retry[A](context: String) extends ResponseAction[A]
    }
  }

  object JsonResponseOptions {
    implicit val default = JsonResponseOptions()
  }
  case class JsonResponseOptions(
    logJsonResponseBody: Boolean = true,
    retryJsonParseErrors: Boolean = false,
    retryJsonCodecErrors: Boolean = false,
    responseValidator: JsVal => UIO[ResponseAction[JsVal]] = jsv => ZIO.succeed(ResponseAction.Success(jsv)),
    jsonWarningsLogLevel: LogLevel = LogLevel.DEBUG,
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
    def applyEffect(fn: Request=>Task[Request]): Request

    def formBody(fields: Iterable[(String,String)]): Request

    def execWithStringResponse(implicit processor: RequestProcessor, trace: Trace, loggerF: LoggerF): Task[String] =
      processor.exec(this)

    def execWithJsonResponse[A : JsonCodec](implicit processor: RequestProcessor, jsonResponseOptions: JsonResponseOptions, trace: Trace, loggerF: LoggerF): Task[A] = {

      implicit val jsonReaderOptions =
        if ( jsonResponseOptions.jsonWarningsLogLevel == LogLevel.OFF) {
          ZJsonReaderOptions.NoLogWarnings
        } else {
          ZJsonReaderOptions.LogWarnings(jsonResponseOptions.jsonWarningsLogLevel, trace, loggerF)
        }

      // some mildly unruly code because all of the JsonResponseOptions come together here
      def responseEffect(response: Response): Task[ResponseAction[A]] = {
        response
          .responseBodyAsString
          .flatMap { responseBodyStr =>

            def nonSuccessfulResponse(retry: Boolean, error: ReadError): ResponseAction[A] = {
              if (jsonResponseOptions.retryJsonParseErrors) {
                ResponseAction.Retry(error.prettyMessage)
              } else {
                ResponseAction.Fail(error.prettyMessage, ResponseInfo(response.responseMetadata, responseBodyStr.some).some)
              }
            }


            val responseActionEffect: Task[ResponseAction[A]] =
              json.parse(responseBodyStr) match {
                case Left(parseError) =>
                  ZIO.succeed(
                    nonSuccessfulResponse(jsonResponseOptions.retryJsonParseErrors, parseError)
                  )
                case Right(unvalidatedJsv) =>
                  jsonResponseOptions
                    .responseValidator(unvalidatedJsv)
                    .map {
                      case r@ ResponseAction.Retry(_) =>
                        ResponseAction.Retry(r.context)
                      case f@ ResponseAction.Fail(_, _) =>
                        ResponseAction.Fail(f.context, f.responseInfo)
                      case ResponseAction.Success(validatedJsv) =>
                        JsonReader[A].readResult(validatedJsv.toRootDoc) match {
                          case rre: ReadResult.Error[_] =>
                            nonSuccessfulResponse(jsonResponseOptions.retryJsonCodecErrors, rre.readError)
                          case ReadResult.Success(a, _, _, _) =>
                            // happy path yay we made it
                            ResponseAction.Success(a)
                        }
                    }
              }
            for {
              _ <-
                if (jsonResponseOptions.logJsonResponseBody) {
                  loggerF.debug(s"response body --\n${responseBodyStr}")
                } else {
                  ZIO.unit
                }
              ra <- responseActionEffect
            } yield ra
          }
      }

      execWithEffect(standardResponseActionProcessor(responseEffect))
    }

    def execWithResponseEffect[A](responseEffect: Response=>Task[A])(implicit processor: RequestProcessor, trace: Trace, loggerF: LoggerF): Task[A] =
      processor.execWithResponseEffect[A](this, responseEffect)

    def execWithStringEffect[A](effect: String=>Task[A])(implicit processor: RequestProcessor, trace: Trace, loggerF: LoggerF): Task[A] =
      processor.execWithStringResponse(this, effect)

    /**
     * caller must supply a responseEffect that is responsible for things like checking http status codes, etc, etc
     */
    def execWithEffect[A](responseEffect: Either[Throwable,Response]=>Task[ResponseAction[A]])(implicit processor: RequestProcessor, trace: Trace, loggerF: LoggerF): Task[A] =
      processor.execWithEffect[A](this, responseEffect)

    def curlCommand: String

    def streamingRequestBody(requestBody: XStream[Byte]): Request

  }

  case class Response(
    request: Request,
    responseMetadata: ResponseMetadata,
    responseBody: XStream[Byte],
  ) {

    lazy val charset: Charset =
      responseMetadata
        .contentType
        .flatMap(impl.charsetFromContentType)
        .map(impl.sanitizeCharset)
        .map(Charset.forName)
        .getOrElse(Utf8Charset)

    def raiseResponseErrors: Task[Unit] =
      if ( responseMetadata.statusCode.isSuccess ) {
        ZIO.unit
      } else {
        asInvalidHttpResponseStatusCode
      }

    def jsonResponseBody[A : JsonCodec](implicit jsonReaderOptions: ZJsonReaderOptions): Task[A] =
      responseBodyAsString
        .flatMap { jsonStr =>
          val jsonSource: ZJsonSource = jsonStr
          ZJsonReader[A].read(jsonSource.withContext(s"response from ${request.uri}".some))
        }

    def responseBodyAsString: Task[String] =
      responseBody
        .via(ZPipeline.decodeCharsWith(charset))
        .run(ZSink.mkString)

    def asInvalidHttpResponseStatusCode[A]: Task[A] = {
      responseBodyAsString
        .flatMap { body =>
          ZIO.fail(InvalidHttpResponseStatusCode(responseMetadata.statusCode, responseMetadata.statusText, body))
        }
    }
  }

  object Method extends StringValue.Companion[Method] {
    val GET = Method("GET")
    val HEAD = Method("HEAD")
    val DELETE = Method("DELETE")
    val OPTIONS = Method("OPTIONS")
    val PATCH = Method("PATCH")
    val POST = Method("POST")
    val PUT = Method("PUT")
  }

  case class Method(
    value: String
  ) extends StringValue

  object ResponseMetadata extends MxResponseMetadata {

    def apply[A](sttpResponse: sttp.client3.Response[A]): ResponseMetadata =
      ResponseMetadata(
        sttpResponse.code.code,
        sttpResponse.statusText,
        sttpResponse.headers.map(h => h.name -> h.value).toVector,
      )

    def apply[A](sttpResponseMetadata: sttp.model.ResponseMetadata): ResponseMetadata =
      ResponseMetadata(
        sttpResponseMetadata.code.code,
        sttpResponseMetadata.statusText,
        sttpResponseMetadata.headers.map(h => h.name -> h.value).toVector,
      )

  }

  @CompanionGen
  case class ResponseMetadata(
    statusCodeInt: Int,
    statusText: String,
    headers: Vector[(String,String)],
  ) {

    lazy val statusCode = StatusCode(statusCodeInt)

    lazy val headersByName =
      headers
        .map(h => CIString(h._1) -> h._2)
        .toMap

    lazy val contentType: Option[String] = headersByName.get(impl.contentTypeHeaderName)

  }

  sealed trait RequestProcessor {

    val backend: Backend

    def exec(request: Request)(implicit trace: Trace, loggerF: LoggerF): Task[String] =
      execWithStringResponse(request, s => ZIO.succeed(s))

    /**
     * Will exec the request and map the response allowing for error's in the map'ing to
     * trigger a retry.
     *
     * This lifts the mapFn to run inside the scope for the retry so you can do things
     * like self manage recovery and further validating the response (like the response
     * may be json but is it the json you actually want).
     *
     */
    def execWithStringResponse[A](request: Request,  effect: String => Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A] = {
      def responseEffect(response: Response): Task[A] = {
        for {
          _ <- response.raiseResponseErrors
          responseBodyAsString <- response.responseBodyAsString
          a <- effect(responseBodyAsString)
        } yield a
      }
      execWithEffect(request, standardResponseProcessor(responseEffect))
    }

    def execWithResponseEffect[A](request: Request, responseEffect: Response=>Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A] = {
      def wrappedResponseEffect(response: Response): Task[A] = {
        response.raiseResponseErrors *> responseEffect(response)
      }
      execWithEffect(request, standardResponseProcessor(wrappedResponseEffect))
    }

    def execWithEffect[A](request: Request, responseEffect: Either[Throwable,Response]=>Task[ResponseAction[A]])(implicit trace: Trace, loggerF: LoggerF): Task[A]

  }

  object RequestProcessor {

    lazy val layer =
      ZLayer(
        for {
          config <- zservice[RequestProcessorConfig]
          requestProcessor <- asResource(config.retry, config.maxConnections)
        } yield requestProcessor
      )

    def asResource(retry: RetryConfig = RetryConfig.noRetries, maxConnections: Int = 50): Resource[RequestProcessor] = {
      for {
        sttpBackend <-sttp.client3.armeria.zio.ArmeriaZioBackend.scoped()
        maxConnectionSemaphore <- Semaphore.make(maxConnections)
      } yield
        RequestProcessorImpl(retry, SttpBackend(sttpBackend), maxConnectionSemaphore)
    }


    def apply(retry: RetryConfig, backend: Backend, maxConnectionSemaphore: Semaphore): RequestProcessor =
      RequestProcessorImpl(retry, backend, maxConnectionSemaphore)

  }

  object impl {

    def standardResponseProcessor[A](effect: Response=>Task[A])(implicit trace: Trace, loggerF: LoggerF): Either[Throwable,Response]=>Task[ResponseAction[A]] =
      { responseE =>
        standardResponseProcessorImpl(responseE, effect.map(_.map(ResponseAction.Success(_))))
      }

    def standardResponseActionProcessor[A](effect: Response => Task[ResponseAction[A]])(implicit trace: Trace, loggerF: LoggerF): Either[Throwable, Response] => Task[ResponseAction[A]] = { responseE =>
      standardResponseProcessorImpl(responseE, effect)
    }

    lazy val retryableStatusCodes = Set(429, 500, 502, 503, 504)

    def standardResponseProcessorImpl[A](responseE: Either[Throwable,Response], effect: Response=>Task[ResponseAction[A]])(implicit trace: Trace, loggerF: LoggerF): Task[ResponseAction[A]] = {
      responseE match {
        case Left(th) =>
          loggerF.debug("throwable during request", th)
            .as(ResponseAction.Retry[A]("retry from throwable"))
        case Right(response) =>
          response.responseMetadata.statusCode match {
            case sc if sc.isSuccess =>
              effect(response)
            case sc if retryableStatusCodes(sc.code) =>
              ZIO.succeed(ResponseAction.Retry[A](s"http response ${sc.code} status received -- ${response.responseMetadata.compactJson}"))
            case sc =>
              response
                .responseBodyAsString
                .map { responseBodyStr =>
                  val responseInfo = ResponseInfo(response.responseMetadata, responseBodyStr.some)
                  ResponseAction.Fail[A](s"unable to process response ${responseInfo.compactJson}", responseInfo.some)
                }
          }
      }
    }

    val contentTypeHeaderName = CIString("Content-Type")

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
      retryConfig: RetryConfig,
      backend: Backend,
      maxConnectionSemaphore: Semaphore
    )
      extends RequestProcessor
    {


      override def execWithEffect[A](request: Request, responseEffect: Either[Throwable, Response] => Task[ResponseAction[A]])(implicit trace: Trace, loggerF: LoggerF): Task[A] = {
        request match {
          case r0: RequestImpl =>
            runWithRetry(r0, responseEffect)
        }
      }

      def runWithRetry[A](request: RequestImpl, responseEffect: Either[Throwable,Response]=>Task[ResponseAction[A]])(implicit trace: Trace, loggerF: LoggerF): Task[A] = {
        def impl(retryNumber: Int, backoff: FiniteDuration): Task[A] = {
          val resolvedBackoff = List(backoff, retryConfig.maxBackoff).min

          val rawSingleEffect =
            rawSingleExec(request, responseEffect)
              .flatMap {
                case ResponseAction.Success(a) =>
                  ZIO.succeed(a)
                case ResponseAction.Retry(context) =>
                  if (retryNumber <= retryConfig.maxRetries) {
                    for {
                      _ <- loggerF.info(s"${context} will retry in ${resolvedBackoff}")
                      _ <- ZIO.sleep(resolvedBackoff.toZio)
                      responseValue <- impl(retryNumber + 1, resolvedBackoff * 2)
                    } yield responseValue
                  } else {
                    ZIO.fail(new RuntimeException(s"reached retry limit tried ${retryNumber} times"))
                  }
                case f@ ResponseAction.Fail(msg, rm) =>
                  ZIO.fail(new RuntimeException(s"received ${rm.map(_.metadata.statusCode)} -- ${msg}"))
              }

          ZIO.scoped(rawSingleEffect)

        }
        impl(0, retryConfig.initialBackoff)
      }

      def rawSingleExec[A](rawRequest: RequestImpl, responseEffect: Either[Throwable,Response]=>Task[ResponseAction[A]])(implicit trace: Trace, loggerF: LoggerF): Resource[ResponseAction[A]] = {
        val rawRequest0: Task[Request] = ZIO.succeed(rawRequest: Request)
        for {
          request0 <- rawRequest.effects.foldLeft(rawRequest0)((r, effect) => r.flatMap(effect))
          request = request0 match { case r0: RequestImpl => r0 }
          _ <- loggerF.debug(s"http request${if ( request.body.isStream ) "(with streaming request body)" else ""}\n${request.curlCommand.indent("    ")}")
          response <-
            maxConnectionSemaphore
              .withPermit {
                backend
                  .rawSingleExec(request)
                  .either
                  .flatMap(responseEffect)
              }
        } yield response
      }

    }

    case class RequestImpl(
      uri: Uri,
      body: Body = Body.Empty,
      headers: Map[String, String] = Map.empty,
      method: Method = Method.GET,
      retryConfig: Option[RetryConfig] = None,
      effects: Vector[Request=>Task[Request]] = Vector.empty,
      followRedirects: Boolean = true,
    ) extends Request {


      override def streamingRequestBody(requestBody: SharedImports.XStream[Byte]): Request =
        copy(body = Body.StreamingBody(requestBody))

      def uri(uri: Uri): RequestImpl =
        copy(uri = uri)

      override def updateUri(updateFn: Uri => Uri): Request =
        copy(uri = updateFn(uri))

      override def noFollowRedirects: Request =
        copy(followRedirects = false)

      override def applyEffect(fn: Request => Task[Request]): Request =
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
    case class StreamingBody(stream: XStream[Byte]) extends Body {
      override def isStream: Boolean = true
    }
  }

}
