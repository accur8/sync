package a8.httpserver


import a8.shared.{FileSystem, ZFileSystem}
import zio.{Chunk, ZIO}
import zio.http.Header.ContentType
import zio.http.{Body, Header, Headers, MediaType, Method, Response, Status, URL}
import a8.shared.SharedImports.*
import zio.http.Path.Segment

object model {

  type CiString = org.typelevel.ci.CIString
  val CiString = org.typelevel.ci.CIString

  given CanEqual[Segment, Segment] = CanEqual.derived

  type HttpStatusCode = zio.http.Status

  object HttpStatusCode {

    val Ok = Status.Ok
    val MovedPermanently = Status.MovedPermanently
    val NotAuthorized = Status.Unauthorized
    val Forbidden = Status.Forbidden
    val NotFound = Status.NotFound
    val MethodNotAllowed = Status.MethodNotAllowed
    val Conflict = Status.Conflict
    val Error = Status.InternalServerError

  }


  case class PreparedRequest(
    requestMeta: RequestMeta,
    responseEffect: Request => ZIO[Env, Throwable, Response],
  )


  case class RequestMeta(
    curlLogRequestBody: Boolean
  )

  case class PreFlightRequest(
    method: zio.http.Method,
    path: FullPath,
  )

  case class HttpResponseException(httpResponse: HttpResponse) extends Exception

  type Request = zio.http.Request

  object Path {
    def fromzioPath(zioPath: zio.http.Path): FullPath = {
      val isDirectory =
        zioPath
          .segments
          .lastOption
          .collect {
            case zio.http.Path.Segment.Root =>
              ()
          }
          .nonEmpty
      val parts =
        zioPath
          .segments
          .collect {
            case zio.http.Path.Segment.Text(s) =>
              CiString(s)
          }
      FullPath(parts.toIndexedSeq)
    }

  }

  sealed trait Path {
    val parts: IndexedSeq[CiString]
    def matches(path: FullPath): Boolean
  }

  object PathPrefix {
    def apply(parts: (String | CiString)*): PathPrefix =
      PathPrefix(
        parts
          .map {
            case s: CiString =>
              s
            case s: String =>
              CiString(s)
          }
          .toIndexedSeq
      )
  }

  case class PathPrefix(parts: IndexedSeq[CiString]) extends Path {
    override def matches(path: FullPath): Boolean =
      path.parts.startsWith(parts)
  }

  object FullPath {
    def apply(parts: (String | CiString)*): FullPath =
      FullPath(
        parts
          .map {
            case s: CiString =>
              s
            case s: String =>
              CiString(s)
          }
          .toIndexedSeq
      )
  }

  case class FullPath(parts: IndexedSeq[CiString]) extends Path {
    override def matches(path: FullPath): Boolean =
      parts.equals(path.parts)
  }

  case class RequestMatcher(methods: Seq[Method], paths: Seq[Path]) {

    lazy val methodsSet = methods.toSet

    def matches(preFlightRequest: PreFlightRequest): Boolean = {
      if ( methodsSet.contains(preFlightRequest.method) ) {
        paths.exists(_.matches(preFlightRequest.path))
      } else {
        false
      }
    }

  }

  type Env = Any

  type M[A] = zio.ZIO[Env, Throwable,A]

  def htmlResponse(htmlContent: String): HttpResponse =
    HttpResponse
      .fromHtml(htmlContent)

  def htmlResponseZ(htmlContent: String): zio.UIO[HttpResponse] =
    zsucceed(htmlResponse(htmlContent))

  //  type HttpResponseBody = zio.http.Body

  object ContentTypes {

    val empty = ContentType(MediaType("", ""))
    val html = ContentType.parse("text/html").toOption.get
    val xml = ContentType.parse("text/xml").toOption.get
    val json = ContentType.parse("application/json").toOption.get
  }


  object HttpResponseBody {

    def fromStr(string: String, contentType: ContentType = ContentTypes.empty): HttpResponse =
      HttpResponse(
        body = Body.fromString(string),
        headers = Headers(contentType)
      )

    def fromBytes(bytes: Array[Byte], contentType: ContentType = ContentTypes.empty): HttpResponse =
      HttpResponse(
        body = Body.fromChunk(Chunk.fromArray(bytes)),
        headers = Headers(contentType)
      )

    def fromFile(file: FileSystem.File, contentType: ContentType = ContentTypes.empty): HttpResponse =
      HttpResponse(
        body = Body.fromFile(file.asNioPath.toFile),
        headers = Headers(contentType)
      )

    def html(html: String) = fromStr(html, ContentTypes.html)
    def xml(xml: String) = fromStr(xml, ContentTypes.xml)

  }

  type HttpResponse = zio.http.Response

  object HttpResponse {

    val Ok = HttpResponses.Ok()
    val OkZ = zsucceed(emptyResponse)

    def apply(
               status: Status = Status.Ok,
               body: Body = Body.empty,
               headers: Headers = Headers.empty,
             ) =
      Response(
        body = body,
        status = status,
        headers = headers,
      )

    def emptyResponse(statusCode: HttpStatusCode): HttpResponse =
      HttpResponse(
        status = statusCode,
      )

    def conflict(message: String = ""): HttpResponse =
      HttpResponse(body = Body.fromString(message), status = HttpStatusCode.Conflict)

    def methodNotAllowed(message: String = ""): HttpResponse =
      HttpResponse(body=Body.fromString(message), status=HttpStatusCode.MethodNotAllowed)

    def forbidden(message: String = ""): HttpResponse =
      HttpResponse(body=Body.fromString(message), status=HttpStatusCode.Forbidden)

    def notFound(message: String = ""): HttpResponse =
      HttpResponse(body=Body.fromString(message), status=HttpStatusCode.NotFound)

    def fromFile(file: ZFileSystem.File, contentType: ContentType=ContentTypes.empty): HttpResponse =
      HttpResponse(body=Body.fromFile(file.asNioPath.toFile), headers=Headers(contentType))

    def fromHtml(html: String): HttpResponse =
      HttpResponse(body=Body.fromString(html), headers=Headers(ContentTypes.html))

    def error(message: String): HttpResponse =
      HttpResponse(body=Body.fromString(message), status=HttpStatusCode.NotFound)

    def errorz(message: String): zio.UIO[HttpResponse] =
      zsucceed(error(message))

//    def permanentRedirect(location: UrlPath): HttpResponse =
//      HttpResponse(
//        headers = Headers(Header.Location(URL(location.zioPath))),
//        status = HttpStatusCode.MovedPermanently,
//      )

  }


  object ContentPath {

    def fromZHttpPath(zhttpPath: zio.http.Path): ContentPath = {
      import zio.http.Path.Segment
      val isDirectory =
        zhttpPath
          .segments
          .lastOption
          .collect {
            case Segment.Root =>
              true
          }
          .nonEmpty
      val parts =
        zhttpPath
          .segments
          .flatMap {
            case Segment.Root =>
              None
            case Segment.Text(t) =>
              Some(t)
          }
      ContentPath(parts, isDirectory)
    }


    def apply(parts: Seq[String], isDirectory: Boolean): ContentPath = {
      val scrubbedParts =
        parts
          .filterNot { p =>
            val scrubbed = p.trim
            scrubbed == "." || scrubbed == ".." || scrubbed.contains("/") || scrubbed.contains("\\")
          }
      ContentPathImpl(scrubbedParts, isDirectory)
    }

    private case class ContentPathImpl(parts: Seq[String], isDirectory: Boolean) extends ContentPath {

      def parent = copy(parts = parts.init, isDirectory = true)

      def appendExtension(extension: String): ContentPath =
        copy(parts = parts.dropRight(1) ++ Some(parts.last + extension))

      def dropExtension: Option[ContentPath] =
        parts.last.lastIndexOf(".") match {
          case i if i > 0 =>
            val filename = parts.last.substring(0, i)
            copy(parts = parts.dropRight(1) ++ Some(filename))
              .some
          case _ =>
            None
        }

      override def append(suffix: ContentPath): ContentPath =
        copy(parts = parts ++ suffix.parts, suffix.isDirectory)

      override def asDirectory: ContentPath =
        copy(isDirectory = true)

      override def asFile: ContentPath =
        copy(isDirectory = false)
    }

  }

  sealed trait ContentPath {
    val parts: Seq[String]
    val isDirectory: Boolean
    def append(suffix: ContentPath): ContentPath
    def dropExtension: Option[ContentPath]
    def appendExtension(extension: String): ContentPath
    def asDirectory: ContentPath
    def asFile: ContentPath
    def parent: ContentPath
    def last = parts.last
    def fullPath: String = parts.mkString("/") + (if ( isDirectory ) "/" else "")
    override def toString: String = fullPath
  }


}
