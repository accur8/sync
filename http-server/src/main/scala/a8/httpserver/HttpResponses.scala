package a8.httpserver


import a8.shared.json.JsonCodec
import zio.http.{Headers, Body, HttpError, Response, Status}
import zio.{Task, UIO, ZIO}
import a8.shared.SharedImports._

object HttpResponses {

  lazy val textPlainContentTypeAsHeaders = Headers("Content-Type" -> "text/plain")
  lazy val jsonContentTypeAsHeaders = Headers(jsonContentTypeHeader)
  lazy val jsonContentTypeHeader = "Content-Type" -> "application/json"

  private def impl(body: String, headers: Headers = Headers.empty, status: Status = Status.Ok): ZIO[Any, Nothing, Response] =
    ZIO.succeed(
      Response(
        status = status,
        headers = headers,
        body = Body.fromString(body),
      )
    )

  def BadRequest(responseBody: String = ""): UIO[Response] =
    impl(responseBody, status = Status.BadRequest)

  def Ok(responseBody: String = ""): UIO[Response] =
    impl(responseBody)

  def Forbidden(responseBody: String): UIO[Response] =
    impl(responseBody, status = Status.Forbidden)

  def NotFound(responseBody: String = ""): UIO[Response] =
    impl(responseBody, status = Status.NotFound)

  def fromError(httpError: HttpError): UIO[Response] =
    impl(httpError.message, status = httpError.status)

  def json[A: JsonCodec](a: A, status: Status = Status.Ok): UIO[Response] =
    impl(a.prettyJson, status = status, headers = jsonContentTypeAsHeaders)

  def text(text: String, status: Status = Status.Ok): UIO[Response] =
    impl(text, status = status, headers = textPlainContentTypeAsHeaders)

}
