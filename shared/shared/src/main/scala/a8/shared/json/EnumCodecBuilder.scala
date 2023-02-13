package a8.shared.json


import a8.shared.json.ast.JsStr
import a8.shared.SharedImports._

object EnumCodecBuilder {

  def apply[A <: enumeratum.values.StringEnumEntry](`enum`: enumeratum.values.StringEnum[A]): JsonCodec[A] =
    new JsonCodec[A] {

      lazy val enumValuesByName =
        `enum`
          .values
          .map(e => e.value.toCi -> e)
          .toMap

      override def write(a: A): ast.JsVal =
        JsStr(a.value)

      override def read(doc: ast.JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, A] =
        doc.value match {
          case JsStr(s) =>
            enumValuesByName.get(s.toCi) match {
              case Some(a) =>
                Right(a)
              case None =>
                doc.errorL(s"enum ${s} not found")
            }
          case _ =>
            doc.errorL("expected a string")
        }
    }

  def apply[A <: enumeratum.EnumEntry](`enum`: enumeratum.Enum[A]): JsonCodec[A] =
    new JsonCodec[A] {

      lazy val enumValuesByName =
        `enum`
          .values
          .map(e => e.entryName.toCi -> e)
          .toMap

      override def write(a: A): ast.JsVal =
        JsStr(a.entryName)

      override def read(doc: ast.JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, A] =
        doc.value match {
          case JsStr(s) =>
            enumValuesByName.get(s.toCi) match {
              case Some(a) =>
                Right(a)
              case None =>
                doc.errorL(s"enum ${s} not found")
            }
          case _ =>
            doc.errorL("expected a string")
        }
    }

}
