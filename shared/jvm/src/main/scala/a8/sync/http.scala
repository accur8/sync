package a8.sync


import a8.shared.CompanionGen
import a8.shared.json.JsonCodec
import a8.shared.json.ast.JsVal
import a8.sync.Mxhttp.MxRetryConfig
import sttp.model.{StatusCode, Uri}
import wvlet.log.LazyLogger
import a8.shared.SharedImports._
import a8.shared.app.{LoggerF, Logging, LoggingF}
import a8.sync.http.Body
import a8.sync.http.Body.StringBody
import retry.RetryDetails.{GivingUp, WillDelayAndRetry}
import retry._
import sttp.capabilities.fs2.Fs2Streams
import sttp.{capabilities, client3}
import sttp.client3.{Identity, RequestT, basicRequest}

import java.net.URLEncoder
import scala.concurrent.duration.FiniteDuration

object http extends LazyLogger {

  case class Backend[F[_] : Async](
    delegate: sttp.client3.SttpBackend[F, Fs2Streams[F] with capabilities.WebSockets]
  )

  object RetryConfig extends MxRetryConfig {
  }
  @CompanionGen
  case class RetryConfig(
    count: Int,
    initialBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
  )

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

  object Request {
    def apply(baseUri: Uri): Request =
      impl.RequestImpl(baseUri)
  }

  sealed trait Request {

    val uri: Uri
    val body: Body
    val method: Method
    val headers: Map[String,String]

    def subPath(subPath: Uri): Request

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
      processor.execAndMap(this)(responseJson => json.readF[F,A](responseJson))

    def execAndMap[F[_],A](validateFn: String=>F[A])(implicit processor: RequestProcessor[F]): F[A] =
      processor.execAndMap(this)(validateFn)

    def execWithStreamResponse[F[_],A](implicit processor: RequestProcessor[F]): fs2.Stream[F,Byte] =
      processor.execWithStreamResponse(this)

    def curlCommand: String

  }

  trait Responder[A] {
    def exec[F[_] : Async : RequestProcessor](responseMetadata: ResponseMetadata, body: fs2.Stream[F,Byte]): F[A]
  }

  sealed trait RequestWithResponse[A] {
    def exec[F[_] : Async]: F[A]
  }

  object Method {
    val GET = Method("GET")
    val POST = Method("POST")
  }

  case class Method(
    value: String
  )

  case class ResponseMetadata(
    statusCode: StatusCode,
    statusText: String,
    headers: Map[String,String],
  )

  sealed trait RequestProcessor[F[_]] {

    def exec(request: Request): F[String]


    /**
     * Will exec the request and map the response allowing for error's in the map'ing to
     * trigger a retry.
     *
     * This lifts the mapFn to run inside the scope for the retry so you can do things
     * like self manage recovery and further validating the response (like the response
     * may be json but is it the json you actually want).
     *
     */
    def execAndMap[A](request: Request)(validateFn: String => F[A]): F[A]

    def execWithStreamResponse[A](request: Request): fs2.Stream[F,Byte]

  }

  object RequestProcessor {

    def apply[F[_] : Async](retry: RetryConfig, backend: Backend[F], maxConnectionSemaphore: Semaphore[F]) =
      RequestProcessorImpl(retry, backend, maxConnectionSemaphore)

    case class RequestProcessorImpl[F[_] : Async](
      retryConfig: RetryConfig,
      backend: Backend[F],
      maxConnectionSemaphore: Semaphore[F]
    ) extends LoggingF[F]
      with RequestProcessor[F]
    {

      lazy val retryPolicy: RetryPolicy[F] = {
        import RetryPolicies._
        // https://cb372.github.io/cats-retry/docs/policies.html
        limitRetries[F](retryConfig.count)
          .join(
            exponentialBackoff[F](retryConfig.initialBackoff)
              meet
            constantDelay[F](retryConfig.maxBackoff)
          )
      }

      val F = Async[F]

      override def exec(request: Request): F[String] =
        execAndMap(request)(F.pure)

      override def execWithStreamResponse[A](request: Request): fs2.Stream[F, Byte] =
        streamExecWithRetry(request)

      override def execAndMap[A](request: Request)(mapFn: String => F[A]): F[A] =
        execWithRetry(request, mapFn)

      def execWithRetry[A](request: Request, mapFn: String => F[A]): F[A] = {
        applyRetryPolicy(retryPolicy, request.uri.toString(), singleExec(request, mapFn))
      }

      def singleExec[A](request: Request, mapFn: String => F[A]): F[A] = {
        maxConnectionSemaphore.permit.use { _ =>

          val request0: client3.Request[Either[String, String], Any] =
            basicRequest
              .method(sttp.model.Method(request.method.value), request.uri)
              .headers(request.headers)

          val requestWithBody: RequestT[Identity, Either[String, String], Any] =
            request.body match {
              case Body.Empty =>
                request0
              case Body.StringBody(content) =>
                request0.body(content)
              case Body.JsonBody(json) =>
                request0.body(json.compactJson)
            }

          logger.trace(s"${request.method.value} ${request.uri}")
          val startTime = System.currentTimeMillis()

          requestWithBody
            .send(backend.delegate)
            .flatMap { response =>
              logger.trace(s"${request.method.value} ${request.uri} completed in ${System.currentTimeMillis() - startTime} ms -- ${response.code} ${response.statusText}")
              val responseBodyStrF: F[String] =
                if ( response.code.isSuccess ) {
                  response.body match {
                    case Left(errorMessage) =>
                      F.raiseError(new RuntimeException("we should never get a Left here since we are only dealing with 2xx successes"))
                    case Right(responseBody) =>
                      F.pure(responseBody)
                  }
                } else {
                  F.raiseError(new RuntimeException(s"http response code is ${response.code} which is not success -- ${response.body.merge}"))
                }
              responseBodyStrF
            }
            .flatMap(mapFn)
            .onError { error =>
              loggerF.debug(s"error with http request -- \n${request.curlCommand}", error)
            }

        }
      }

      def streamExecWithRetry[A](request: Request): fs2.Stream[F,Byte] =
        applyRetryPolicy(retryPolicy, request.uri.toString(), streamSingleExec(request))
          .fs2StreamEval
          .flatten


      def streamSingleExec[A](request: Request): F[fs2.Stream[F,Byte]] = {
        maxConnectionSemaphore.permit
          .use { _ =>

            val request0: RequestT[Identity, Either[String, fs2.Stream[F, Byte]], Any with Fs2Streams[F]] =
              basicRequest
                .method(sttp.model.Method(request.method.value), request.uri)
                .headers(request.headers)
                .response(client3.asStreamUnsafe(Fs2Streams[F]))

            val requestWithBody: RequestT[Identity, Either[String, fs2.Stream[F,Byte]], Any with Fs2Streams[F]] =
              request.body match {
                case Body.Empty =>
                  request0
                case Body.StringBody(content) =>
                  request0.body(content)
                case Body.JsonBody(json) =>
                  request0.body(json.compactJson)
              }

            logger.trace(s"${request.method.value} ${request.uri}")
            val startTime = System.currentTimeMillis()

            requestWithBody
              .send(backend.delegate)
              .flatMap { response =>
                logger.trace(s"${request.method.value} ${request.uri} completed in ${System.currentTimeMillis() - startTime} ms -- ${response.code} ${response.statusText}")
                val responseBodyStrF: F[fs2.Stream[F,Byte]] =
                  if ( response.code.isSuccess ) {
                    response.body match {
                      case Left(errorMessage) =>
                        F.raiseError(new RuntimeException("we should never get a Left here since we are only dealing with 2xx successes"))
                      case Right(responseBody) =>
                        F.pure(responseBody)
                    }
                  } else {
                    F.raiseError(new RuntimeException(s"http response code is ${response.code} which is not success -- ${response.body.merge}"))
                  }
                responseBodyStrF
              }
              .onError { error =>
                loggerF.debug(s"error with http request -- \n${request.curlCommand}", error)
              }

          }
      }

    }

  }

  object impl {

    case class RequestImpl(
      uri: Uri,
      body: Body = Body.Empty,
      headers: Map[String, String] = Map.empty,
      method: Method = Method.GET,
    ) extends Request {

      def uri(uri: Uri): RequestImpl = copy(uri = uri)

      override def addHeader(name: String, value: String): RequestImpl =
        copy(headers = headers + (name -> value))

      override def body[A](a: A)(implicit toBodyFn: A => Body): RequestImpl =
        copy(
          body = toBodyFn(a)
        )

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
