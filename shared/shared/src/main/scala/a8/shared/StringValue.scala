package a8.shared

import a8.shared.jdbcf.{RowReader, RowWriter, SqlString}
import org.typelevel.ci.CIString
import SharedImports._
import a8.shared.json.{JsonCodec, JsonTypedCodec, ast}

import language.implicitConversions

object StringValue {

  abstract class Companion[A <: StringValue] {

    implicit lazy val catsEq: cats.kernel.Eq[A] =
      cats.kernel.Eq.by[A,String](_.value)

    implicit lazy val jsonCodec: JsonCodec[A] =
      jsonTypedCodec.asJsonCodec

    implicit lazy val jsonTypedCodec: JsonTypedCodec[A, ast.JsStr] =
      JsonCodec.string.dimap[A](
        apply _,
        _.value.toString,
      )

    implicit val rowReader = RowReader.stringReader.map(s => apply(s.trim))
    implicit val rowWriter = RowWriter.stringWriter.mapWriter[A](_.value)

    implicit def toSqlString(a: A): SqlString =
      SqlString.escapedString(a.value)

    def apply(value: String): A

    def unapply(value: String): Option[A] =
      Some(apply(value))

  }


  trait CIStringValue {
    val value: CIString
    def asString = value.toString
  }

  abstract class CIStringValueCompanion[A <: CIStringValue] {

    implicit val catsEq: cats.kernel.Eq[A] =
      cats.kernel.Eq.by[A,CIString](_.value)

    implicit val jsonCodec =
      JsonCodec.string.dimap[A](
        apply _,
        _.value.toString,
      )

    implicit val rowReader = RowReader.stringReader.map(s => apply(s.trim))
    implicit val rowWriter = RowWriter.stringWriter.mapWriter[A](_.value.toString)

    implicit def toSqlString(a: A): SqlString =
      SqlString.escapedString(a.asString)

    def apply(value: String): A =
      apply(CIString(value))

    def apply(value: CIString): A

  }

}


trait StringValue {
  val value: String
}
