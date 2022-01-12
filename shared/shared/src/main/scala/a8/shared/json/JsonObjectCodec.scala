package a8.shared.json


import a8.shared.Meta.Constructors
import a8.shared.json.JsonObjectCodecBuilder.Parm
import a8.shared.json.ReadError.ReadErrorException
import a8.shared.json.ast.{JsDoc, JsNothing, JsObj}
import cats.{Eval, Foldable}
import fs2.Chunk
import a8.shared.SharedImports._

object JsonObjectCodec {

//  case class DecodingHints(
//    reportUnusedFields: Boolean,
//    ignoreUnusedFields: ((String,Json)) => Boolean = { _ => false }
//  )

}

class JsonObjectCodec[A](
  parms: Vector[Parm[A]],
  constructors: Constructors[A],
) extends JsonTypedCodec[A,JsObj] {

  lazy val parmsByName = parms.toMapTransform(_.name)

  override def write(a: A): ast.JsObj = {
    JsObj(
      parms
        .iterator
        .map(p => p.name -> p.write(a))
        .toMap
    )
  }

  override def read(doc: JsDoc): Either[ReadError, A] = {
    doc.value match {
      case jo: JsObj =>
        try {
          val valuesIterator =
            parms
              .iterator
              .map { parm =>
                parm.read(doc(parm.name)) match {
                  case Left(re) =>
                    throw re.asException
                  case Right(v) =>
                    v
                }
              }
          val unusedFields = jo.values.filter(f => !parmsByName.contains(f._1))
          if ( unusedFields.isEmpty ) {
            Right(constructors.iterRawConstruct(valuesIterator))
          } else {
            doc.errorL(s"json object has the following unused fields (${unusedFields.map(_._1).mkString(" ")}) valid field names are (${parms.map(_.name).mkString(" ")})")
          }
        } catch {
          case ReadErrorException(re) =>
            Left(re)
        }

      case _ =>
        doc.errorL(s"expected a json object")
    }
  }

}
