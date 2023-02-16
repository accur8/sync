package a8.shared

import a8.shared.jdbcf.{RowReader, RowWriter, SqlString}
import a8.shared.json.{JsonCodec, JsonTypedCodec, ast}
import SqlString._
import a8.shared.ZString.ZStringer
import a8.shared.json.ast.JsNum

import language.implicitConversions

object NumberValue {

  abstract class Companion[A <: NumberValue[B], B : cats.Eq](
    implicit
      rowReaderB: RowReader[B],
      rowWriterB: RowWriter[B],
      jsonCodecB: JsonTypedCodec[B, JsNum],
      fromStringB: FromString[B],
      zstringerB: ZStringer[B],
      canEqual: CanEqual[B,B],
  ) extends AbstractSingleValue.Companion[A, B, JsNum] {

    implicit val fromString: FromString[A] =
      new FromString[A] {
        override def fromString(value: String): Option[A] =
          fromStringB
            .fromString(value)
            .map(apply)
      }

    def unapply(value: String): Option[A] = {
      fromStringB
        .fromString(value)
        .map(apply)
    }

  }

}

trait NumberValue[A] extends AbstractSingleValue[A] {
  val value: A
}
