package a8.shared.json


import a8.shared.{Chord, FileSystem, SingleArgConstructor, ZFileSystem}
import a8.shared.json.ast._
import a8.shared.json.impl.{JsValOps, JsonCodecs}

import scala.reflect.{ClassTag, classTag}
import a8.shared.SharedImports._
import a8.shared.json.JsonReadOptions.UnusedFieldAction
import wvlet.log.Logger
import zio.{Task, UIO}

import scala.collection.mutable

object JsonCodec extends JsonCodecs {

  // these are here temporarily to keep code compiling while we decide whether JsonTypedCodec is moved to a top level Api or stays hidden in impl
  val string = JsonTypedCodec.string
  val long = JsonTypedCodec.long

  @inline
  final def apply[A : JsonCodec] = implicitly[JsonCodec[A]]

  class JsonCodecOps[A: JsonCodec](private val a: A) {
    def toJsVal: JsVal = JsonCodec[A].write(a)
    def toJsDoc: JsDoc = toJsVal.toDoc
    def compactJson: String = JsValOps.toCompactJsonChord(toJsVal, false).toString
    def prettyJson: String = JsValOps.toPrettyJsonChord(toJsVal).toString
  }


  implicit def jsonTypedCodecAsJsonCodec[A, B <: JsVal](implicit jsonTypedCodec: JsonTypedCodec[A,B]): JsonCodec[A] =
    jsonTypedCodec.asJsonCodec

  def or[A](left: JsonCodec[A], right: JsonCodec[A]): JsonCodec[A] =
    new JsonCodec[A] {

      override def write(a: A): JsVal =
        left.write(a)

      override def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, A] =
        left.read(doc) match {
          case l@ Left(_) =>
            right.read(doc) match {
              case Left(_) =>
                l
              case r@ Right(_) =>
                r
            }
          case r@ Right(_) =>
            r
        }

    }

}

trait JsonCodec[A] { jsonCodecA =>

  def write(a: A): JsVal
  def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError,A]

  def read(doc: JsDoc, default: Option[() => A])(implicit readOptions: JsonReadOptions): Either[ReadError,A] = {
    doc.value match {
      case JsNothing =>
        default match {
          case None =>
            read(doc)
          case Some(fn) =>
            Right(fn())
        }
      case _ =>
        read(doc)
    }

  }

}
