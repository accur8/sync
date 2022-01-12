package a8.shared.json.impl

import a8.shared.json.ReadError.{ParseError, SingleReadError}
import a8.shared.json.ast.{JsObj, JsVal}
import a8.shared.json.{JsonCodec, ReadError}
import cats.effect.kernel.Async
import org.typelevel.jawn.Parser

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

  def parseF[F[_]: Async](jsonStr: String): F[JsVal] =
    fromDeferredEither(
      parse(jsonStr)
    )

  def readF[F[_] : Async, A : JsonCodec](jsonStr: String): F[A] =
    fromDeferredEither(
      read[A](jsonStr)
    )

  protected def fromDeferredEither[F[_] : Async,A](eitherFn: => Either[ReadError,A]): F[A] =
    Async[F].defer {
      Async[F].fromEither(
        eitherFn
          .left
          .map(_.asException)
      )
    }

  def read[A : JsonCodec](jsonStr: String): Either[ReadError,A] =
    parse(jsonStr)
      .flatMap(_.as[A])

}
