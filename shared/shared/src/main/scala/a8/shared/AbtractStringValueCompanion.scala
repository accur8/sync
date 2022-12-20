package a8.shared


import SharedImports._
import a8.shared.ZString.ZStringer
import a8.shared.jdbcf.{RowReader, RowWriter, SqlString}
import a8.shared.json.{JsonCodec, JsonTypedCodec, ast}
import sourcecode.Text.generate
import scala.language.implicitConversions
import scala.reflect.{ClassTag, classTag}

abstract class AbstractStringValueCompanion[A] {

  implicit val fromString: FromString[A] =
    new FromString[A] {
      override def fromString(value: String): Option[A] =
        valueFromString(value).some
    }

  def valueToString(a: A): String
  def valueFromString(s: String): A


  implicit lazy val zioEq: zio.prelude.Equal[A] =
    zio.prelude.Equal.make((a, b) => valueToString(a) == valueToString(b))

  implicit lazy val catsEq: cats.kernel.Eq[A] =
    cats.kernel.Eq.by[A, String](valueToString)

  implicit lazy val jsonCodec: JsonCodec[A] =
    jsonTypedCodec.asJsonCodec

  implicit lazy val jsonTypedCodec: JsonTypedCodec[A, ast.JsStr] =
    JsonCodec.string.dimap[A](
      valueFromString,
      valueToString,
    )

  implicit val rowReader: RowReader[A] = RowReader.stringReader.map(s => valueFromString(s.rtrim))
  implicit val rowWriter: RowWriter[A] = RowWriter.stringWriter.mapWriter[A](valueToString)

  implicit def toSqlString(a: A): SqlString =
    SqlString.escapedString(valueToString(a))

  implicit val zstringer: ZStringer[A] =
    new ZStringer[A] {
      override def toZString(a: A): ZString =
        ZString.str(valueToString(a))
    }

}
