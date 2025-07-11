package a8.shared

import a8.shared.jdbcf.{RowReader, RowWriter, SqlString}
import org.typelevel.ci.CIString
import SharedImports.*
import a8.shared.ZString.{HasZString, ZStringer}
import a8.shared.json.{JsonCodec, JsonTypedCodec, ast}
import a8.sync.http.Body

import language.implicitConversions
import scala.reflect.ClassTag

object StringValue {

  import SharedImports.given

  abstract class Companion[A <: StringValue] extends AbstractStringValueCompanion[A] {

    given CanEqual[A, A] = CanEqual.derived

    override def valueToString(a: A): String = a.value

    override def valueFromString(s: String): A = apply(s)

    def apply(value: String): A

    def unapply(value: String): Option[A] =
      Some(apply(value))

  }


  trait CIStringValue {
    val value: CIString
    def asString = value.toString
    override def toString = value.toString
  }

  abstract class CIStringValueCompanion[A <: CIStringValue] {

    given CanEqual[A,A] = CanEqual.derived

    implicit val fromString: FromString[A] =
      new FromString[A] {
        override def fromString(value: String): Option[A] =
          Some(apply(value))
      }

    implicit val catsEq: cats.kernel.Eq[A] =
      cats.kernel.Eq.by[A,CIString](_.value)

    implicit val jsonCodec: JsonTypedCodec[A, ast.JsStr] =
      JsonCodec.string.dimap[A](
        apply,
        _.value.toString,
      )

    implicit val rowReader: RowReader[A] = RowReader.stringReader.map(s => apply(s.trim))
    implicit val rowWriter: RowWriter[A] = RowWriter.stringWriter.mapWriter[A](_.value.toString)

    implicit def toSqlString(a: A): SqlString =
      SqlString.escapedString(a.asString)

    def apply(value: String): A =
      apply(CIString(value))

    def apply(value: CIString): A

    implicit val zstringer: ZStringer[A] =
      new ZStringer[A] {
        override def toZString(a: A): ZString =
          a.value.toString
      }

  }

}


trait StringValue {
  val value: String
}
