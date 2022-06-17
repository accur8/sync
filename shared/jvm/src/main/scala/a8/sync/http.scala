package a8.sync


import a8.shared.{CompanionGen, ZString}
import a8.shared.json.JsonCodec
import a8.shared.json.ast.JsVal
import a8.sync.Mxhttp.{MxResponseMetadata, MxRetryConfig}
import sttp.model.{StatusCode, Uri}
import a8.shared.SharedImports._
import a8.shared.ZString.ZStringer
import a8.shared.app.{LoggerF, Logging, LoggingF}
import a8.sync.http.Body
import a8.sync.http.Body.StringBody
import a8.sync.http.impl.RequestProcessorImpl
import cats.data.Chain
import sttp.capabilities.zio.ZioStreams
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.client3.internal.{charsetFromContentType, sanitizeCharset}
import sttp.{capabilities, client3}
import sttp.client3.{Identity, RequestT, ResponseAs, basicRequest}
import zio.Schedule.WithState
import zio.{durationInt => _, _}
import zio.stream.{ZPipeline, ZSink}

import java.net.URLEncoder
import java.nio.charset.Charset
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._

object http extends LoggingF {

  case class Backend(
    delegate: sttp.client3.SttpBackend[Task, ZioStreams with capabilities.WebSockets]
  )

  object RetryConfig extends MxRetryConfig {
    val noRetries = RetryConfig(0, 1.second, 1.minute)
  }

//  type RetryPolicy = WithState[(Long, Long), Any, Any, (zio.Duration, Long)]
  type RetryPolicy = Schedule[Any, Any, (zio.Duration, Long)]

  @CompanionGen
  case class RetryConfig(
    count: Int,
    initialBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
  ) {
    lazy val retryPolicy: RetryPolicy = {
      val exponential = Schedule.exponential(initialBackoff.toJava)
      val retry = Schedule.recurs(count)
      exponential && retry
    }
  }

  def applyRetryPolicy[A](retryPolicy: RetryPolicy, context: String, effect: Task[A]): Task[A] =
    for {
      counter <- Ref.make(1)
      a <-
        effect
          .onError { cause =>
            for {
              tryNumber <- counter.get
              _ <- loggerF.debug(s"try number ${tryNumber} failed", cause)
              _ <- counter.update(_ + 1)
            } yield ()
          }
          .retry(retryPolicy)
    } yield a

  case class InvalidHttpResponseStatusCode(statusCode: StatusCode, statusText: String, responseBody: String)
    extends Exception(s"${statusCode.code} ${statusText} -- ${responseBody}")

  object Request {
    def apply(baseUri: Uri): Request =
      impl.RequestImpl(baseUri)
  }

  sealed trait Request {

    val uri: Uri
    val body: Body
    val method: Method
    val headers: Map[String,String]

    val retryConfig: Option[RetryConfig]

    def subPath(subPath: Uri): Request

    def withRetryConfig(retryConfig: RetryConfig): Request

    def jsonBody[A : JsonCodec](a: A): Request = jsonBody(a.toJsVal)
    def jsonBody(json: JsVal): Request
    def addHeader(name: String, value: String): Request
    def addHeader[A: ZStringer](name: String, value: A): Request
    def addQueryParm(name: String, value: String): Request
    def addQueryParm[A: ZStringer](name: String, value: A): Request
    def body[A](a: A)(implicit toBodyFn: A => Body): Request
    def method(m: Method): Request

    def formBody(fields: Iterable[(String,String)]): Request

    def exec[F[_]](implicit processor: RequestProcessor): Task[String] =
      processor.exec(this)

    def execWithJsonResponse[A : JsonCodec](implicit processor: RequestProcessor): Task[A] =
      processor.execWithStringResponse(this, None, responseJson => json.readF[A](responseJson))

    def execWithStreamResponse[A](responseEffect: Response=>Task[A])(implicit processor: RequestProcessor): Task[A] =
      processor.execWithStreamResponse[A](this, None, responseEffect)

    def execWithString[F[_],A](effect: String=>Task[A])(implicit processor: RequestProcessor): Task[A] =
      processor.execWithStringResponse(this, None, effect)

    /**
     * implementer of the responseEffect function must raise errors into the F.
     * implementer is responsible for things like checking http status codes, etc, etc
     */
    def execRaw[A](responseEffect: Response=>Task[A])(implicit processor: RequestProcessor): Task[A] =
      processor.execRaw[A](this, None, responseEffect)

    def curlCommand: String

    def streamingBody(requestBody: XStream[Byte]): StreamingRequest =
      StreamingRequest(this, Some(requestBody))

    def streaming: StreamingRequest =
      StreamingRequest(this)

  }

  case class StreamingRequest(rawRequest: Request, requestBody: Option[XStream[Byte]] = None) {

    def requestBody(requestBody: XStream[Byte]): StreamingRequest =
      copy(requestBody = Some(requestBody))

    def exec(implicit processor: RequestProcessor): Task[String] =
      processor.exec(rawRequest, requestBody)

    def execRaw[A](responseEffect: Response=>Task[A])(implicit processor: RequestProcessor): Task[A] =
      processor.execRaw[A](rawRequest, requestBody, responseEffect)

    def execWithStreamingResponse[A](responseEffect: Response=>Task[A])(implicit processor: RequestProcessor): Task[A] =
      processor.execWithStreamResponse[A](rawRequest, requestBody, responseEffect)

    def execWithJsonResponse[A : JsonCodec](implicit processor: RequestProcessor): Task[A] =
      processor.execWithStringResponse(rawRequest, requestBody, responseJson => json.readF[A](responseJson))

    def execWithEffect[A](effect: String=>Task[A])(implicit processor: RequestProcessor): Task[A] =
      processor.execWithStringResponse(rawRequest, requestBody, effect)

    def updateRawRequest(fn: Request => Request): StreamingRequest =
      copy(rawRequest = fn(rawRequest))

  }

  case class Response(
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

    def jsonResponseBody[A : JsonCodec]: Task[A] =
      responseBodyAsString
        .flatMap(json.readF[A])

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

  object Method {
    val GET = Method("GET")
    val PATCH = Method("PATCH")
    val POST = Method("POST")
    val PUT = Method("PUT")
  }

  case class Method(
    value: String
  )

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

    def exec(request: Request, streamingRequestBody: Option[XStream[Byte]] = None)(implicit trace: Trace, loggerF: LoggerF): Task[String]

    /**
     * Will exec the request and map the response allowing for error's in the map'ing to
     * trigger a retry.
     *
     * This lifts the mapFn to run inside the scope for the retry so you can do things
     * like self manage recovery and further validating the response (like the response
     * may be json but is it the json you actually want).
     *
     */
    def execWithStringResponse[A](request: Request, streamingRequestBody: Option[XStream[Byte]] = None, effect: String => Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A]

    def execWithStreamResponse[A](request: Request, streamingRequestBody: Option[XStream[Byte]] = None, responseEffect: Response=>Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A]

    /**
     *
     * implementer of the responseEffect function must raise errors into the F.
     * implementer is responsible for things like checking http status codes, etc, etc
     *
     */
    def execRaw[A](request: Request, streamingRequestBody: Option[XStream[Byte]] = None, responseEffect: Response=>Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A]

  }

  object RequestProcessor {

    def asResource(retry: RetryConfig = RetryConfig.noRetries, maxConnections: Int = 50): Resource[RequestProcessor] = {
      for {
        sttpBackend <-HttpClientZioBackend.scoped()
        maxConnectionSemaphore <- Semaphore.make(maxConnections)
      } yield
        RequestProcessorImpl(retry, Backend(sttpBackend), maxConnectionSemaphore)
    }


    def apply(retry: RetryConfig, backend: Backend, maxConnectionSemaphore: Semaphore) =
      RequestProcessorImpl(retry, backend, maxConnectionSemaphore)

  }

  object impl {

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

      lazy val defaultRetryPolicy = retryConfig.retryPolicy

      override def exec(request: Request, streamingRequestBody: Option[XStream[Byte]])(implicit trace: Trace, loggerF: LoggerF): Task[String] =
        execWithStringResponse(request, streamingRequestBody, s => ZIO.succeed(s))

      override def execRaw[A](request: Request, streamingRequestBody: Option[XStream[Byte]], responseEffect: Response=>Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A] = {
        val retryPolicy =
          request
            .retryConfig
            .map(_.retryPolicy)
            .getOrElse(defaultRetryPolicy)
        applyRetryPolicy(retryPolicy, request.uri.toString(), rawSingleExec(request, streamingRequestBody, responseEffect))
      }


      override def execWithStringResponse[A](request: Request, streamingRequestBody: Option[XStream[Byte]], effect: String => Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A] = {
        def responseEffect(response: Response): Task[A] = {
          for {
            _ <- response.raiseResponseErrors
            responseBodyAsString <- response.responseBodyAsString
            a <- effect(responseBodyAsString)
          } yield a
        }
        execRaw(request, streamingRequestBody, responseEffect)
      }

      override def execWithStreamResponse[A](request: Request, streamingRequestBody: Option[XStream[Byte]], responseEffect: Response=>Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A] = {
        def wrappedResponseEffect(response: Response): Task[A] = {
          response.raiseResponseErrors *> responseEffect(response)
        }
        execRaw(request, streamingRequestBody, wrappedResponseEffect)
      }

      def rawSingleExec[A](request: Request, streamingRequestBody: Option[XStream[Byte]], responseEffect: Response=>Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A] = {
        maxConnectionSemaphore
          .withPermit {

            def wrappedEffect(stream: XStream[Byte], sttpResponseMetadata: sttp.model.ResponseMetadata): Task[A] = {
              val responseMetadata = ResponseMetadata(sttpResponseMetadata)
              responseEffect(Response(responseMetadata, stream))
            }

            val request0: RequestT[Identity, A, Any with capabilities.Effect[Task] with ZioStreams] =
              basicRequest
                .method(sttp.model.Method(request.method.value), request.uri)
                .headers(request.headers)
                .response(client3.asStreamAlwaysWithMetadata(ZioStreams)(wrappedEffect))

            val requestWithBody: RequestT[Identity, A, Any with capabilities.Effect[Task] with ZioStreams] =
              (streamingRequestBody, request.body) match {
                case (Some(stream), _) =>
                  request0.streamBody(ZioStreams)(stream)
                case (_, Body.Empty) =>
                  request0
                case (_, Body.StringBody(content)) =>
                  request0.body(content)
                case (_, Body.JsonBody(json)) =>
                  request0.body(json.compactJson)
              }

            val startTime = java.lang.System.currentTimeMillis()

            val effect =
              requestWithBody
                .send(backend.delegate)
                .flatMap { response =>
                  loggerF.trace(s"${request.method.value} ${request.uri} completed in ${java.lang.System.currentTimeMillis() - startTime} ms -- ${response.code} ${response.statusText}")
                    .as(response.body)
                }
                .onError { error =>
                  loggerF.debug(s"error with http request -- \n${request.curlCommand}", error)
                }

            loggerF.debug(s"running request ${streamingRequestBody.nonEmpty.toOption("(has streaming request body)").getOrElse("")}\n${request.curlCommand.indent("    ")}") *> effect

          }
      }

    }

    case class RequestImpl(
      uri: Uri,
      body: Body = Body.Empty,
      headers: Map[String, String] = Map.empty,
      method: Method = Method.GET,
      retryConfig: Option[RetryConfig] = None,
    ) extends Request {

      def uri(uri: Uri): RequestImpl = copy(uri = uri)


      override def addHeader[A: ZStringer](name: String, value: A): Request =
        addHeader(name, ZStringer[A].toZString(value).toString)

      override def addHeader(name: String, value: String): RequestImpl =
        copy(headers = headers + (name -> value))

      override def body[A](a: A)(implicit toBodyFn: A => Body): RequestImpl =
        copy(
          body = toBodyFn(a)
        )

      override def withRetryConfig(retryConfig: RetryConfig): Request =
        copy(retryConfig = retryConfig.some)

      override def subPath(subPath: Uri): Request = {
        uri(uri.addPathSegments(subPath.pathSegments.segments))
      }

      override def jsonBody(json: JsVal): Request = {
        addHeader("Content-Type", "application/json")
          .copy(
            body = Body.JsonBody(json),
          )
      }

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
              case Body.Empty =>
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

  sealed trait Body
  object Body {
    case object Empty extends Body
    case class StringBody(content: String) extends Body
    case class JsonBody(content: JsVal) extends Body
  }

}
