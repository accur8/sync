package a8.shared.json


import a8.shared.{Chord, SingleArgConstructor}
import a8.shared.json.ast._
import a8.shared.json.impl.{JsValOps, JsonCodecs}

import scala.reflect.{ClassTag, classTag}
import a8.shared.SharedImports._

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
