package a8.sync.http.impl


import a8.shared
import a8.shared.SharedImports
import a8.shared.SharedImports.{*, given}
import a8.shared.ZString.ZStringer
import a8.shared.json.JsonCodec
import a8.shared.json.ast.JsVal
import a8.sync.http.{Body, JsonResponseOptions, Method, Request, RequestProcessor, Response, ResponseAction, RetryConfig}
import cats.data.Chain
import sttp.model.Uri

import java.net.URLEncoder

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
          case Body.BytesBody(b) =>
            Chain.one(s"  # binary request body omitted")
        }
      )
      .append(s"  -X ${method.value}")
      .append(s"  '${uri.toString()}'")
      .toVector
      .mkString(" \\\n")
  }

  override def execWithStringResponse(using RequestProcessor, Logger): String =
    summon[RequestProcessor]
      .execWithStringResponseBody(this, identity)

  override def execWithJsonResponse[A: JsonCodec](using RequestProcessor, JsonResponseOptions, Logger): A =
    summon[RequestProcessor]
      .execWithJsonResponse[A](this)

  override def execWithResponseActionFn[A](responseActionFn: Response => ResponseAction[A])(using RequestProcessor, Logger): A =
    summon[RequestProcessor]
      .execWithResponseActionFn(this, standardResponseActionProcessor(responseActionFn))

  override def formBody(fields: Iterable[(String, String)]): Request = {
    def encode(text: String): String = URLEncoder.encode(text, "UTF-8")
    val encodedFormBody =
      fields
        .map(t => encode(t._1) + "=" + encode(t._2))
        .mkString("&")
    copy(
      body = Body.StringBody(encodedFormBody),
      headers = headers + ("Content-Type" -> "application/x-www-form-urlencoded"),
    )
  }
}
