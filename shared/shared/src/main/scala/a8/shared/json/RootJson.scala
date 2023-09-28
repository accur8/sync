package a8.shared.json

import a8.shared.json.ast.{JsDoc, JsNothing}


object RootJson {
  implicit val jsonCode: JsonCodec[RootJson] =
    new JsonCodec[RootJson] {
      override def write(a: RootJson): ast.JsVal = JsNothing
      override def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, RootJson] =
        Right(RootJson(doc.root))
    }
}

case class RootJson(value:JsDoc)
