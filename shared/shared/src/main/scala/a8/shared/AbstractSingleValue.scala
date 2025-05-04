package a8.shared

import a8.shared.jdbcf.{RowReader, RowWriter, SqlString}
import a8.shared.json.{JsonCodec, JsonTypedCodec, ast}
import SqlString._
import a8.shared.ZString.ZStringer
import a8.shared.json.ast.{JsNum, JsVal}

import language.implicitConversions

object AbstractSingleValue {

  abstract class Companion[A <: AbstractSingleValue[B], B : cats.Eq, C <: JsVal](
    implicit
      rowReaderB: RowReader[B],
      rowWriterB: RowWriter[B],
      jsonCodecB: JsonTypedCodec[B, C],
      zstringerB: ZStringer[B],
      canEqual: CanEqual[B,B],
  ) {


    implicit lazy val zioEq: zio.prelude.Equal[A] =
      zio.prelude.Equal.make((a, b) => a.value == b.value)

    implicit lazy val catsEq: cats.kernel.Eq[A] =
      cats.kernel.Eq.by[A,B](_.value)

    implicit lazy val jsonCodec: JsonCodec[A] =
      jsonTypedCodec.asJsonCodec

    implicit lazy val jsonTypedCodec: JsonTypedCodec[A, C] = {
      jsonCodecB.dimap[A](
        apply,
        _.value,
      )
    }

    implicit lazy val rowReader: RowReader[A] = rowReaderB.map(v => apply(v))
    implicit lazy val rowWriter: RowWriter[A] = rowWriterB.mapWriter[A](_.value)

    implicit def toSqlString(a: A): SqlString =
      unsafe.rawSqlString(a.value.toString)

    def apply(value: B): A

    def unapply(value: B): Option[A] =
      Some(apply(value))

    implicit val zstringer: ZStringer[A] =
      new ZStringer[A] {
        override def toZString(a: A): ZString =
          zstringerB.toZString(a.value)
      }

  }

}

trait AbstractSingleValue[A] {
  val value: A
}
