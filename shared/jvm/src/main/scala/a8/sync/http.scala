package a8.sync


import a8.shared.{CompanionGen, SharedImports}
import a8.shared.json.JsonCodec
import a8.shared.json.ast.JsVal
import a8.sync.Mxhttp.MxRetryConfig
import sttp.model.{StatusCode, Uri}
import wvlet.log.LazyLogger
import a8.shared.SharedImports._
import a8.shared.app.{LoggerF, Logging, LoggingF}
import a8.sync.http.Body
import a8.sync.http.Body.StringBody
import a8.sync.http.impl.RequestProcessorImpl
import fs2.text
import retry.RetryDetails.{GivingUp, WillDelayAndRetry}
import retry._
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.client3.internal.{charsetFromContentType, sanitizeCharset}
import sttp.{capabilities, client3}
import sttp.client3.{Identity, RequestT, ResponseAs, basicRequest}

import java.net.URLEncoder
import java.nio.charset.Charset
import scala.concurrent.duration.FiniteDuration

object http extends LazyLogger {

  case class Backend[F[_] : Async](
    delegate: sttp.client3.SttpBackend[F, Fs2Streams[F] with capabilities.WebSockets]
  )

  object RetryConfig extends MxRetryConfig {
    val noRetries = RetryConfig(0, 1.second, 1.minute)
  }
  @CompanionGen
  case class RetryConfig(
    count: Int,
    initialBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
  ) {
    def retryPolicy[F[_]: Async] = {
      import RetryPolicies._
      // https://cb372.github.io/cats-retry/docs/policies.html
      limitRetries[F](count)
        .join(
          exponentialBackoff[F](initialBackoff)
            meet
            constantDelay[F](maxBackoff)
        )
    }
  }

  def applyRetryPolicy[F[_] : Async, A](retryPolicy: RetryPolicy[F], context: String, fa: F[A]): F[A] = {

    def logError(err: Throwable, details: RetryDetails): F[Unit] = {
      val F = Sync[F]
      details match {
        case WillDelayAndRetry(nextDelay, retriesSoFar, cumulativeDelay) =>
          F.delay{
            logger.debug(s"http request failed on the $retriesSoFar retry -- $context", err)
          }
        case GivingUp(totalRetries, totalDelay) =>
          F.delay {
            logger.warn(s"giving up http request after $totalRetries retries -- $context", err)
          }
      }
    }

    // using cats-retry here https://cb372.github.io/cats-retry/docs/index.htmlx
    retryingOnAllErrors[A](
      policy = retryPolicy,
      onError = logError
    )(fa)

  }

  case class InvalidHttpResponseStatusCode(statusCode: StatusCode, statusText: String, responseBody: String) extends Exception

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
    def addQueryParm(name: String, value: String): Request
    def body[A](a: A)(implicit toBodyFn: A => Body): Request
    def method(m: Method): Request

    def formBody(fields: Iterable[(String,String)]): Request

    def exec[F[_]](implicit processor: RequestProcessor[F]): F[String] =
      processor.exec(this)

    def execWithJsonResponse[F[_] : Async, A : JsonCodec](implicit processor: RequestProcessor[F]): F[A] =
      processor.execWithStringResponse(this, None, responseJson => json.readF[F,A](responseJson))

    def execWithStreamResponse[F[_] : Async, A](responseEffect: Response[F]=>F[A])(implicit processor: RequestProcessor[F]): F[A] =
      processor.execWithStreamResponse[A](this, None, streamEffect)

    def execWithString[F[_],A](effect: String=>F[A])(implicit processor: RequestProcessor[F]): F[A] =
      processor.execWithStringResponse(this, None, effect)

    /**
     * implementer of the responseEffect function must raise errors into the F.
     * implementer is responsible for things like checking http status codes, etc, etc
     */
    def execRaw[F[_] : Async, A](responseEffect: Response[F]=>F[A])(implicit processor: RequestProcessor[F]): F[A] =
      processor.execRaw[A](this, None, responseEffect)

    def curlCommand: String

    def streamingBody[F[_] : Async](requestBody: fs2.Stream[F,Byte]): StreamingRequest[F] =
      StreamingRequest[F](this, requestBody.some)

    def streaming[F[_] : Async]: StreamingRequest[F] =
      StreamingRequest[F](this)

  }

  case class StreamingRequest[F[_] : Async](rawRequest: Request, requestBody: Option[fs2.Stream[F,Byte]] = None) {

    def requestBody(requestBody: fs2.Stream[F,Byte]): StreamingRequest[F] =
      copy(requestBody = requestBody.some)

    def exec(implicit processor: RequestProcessor[F]): F[String] =
      processor.exec(rawRequest, requestBody)

    def execRaw[A](responseEffect: Response[F]=>F[A])(implicit processor: RequestProcessor[F]): F[A] =
      processor.execRaw[A](rawRequest, requestBody, responseEffect)

    def execWithStreamingResponse[A](responseEffect: Response[F]=>F[A])(implicit processor: RequestProcessor[F]): F[A] =
      processor.execWithStreamResponse[A](rawRequest, requestBody, responseEffect)

    def execWithJsonResponse[A : JsonCodec](implicit processor: RequestProcessor[F]): F[A] =
      processor.execWithStringResponse(rawRequest, requestBody, responseJson => json.readF[F,A](responseJson))

    def execWithEffect[A](effect: String=>F[A])(implicit processor: RequestProcessor[F]): F[A] =
      processor.execWithStringResponse(rawRequest, requestBody, effect)

    def updateRawRequest(fn: Request => Request): StreamingRequest[F] =
      copy(rawRequest = fn(rawRequest))

  }

  case class Response[F[_]: Async](
    responseMetadata: ResponseMetadata,
    responseBody: fs2.Stream[F,Byte],
  ) {

    lazy val charset: Charset =
      responseMetadata
        .contentType
        .flatMap(impl.charsetFromContentType)
        .map(impl.sanitizeCharset)
        .map(Charset.forName)
        .getOrElse(Utf8Charset)

    def raiseResponseErrors: F[Unit] =
      if ( responseMetadata.statusCode.isSuccess ) {
        Async[F].unit
      } else {
        asInvalidHttpResponseStatusCode
      }

    def responseBodyAsString: F[String] =
      responseBody
        .through(text.decodeWithCharset(charset))
        .compile
        .string

    def asInvalidHttpResponseStatusCode[A]: F[A] = {
      responseBodyAsString
        .flatMap { body =>
          Async[F].raiseError[A](InvalidHttpResponseStatusCode(responseMetadata.statusCode, responseMetadata.statusText, body))
        }
    }
  }

  object Method {
    val GET = Method("GET")
    val POST = Method("POST")
  }

  case class Method(
    value: String
  )

  object ResponseMetadata {

    def apply[A](sttpResponse: sttp.client3.Response[A]): ResponseMetadata =
      ResponseMetadata(
        sttpResponse.code,
        sttpResponse.statusText,
        sttpResponse.headers.map(h => h.name -> h.value).toVector,
      )

    def apply[A](sttpResponseMetadata: sttp.model.ResponseMetadata): ResponseMetadata =
      ResponseMetadata(
        sttpResponseMetadata.code,
        sttpResponseMetadata.statusText,
        sttpResponseMetadata.headers.map(h => h.name -> h.value).toVector,
      )

  }

  case class ResponseMetadata(
    statusCode: StatusCode,
    statusText: String,
    headers: Vector[(String,String)],
  ) {

    lazy val headersByName =
      headers
        .map(h => CIString(h._1) -> h._2)
        .toMap

    lazy val contentType: Option[String] = headersByName.get(impl.contentTypeHeaderName)

  }

  sealed trait RequestProcessor[F[_]] {

    val backend: Backend[F]

    def exec(request: Request, streamingRequestBody: Option[fs2.Stream[F,Byte]] = None): F[String]

    /**
     * Will exec the request and map the response allowing for error's in the map'ing to
     * trigger a retry.
     *
     * This lifts the mapFn to run inside the scope for the retry so you can do things
     * like self manage recovery and further validating the response (like the response
     * may be json but is it the json you actually want).
     *
     */
    def execWithStringResponse[A](request: Request, streamingRequestBody: Option[fs2.Stream[F,Byte]] = None, effect: String => F[A]): F[A]
    
    def execWithStreamResponse[A](request: Request, streamingRequestBody: Option[fs2.Stream[F,Byte]] = None, responseEffect: Response[F]=>F[A]): F[A]

    /**
     *
     * implementer of the responseEffect function must raise errors into the F.
     * implementer is responsible for things like checking http status codes, etc, etc
     *
     */
    def execRaw[A](request: Request, streamingRequestBody: Option[fs2.Stream[F,Byte]] = None, responseEffect: Response[F]=>F[A]): F[A]

  }

  object RequestProcessor {

    def asResource[F[_] : Async](retry: RetryConfig = RetryConfig.noRetries, maxConnections: Int = 50): Resource[F,RequestProcessor[F]] = {
      for {
        sttpBackend <- AsyncHttpClientFs2Backend.resource()
        maxConnectionSemaphore <- Resource.eval(Semaphore[F](maxConnections))
      } yield
        RequestProcessorImpl(retry, Backend(sttpBackend), maxConnectionSemaphore)
    }


    def apply[F[_] : Async](retry: RetryConfig, backend: Backend[F], maxConnectionSemaphore: Semaphore[F]) =
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

    case class RequestProcessorImpl[F[_] : Async](
      retryConfig: RetryConfig,
      backend: Backend[F],
      maxConnectionSemaphore: Semaphore[F]
    ) extends LoggingF[F]
      with RequestProcessor[F]
    {

      lazy val defaultRetryPolicy = retryConfig.retryPolicy[F]

      val F = Async[F]

      override def exec(request: Request, streamingRequestBody: Option[fs2.Stream[F, Byte]]): F[String] =
        execWithStringResponse(request, streamingRequestBody, F.pure)

      override def execRaw[A](request: Request, streamingRequestBody: Option[fs2.Stream[F, Byte]], responseEffect: Response[F]=>F[A]): F[A] = {
        val retryPolicy =
          request
            .retryConfig
            .map(_.retryPolicy[F])
            .getOrElse(defaultRetryPolicy)
        applyRetryPolicy(retryPolicy, request.uri.toString(), rawSingleExec(request, streamingRequestBody, responseEffect))
      }


      override def execWithStringResponse[A](request: Request, streamingRequestBody: Option[fs2.Stream[F, Byte]], effect: String => F[A]): F[A] = {
        def responseEffect(response: Response[F]): F[A] = {
          for {
            _ <- response.raiseResponseErrors
            responseBodyAsString <- response.responseBodyAsString
            a <- effect(responseBodyAsString)
          } yield a
        }
        execRaw(request, streamingRequestBody, responseEffect)
      }

      override def execWithStreamResponse[A](request: Request, streamingRequestBody: Option[fs2.Stream[F, Byte]], responseEffect: Response[F]=>F[A]): F[A] = {
        def wrappedResponseEffect(response: Response[F]): F[A] = {
          response.raiseResponseErrors >> responseEffect(response)
        }
        execRaw(request, streamingRequestBody, wrappedResponseEffect)
      }

      def rawSingleExec[A](request: Request, streamingRequestBody: Option[fs2.Stream[F,Byte]], responseEffect: Response[F]=>F[A]): F[A] = {
        maxConnectionSemaphore.permit
          .use { _ =>

            def wrappedEffect(stream: fs2.Stream[F,Byte], sttpResponseMetadata: sttp.model.ResponseMetadata): F[A] = {
              val responseMetadata = ResponseMetadata(sttpResponseMetadata)
              responseEffect(Response(responseMetadata, stream))
            }

            val request0: RequestT[Identity, A, Any with capabilities.Effect[F] with Fs2Streams[F]] =
              basicRequest
                .method(sttp.model.Method(request.method.value), request.uri)
                .headers(request.headers)
                .response(client3.asStreamAlwaysWithMetadata(Fs2Streams[F])(wrappedEffect))

            val requestWithBody: RequestT[Identity, A, Any with capabilities.Effect[F] with Fs2Streams[F]] =
              (streamingRequestBody, request.body) match {
                case (Some(stream), _) =>
                  request0.streamBody(Fs2Streams[F])(stream)
                case (_, Body.Empty) =>
                  request0
                case (_, Body.StringBody(content)) =>
                  request0.body(content)
                case (_, Body.JsonBody(json)) =>
                  request0.body(json.compactJson)
              }

            logger.trace(s"${request.method.value} ${request.uri}")
            val startTime = System.currentTimeMillis()

            requestWithBody
              .send(backend.delegate)
              .map { response =>
                val responseMetadata = ResponseMetadata(response)
                logger.trace(s"${request.method.value} ${request.uri} completed in ${System.currentTimeMillis() - startTime} ms -- ${response.code} ${response.statusText}")
                response.body
              }
              .onError { error =>
                loggerF.debug(s"error with http request -- \n${request.curlCommand}", error)
              }

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
