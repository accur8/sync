package a8.sync.http

import a8.shared.SharedImports.*
import a8.shared.app.Ctx
import a8.shared.zreplace.Chunk
import a8.sync.http.{Result, impl}
import a8.sync.http
import a8.sync.http.impl.RequestImpl
import sttp.client4.*
import a8.shared.zreplace.Chunk.toArray

import scala.concurrent.duration.FiniteDuration

object SttpBackend {

  def apply[A](sttpResponse: sttp.client4.Response[A]): http.ResponseMetadata =
    http.ResponseMetadata(
      sttpResponse.code.code,
      sttpResponse.statusText,
      sttpResponse.headers.map(h => h.name -> h.value).toVector,
    )

  def apply[A](sttpResponseMetadata: sttp.model.ResponseMetadata): http.ResponseMetadata =
    http.ResponseMetadata(
      sttpResponseMetadata.code.code,
      sttpResponseMetadata.statusText,
      sttpResponseMetadata.headers.map(h => h.name -> h.value).toVector,
    )

}

case class SttpBackend(
  sttpBackend: sttp.client4.WebSocketSyncBackend,
  readTimeout: Option[FiniteDuration],
  retryConfig: http.RetryConfig,
  //    replayableStreamFactory: ReplayableStream.Factory
)
  extends http.Backend
  with Logging
{

  override def shutdown(): Unit =
    sttpBackend.close()

  override def runSingleRequest(httpRequest: http.Request): Result[http.Response] = {

    val httpRequestImpl = httpRequest.asInstanceOf[RequestImpl]

    type SttpRequest = Request[Array[Byte]]

    def requestWithReadTimeout(r0: SttpRequest): SttpRequest =
      readTimeout.fold(r0)(r0.readTimeout(_))

    def requestWithBody(r0: SttpRequest): SttpRequest =
      httpRequestImpl.body match {
        case http.Body.BytesBody(bytes) =>
          r0.body(bytes.toArray)
        case http.Body.Empty =>
          r0
        case http.Body.StringBody(content) =>
          r0.body(content)
        case http.Body.JsonBody(json) =>
          r0.body(json.compactJson)
      }

    def requestWithFollowRedirects(r0: SttpRequest): SttpRequest =
      r0.followRedirects(httpRequestImpl.followRedirects)

//    val responseAs: ResponseAs[(XStream[Byte], ResponseMetadata), ZioStreams] =
//      client3
//        .asStreamAlwaysUnsafe(ZioStreams)
//        .mapWithMetadata { (stream, sttpResponseMetadata) =>
//          stream -> ResponseMetadata(sttpResponseMetadata)
//        }

    val request0: SttpRequest =
      basicRequest
        .response(asByteArrayAlways)
        .method(sttp.model.Method(httpRequestImpl.method.value), httpRequestImpl.uri)
        .headers(httpRequestImpl.headers)

    val requestUpdaters: Vector[SttpRequest => SttpRequest] =
      Vector(requestWithBody, requestWithFollowRedirects, requestWithReadTimeout)

    val resolvedSttpRequest: SttpRequest = requestUpdaters.foldLeft(request0)((r0, fn) => fn(r0))

    val startTime = java.lang.System.currentTimeMillis()

    try {
      val sttpResponse = resolvedSttpRequest.send(sttpBackend)

      val response =
        http.Response(
          httpRequestImpl,
          SttpBackend(sttpResponse),
          Chunk.fromArray(sttpResponse.body),
        )
      Result.Success(response)
    } catch {
      case IsNonFatal(e) =>
        logger.debug(s"error with http request -- \n${httpRequestImpl.curlCommand}", e)
        Result.Failure(error = e.some)
    }
  }
}