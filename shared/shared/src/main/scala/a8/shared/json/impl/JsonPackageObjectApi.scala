package a8.shared.json.impl

import a8.shared.ZFileSystem
import a8.shared.json.ReadError.{ParseError, SingleReadError}
import a8.shared.json.ast.{JsObj, JsVal}
import a8.shared.json.{JsonCodec, JsonReader, ReadError, ZJsonReader}
import org.typelevel.jawn.Parser
import zio._
import a8.shared.SharedImports._
import a8.shared.json.JsonReader.JsonReaderOptions
import a8.shared.json.ZJsonReader.ZJsonReaderOptions

trait JsonPackageObjectApi {

  def parseObj(jsonStr: String): Either[ReadError,JsObj] =
    Parser
      .parseFromString[JsVal](jsonStr)
      .toEither
      .left
      .map(th => ParseError(th.getMessage))
      .flatMap {
        case jso: JsObj =>
          Right(jso)
        case jsv =>
          Left(SingleReadError("expected a json object", jsv.toRootDoc))
      }

  def parse(jsonStr: String): Either[ReadError,JsVal] =
    Parser
      .parseFromString[JsVal](jsonStr)
      .toEither
      .left
      .map(th => ParseError(th.getMessage))

  def unsafeParse(jsonStr: String): JsVal =
    Parser
      .parseFromString[JsVal](jsonStr)
      .get

  def unsafeRead[A : JsonCodec](jsonStr: String)(implicit jsonReaderOptions: JsonReaderOptions): A =
    read[A](jsonStr) match {
      case Left(re) =>
        throw re.asException
      case Right(v) =>
        v
    }

  def parseF(jsonStr: String): Task[JsVal] =
    fromDeferredEither(
      parse(jsonStr)
    )

  def readF[A : JsonCodec](jsonStr: String)(implicit jsonReaderZOptions: ZJsonReaderOptions): Task[A] =
    ZJsonReader[A]
      .read(jsonStr)

  protected def fromDeferredEither[A](eitherFn: => Either[ReadError,A]): Task[A] =
    ZIO.fromEither(
      eitherFn
        .left
        .map(_.asException)
    )

  def read[A : JsonCodec](jsonStr: String)(implicit jsonReaderOptions: JsonReaderOptions): Either[ReadError,A] =
    parse(jsonStr)
      .flatMap(_.as[A])

  def fromFile[A: JsonCodec](file: ZFileSystem.File)(implicit jsonReaderZOptions: ZJsonReaderOptions): Task[A] =
    file
      .readAsStringOpt
      .flatMap {
        case None =>
          zfail(new RuntimeException(z"${file} not found"))
        case Some(fileContents) =>
          readF[A](fileContents)
            .either
            .flatMap {
              case Right(a) =>
                zsucceed(a)
              case Left(e) =>
                zfail(new RuntimeException(z"error parsing json from ${file}", e))
            }

      }

}
