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

//def standardResponseProcessor[A](responseProcessor: Response=>Either[Throwable,A])(using Logger): Either[Throwable,Response]=>ResponseAction[A] =
//  { responseE =>
//      !!!
////      responseE match {
////        case l@Left(th) =>
////          !!!
////        case r@Right(response) =>
////          standardResponseProcessorImpl(r, responseProcessor(response))
////      }
//  }

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

// !!!
//def execWithStringResponse(implicit processor: RequestProcessor, trace: Trace, logger: Logger): Attempt[String] =
//  processor.exec(this)
//
//def execWithJsonResponse[A : JsonCodec](implicit processor: RequestProcessor, jsonResponseOptions: JsonResponseOptions, logger: Logger): A = {
//
//  implicit val jsonReaderOptions: JsonReaderOptions = {
//    if ( jsonResponseOptions.jsonWarningsLogLevel.equals(Level.Off) ) {
//      JsonReaderOptions.NoLogWarnings
//    } else {
//      JsonReaderOptions.LogWarnings(jsonResponseOptions.jsonWarningsLogLevel, trace, logger)
//    }
//  }
//
//  // some mildly unruly code because all of the JsonResponseOptions come together here
//  def responseEffect(response: Response): ResponseAction[A] = {
//    val responseBodyStr = response.responseBodyAsString
//
//    def nonSuccessfulResponse(retry: Boolean, error: ReadError): ResponseAction[A] = {
//      if (jsonResponseOptions.retryJsonParseErrors) {
//        ResponseAction.Retry(error.prettyMessage)
//      } else {
//        ResponseAction.Fail(error.prettyMessage, ResponseInfo(response.responseMetadata, responseBodyStr.some).some)
//      }
//    }
//
//    val responseAction: ResponseAction[A] =
//      json.parse(responseBodyStr) match {
//        case Left(parseError) =>
//          nonSuccessfulResponse(jsonResponseOptions.retryJsonParseErrors, parseError)
//        case Right(unvalidatedJsv) =>
//          jsonResponseOptions.responseValidator(unvalidatedJsv) match {
//            case r@ ResponseAction.Retry(_) =>
//              ResponseAction.Retry(r.context)
//            case f@ ResponseAction.Fail(_, _) =>
//              ResponseAction.Fail(f.context, f.responseInfo)
//            case ResponseAction.Success(validatedJsv) =>
//              JsonReader[A].readResult(validatedJsv.toRootDoc) match {
//                case rre: ReadResult.Error[?] =>
//                  nonSuccessfulResponse(jsonResponseOptions.retryJsonCodecErrors, rre.readError)
//                case ReadResult.Success(a, _, _, _) =>
//                  // happy path yay we made it
//                  ResponseAction.Success(a)
//              }
//          }
//      }
//    if (jsonResponseOptions.logJsonResponseBody) {
//      logger.debug(s"response body --\n${responseBodyStr}")
//    }
//    responseAction
//  }
//
//  execWithEffect(standardResponseActionProcessor(responseEffect))
//}
//
//def execWithResponse[A](responseFn: Response=>Attempt[A])(using RequestProcessor, Logger): Attempt[A] =
//  summon[RequestProcessor].execWithResponse[A](this, responseFn)
//
//def execWithString[A](fn: String=>Attempt[A])(using RequestProcessor, Logger): Attempt[A] =
//  summon[RequestProcessor].execWithStringResponse(this, fn)
//
///**
// * caller must supply a responseEffect that is responsible for things like checking http status codes, etc, etc
// */
//def execWithEffect[A](responseActionFn: Attempt[Response]=>ResponseAction[A])(using processor: RequestProcessor, logger: Logger): Attempt[A] =
//  processor.execWithEffect[A](this, responseActionFn)
