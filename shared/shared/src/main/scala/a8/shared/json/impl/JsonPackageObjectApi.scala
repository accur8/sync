package a8.shared.json.impl

import a8.shared.json.ReadError.{ParseError, SingleReadError}
import a8.shared.json.ast.{JsObj, JsVal}
import a8.shared.json.{JsonCodec, ReadError}
import org.typelevel.jawn.Parser
import zio._

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
          Left(SingleReadError("expected a json object", jsv.toDoc))
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

  def unsafeRead[A : JsonCodec](jsonStr: String): A =
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

  def readF[A : JsonCodec](jsonStr: String): Task[A] =
    fromDeferredEither(
      read[A](jsonStr)
    )

  protected def fromDeferredEither[A](eitherFn: => Either[ReadError,A]): Task[A] =
    ZIO.fromEither(
      eitherFn
        .left
        .map(_.asException)
    )

  def read[A : JsonCodec](jsonStr: String): Either[ReadError,A] =
    parse(jsonStr)
      .flatMap(_.as[A])

}
