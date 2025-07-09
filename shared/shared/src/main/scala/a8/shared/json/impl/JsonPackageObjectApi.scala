package a8.shared.json.impl

import a8.shared.FileSystem
import a8.shared.json.ReadError.{ParseError, SingleReadError}
import a8.shared.json.ast.{JsObj, JsVal}
import a8.shared.json.{JsonCodec, JsonReader, ReadError}
import org.typelevel.jawn.Parser
import a8.shared.SharedImports.*
import a8.shared.json.JsonReader.JsonReaderOptions

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

  def read[A : JsonCodec](jsonStr: String)(implicit jsonReaderOptions: JsonReaderOptions): Either[ReadError,A] =
    parse(jsonStr)
      .flatMap(_.as[A])

   def fromFile[A: JsonCodec](file: FileSystem.File)(using JsonReaderOptions): A =
     file
       .readAsStringOpt()
       .map { fileContents =>
         read[A](fileContents) match {
           case Right(a) =>
             a
           case Left(re) =>
             throw new RuntimeException(z"error parsing json from ${file.absolutePath}", re.asException)
         }
       }
       .getOrError(z"${file.absolutePath} not found")

}
