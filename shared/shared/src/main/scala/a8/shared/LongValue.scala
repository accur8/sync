package a8.shared


import a8.shared.jdbcf.{RowReader, RowWriter, SqlString}
import a8.shared.json.{JsonCodec, JsonTypedCodec, ast}
import SqlString._
import a8.shared.ZString.ZStringer

import language.implicitConversions

object LongValue {

  abstract class Companion[A <: LongValue] {

    implicit lazy val catsEq: cats.kernel.Eq[A] =
      cats.kernel.Eq.by[A,Long](_.value)

    implicit lazy val jsonCodec: JsonCodec[A] =
      jsonTypedCodec.asJsonCodec

    implicit lazy val jsonTypedCodec: JsonTypedCodec[A, ast.JsNum] =
      JsonCodec.long.dimap[A](
        apply _,
        _.value,
      )

    implicit lazy val rowReader = RowReader.longReader.map(v => apply(v))
    implicit lazy val rowWriter = RowWriter.longWriter.mapWriter[A](_.value)

    implicit def toSqlString(a: A): SqlString =
      SqlString.number(a.value)

    def apply(value: Long): A

    def unapply(value: Long): Option[A] =
      Some(apply(value))

    def unapply(value: String): Option[A] =
      value
        .toLongOption
        .map(apply)

    implicit val zstringer: ZStringer[A] =
      new ZStringer[A] {
        override def toZString(a: A): ZString =
          a.value
      }

  }

}

trait LongValue {
  val value: Long
}
