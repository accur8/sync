package a8.sync


import a8.shared.SharedImports.*
import a8.shared.app.Ctx
import a8.shared.zreplace.{ScopedInputStream, Task}
import a8.sync.http.{Result, impl}
import sttp.client4.*

import scala.concurrent.duration.FiniteDuration

object SttpBackend {

  def apply[A](sttpResponse: sttp.client4.Response[A]): http.http.ResponseMetadata =
    http.http.ResponseMetadata(
      sttpResponse.code.code,
      sttpResponse.statusText,
      sttpResponse.headers.map(h => h.name -> h.value).toVector,
    )

  def apply[A](sttpResponseMetadata: sttp.model.ResponseMetadata): http.http.ResponseMetadata =
    http.http.ResponseMetadata(
      sttpResponseMetadata.code.code,
      sttpResponseMetadata.statusText,
      sttpResponseMetadata.headers.map(h => h.name -> h.value).toVector,
    )

}

case class SttpBackend(
                        sttpBackend: sttp.client4.WebSocketSyncBackend,
                        readTimeout: Option[FiniteDuration],
                        retryConfig: http.http.RetryConfig,
                        //    replayableStreamFactory: ReplayableStream.Factory
)
  extends http.http.Backend
  with Logging
{

  override def runSingleRequest(request: impl.RequestImpl): Attempt[http.http.Response] = {

    type SttpRequest = Request[String]

    def requestWithReadTimeout(r0: SttpRequest): SttpRequest =
      readTimeout.fold(r0)(r0.readTimeout(_))

    def requestWithBody(r0: SttpRequest): SttpRequest =
      request.body match {
        case http.http.Body.BytesBody(bytes) =>
          r0.body(bytes.toArray)
        case http.http.Body.Empty =>
          r0
        case http.http.Body.StringBody(content) =>
          r0.body(content)
        case http.http.Body.JsonBody(json) =>
          r0.body(json.compactJson)
      }

    def requestWithFollowRedirects(r0: SttpRequest): SttpRequest =
      r0.followRedirects(request.followRedirects)

//    val responseAs: ResponseAs[(XStream[Byte], ResponseMetadata), ZioStreams] =
//      client3
//        .asStreamAlwaysUnsafe(ZioStreams)
//        .mapWithMetadata { (stream, sttpResponseMetadata) =>
//          stream -> ResponseMetadata(sttpResponseMetadata)
//        }

    val request0: SttpRequest =
      basicRequest
        .response(asStringAlways)
        .method(sttp.model.Method(request.method.value), request.uri)
        .headers(request.headers)

    val requestUpdaters: Vector[SttpRequest => SttpRequest] =
      Vector(requestWithBody, requestWithFollowRedirects, requestWithReadTimeout)

    val resolvedSttpRequest: SttpRequest = requestUpdaters.foldLeft(request0)((r0, fn) => fn(r0))

    val startTime = java.lang.System.currentTimeMillis()

    try {
      val sttpResponse = resolvedSttpRequest.send(sttpBackend)

      val response =
        http.http.Response(
          request,
          SttpBackend(sttpResponse),
          !!!, //response.body,
        )
      Right(response)
    } catch {
      case IsNonFatal(e) =>
        logger.debug(s"error with http request -- \n${request.curlCommand}", e)
        Left(e)
    }
  }
}