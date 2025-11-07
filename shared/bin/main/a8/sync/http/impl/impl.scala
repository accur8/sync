package a8.sync.http.impl

import a8.common.logging.{Level, Logger}
import a8.shared.SharedImports.{*, given}
import a8.shared.ZString.ZStringer
import a8.shared.json.JsonReader.{JsonReaderOptions, ReadResult}
import a8.shared.json.{JsonCodec, JsonReader, ReadError}
import a8.shared.json.ast.JsVal
import a8.sync.Semaphore
import a8.sync.http.*
import cats.data.Chain
import sttp.model.Uri

import java.net.URLEncoder

object bld {
  def logger = Logger.logger(getClass)
}

val logger = bld.logger

def standardResponseActionProcessor[A](responseFn: Response => ResponseAction[A])(using Logger): Either[Throwable, Response] => ResponseAction[A] =
  { responseE =>
      standardResponseProcessorImpl(responseE, responseFn)
  }

lazy val retryableStatusCodes: Set[Int] = Set(429, 500, 502, 503, 504)

def standardResponseProcessorImpl[A](responseE: Either[Throwable,Response], responseActionFn: Response=>ResponseAction[A])(using Logger): ResponseAction[A] = {
  responseE match {
    case Left(th) =>
      logger.debug("throwable during request", th)
      ResponseAction.Retryable[A]("retry from throwable")
    case Right(response) =>
      response.responseMetadata.statusCode match {
        case sc if sc.isSuccess =>
          responseActionFn(response)
        case sc if retryableStatusCodes(sc.code) =>
          ResponseAction.Retryable[A](s"http response ${sc.code} status received -- ${response.responseMetadata.compactJson}")
        case sc =>
          val responseInfo = ResponseInfo(response.responseMetadata, Some(response.responseBodyAsString))
          ResponseAction.Fail[A](s"unable to process response ${responseInfo.compactJson}", responseInfo.some)
      }
  }
}

val contentTypeHeaderName: CIString = CIString("Content-Type")

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
