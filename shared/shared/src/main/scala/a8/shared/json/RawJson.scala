package a8.shared.json

import a8.shared.json.ast.{JsDoc, JsNothing}


object RawJson {
  implicit val jsonCodec: JsonCodec[RawJson] =
    new JsonCodec[RawJson] {
      /**
       * we don't want to write the value back to the json for fear of overgrowing json docs
       */
      override def write(a: RawJson): ast.JsVal = JsNothing
      override def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, RawJson] =
        Right(RawJson(doc.parentOpt.getOrElse(JsDoc.empty)))
    }
}

case class RawJson(value:JsDoc)
