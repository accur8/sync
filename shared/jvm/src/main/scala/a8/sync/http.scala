package a8.sync


import a8.shared.{CompanionGen, SharedImports, StringValue, ZString}
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

  @CompanionGen
  case class RetryConfig(
    maxRetries: Int,
    initialBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
  )

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

    def exec[F[_]](implicit processor: RequestProcessor, trace: Trace, loggerF: LoggerF): Task[String] =
      processor.exec(this)

    def execWithJsonResponse[A : JsonCodec](implicit processor: RequestProcessor, trace: Trace, loggerF: LoggerF): Task[A] =
      processor.execWithStringResponse(this, responseJson => json.readF[A](responseJson))

    def execWithStreamResponse[A](responseEffect: Response=>Task[A])(implicit processor: RequestProcessor, trace: Trace, loggerF: LoggerF): Task[A] =
      processor.execWithStreamResponse[A](this, responseEffect)

    def execWithString[A](effect: String=>Task[A])(implicit processor: RequestProcessor, trace: Trace, loggerF: LoggerF): Task[A] =
      processor.execWithStringResponse(this, effect)

    /**
     * implementer of the responseEffect function must raise errors into the F.
     * implementer is responsible for things like checking http status codes, etc, etc
     */
    def execRaw[A](responseEffect: Response=>Task[A])(implicit processor: RequestProcessor, trace: Trace, loggerF: LoggerF): Task[A] =
      processor.execRaw[A](this, responseEffect)

    def curlCommand: String

    def streamingRequestBody(requestBody: XStream[Byte]): Request

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

    def exec(request: Request)(implicit trace: Trace, loggerF: LoggerF): Task[String]

    /**
     * Will exec the request and map the response allowing for error's in the map'ing to
     * trigger a retry.
     *
     * This lifts the mapFn to run inside the scope for the retry so you can do things
     * like self manage recovery and further validating the response (like the response
     * may be json but is it the json you actually want).
     *
     */
    def execWithStringResponse[A](request: Request, effect: String => Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A]

    def execWithStreamResponse[A](request: Request, responseEffect: Response=>Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A]

    /**
     *
     * implementer of the responseEffect function must raise errors into the F.
     * implementer is responsible for things like checking http status codes, etc, etc
     *
     */
    def execRaw[A](request: Request, responseEffect: Response=>Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A]

  }

  object RequestProcessor {

    def asResource(retry: RetryConfig = RetryConfig.noRetries, maxConnections: Int = 50): Resource[RequestProcessor] = {
      for {
        sttpBackend <-sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend.scoped()
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

      override def exec(request: Request)(implicit trace: Trace, loggerF: LoggerF): Task[String] =
        execWithStringResponse(request, s => ZIO.succeed(s))

      override def execRaw[A](request: Request, responseEffect: Response=>Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A] = {
        request match {
          case r0: RequestImpl =>
            run(r0, responseEffect)
        }
      }

      private def run[A](request: RequestImpl, responseEffect: Response=>Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A] = {
        def runImpl(retryNumber: Int, backoff: FiniteDuration): Task[A] = {
          val resolvedBackoff = List(backoff, retryConfig.maxBackoff).min
          def processResponse(response: Response): Task[A] = {

//            lazy val isJsonResponse: Boolean =
//              response
//                .responseMetadata
//                .headersByName
//                .get(CIString("content-type"))
//                .exists(_ === "application/json")

            def retry(context: String): Task[A] = {
              for {
                _ <- loggerF.debug(s"${context} will retry in ${resolvedBackoff}")
                _ <- ZIO.sleep(resolvedBackoff.toZio)
                response <- runImpl(retryNumber + 1, resolvedBackoff * 2)
              } yield response
            }


            response.responseMetadata.statusCode match {
              case sc if sc.isSuccess =>
                responseEffect(response)
              case sc if sc.code === 429 =>
                if (retryNumber > retryConfig.maxRetries) {
                  retry(s"received 429 response ${response.responseMetadata}")
                } else
                  ZIO.fail(new RuntimeException(s"reached retry limit tried ${retryNumber} times"))
//              case _ if isJsonResponse =>
//                response
//                  .responseBodyAsString
//                  .flatMap(json.readF[ODataErrorResponse])
//                  .catchAll(_ =>
//                    IO.raiseError(new RuntimeException(s"invalid response -- ${response.responseMetadata}"))
//                  )
//                  .flatMap(od => IO.raiseError(ODataException(od.error)))
              case sc =>
                ZIO.fail(new RuntimeException(s"invalid response -- ${response.responseMetadata}"))
            }
          }

          def trapHardError(th: Throwable): Task[A] =
            if (retryNumber >= retryConfig.maxRetries) {
              for {
                _ <- loggerF.debug(s"attempt ${retryNumber+1} failed", th)
                response <- runImpl(retryNumber + 1, resolvedBackoff * 2)
              } yield response
            } else {
              ZIO.fail(th)
            }

          rawSingleExec(request, processResponse, trapHardError)

        }
        runImpl(0, retryConfig.initialBackoff)
      }

      override def execWithStringResponse[A](request: Request,  effect: String => Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A] = {
        def responseEffect(response: Response): Task[A] = {
          for {
            _ <- response.raiseResponseErrors
            responseBodyAsString <- response.responseBodyAsString
            a <- effect(responseBodyAsString)
          } yield a
        }
        execRaw(request, responseEffect)
      }

      override def execWithStreamResponse[A](request: Request, responseEffect: Response=>Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A] = {
        def wrappedResponseEffect(response: Response): Task[A] = {
          response.raiseResponseErrors *> responseEffect(response)
        }
        execRaw(request, wrappedResponseEffect)
      }

      def rawSingleExec[A](rawRequest: RequestImpl, responseEffect: Response=>Task[A], catchAll: Throwable=>Task[A])(implicit trace: Trace, loggerF: LoggerF): Task[A] = {
        val rawRequest0: Task[Request] = ZIO.succeed(rawRequest: Request)
        for {
          request0 <- rawRequest.effects.foldLeft(rawRequest0)((r, effect) => r.flatMap(effect))
          request = request0 match { case r0: RequestImpl => r0 }
          response <-
            maxConnectionSemaphore
              .withPermit {

                def wrappedEffect(stream: XStream[Byte], sttpResponseMetadata: sttp.model.ResponseMetadata): Task[A] = {
                  val responseMetadata = ResponseMetadata(sttpResponseMetadata)
                  responseEffect(Response(responseMetadata, stream))
                }

                type SttpRequest = RequestT[Identity, A, Any with capabilities.Effect[Task] with ZioStreams]

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

                val isStreamingRequestBody =
                  request.body match {
                    case Body.StreamingBody(_) =>
                      true
                    case _ =>
                      false
                  }

                def requestWithFollowRedirects(r0: SttpRequest): SttpRequest =
                  r0.followRedirects(request.followRedirects)

                val request0: SttpRequest =
                  basicRequest
                    .method(sttp.model.Method(request.method.value), request.uri)
                    .headers(request.headers)
                    .response(client3.asStreamAlwaysWithMetadata(ZioStreams)(wrappedEffect))

                val requestUpdaters: Vector[SttpRequest=>SttpRequest] =
                  Vector(requestWithBody, requestWithFollowRedirects)

                val resolvedSttpRequest = requestUpdaters.foldLeft(request0)((r0,fn) => fn(r0))

                val startTime = java.lang.System.currentTimeMillis()

                val effect =
                  resolvedSttpRequest
                    .send(backend.delegate)
                    .flatMap { response =>
                      loggerF.trace(s"${request.method.value} ${request.uri} completed in ${java.lang.System.currentTimeMillis() - startTime} ms -- ${response.code} ${response.statusText}")
                        .as(response.body)
                    }
                    .onError { error =>
                      loggerF.debug(s"error with http request -- \n${request.curlCommand}", error)
                    }
                    .catchAll(catchAll)

                loggerF.debug(s"running request ${isStreamingRequestBody.toOption("(has streaming request body)").getOrElse("")}\n${request.curlCommand.indent("    ")}") *> effect

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

  sealed trait Body
  object Body {
    case object Empty extends Body
    case class StringBody(content: String) extends Body
    case class JsonBody(content: JsVal) extends Body
    case class StreamingBody(stream: XStream[Byte]) extends Body
  }

}
