package a8.shared.json

import a8.shared.SingleArgConstructor
import a8.shared.json.ast.{JsDoc, JsVal}
import a8.shared.json.impl.JsonTypedCodecs

object JsonTypedCodec extends JsonTypedCodecs

abstract class JsonTypedCodec[A, B <: JsVal] { outerTypedCodec =>

  def write(a: A): B
  def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError,A]

  def dimap[C](a2c: A=>C, c2a: C=>A): JsonTypedCodec[C,B] =
    new JsonTypedCodec[C,B] {
      override def write(c: C): B = outerTypedCodec.write(c2a(c))
      override def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, C] = outerTypedCodec.read(doc).map(a2c)
    }

  def to[C](implicit singleArgTypeConstructor: SingleArgConstructor[A,C]): JsonTypedCodec[C,B] =
    dimap(singleArgTypeConstructor.construct, singleArgTypeConstructor.deconstruct)

  val asJsonCodec: JsonCodec[A] =
    new JsonCodec[A] {
      override def write(a: A): JsVal = outerTypedCodec.write(a)
      override def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, A] = outerTypedCodec.read(doc)
    }

}
