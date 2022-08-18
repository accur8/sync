package a8.shared.json


import a8.shared.SingleArgConstructor
import a8.shared.json.impl.{HasJsValOps, JawnFacade, JsDocMixin}
import org.typelevel.jawn.Facade

import language.implicitConversions
import scala.util.Try

object ast {

  object HasJsVal {
    implicit def hasJsValOps(hjsv: HasJsVal): HasJsValOps =
      new HasJsValOps(hjsv)
  }
  sealed trait HasJsVal {
    def actualJsVal: JsVal
  }

  object JsVal {
    implicit def jawnFacade: Facade[JsVal] = JawnFacade
    implicit val equal = zio.prelude.Equal.default[JsVal]
  }

  sealed trait JsVal extends HasJsVal {
    override def actualJsVal: JsVal = this
  }

  object JsDoc {
    val empty = JsDoc(JsNothing, None)

    implicit val codec: JsonCodec[JsDoc] =
      new JsonCodec[JsDoc] {
        override def write(jsd: JsDoc): JsVal = jsd.value
        override def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, JsDoc] = Right(doc)
      }
  }
  case class JsDoc(value: JsVal, parent: Option[(JsDoc, Either[String,Int])] = None) extends HasJsVal with JsDocMixin {
    override def actualJsVal = value
  }

  case object JsNothing extends JsVal
  case object JsNull extends JsVal

  object JsObj {
    val empty = JsObj(Map.empty)
    def from(values: (String,JsVal)*): JsObj = new JsObj(values.toMap)
  }
  case class JsObj(values: Map[String,JsVal]) extends JsVal {
    val size = values.size
    def removeField(fieldName: String): JsObj = copy(values = values - fieldName)
    def removeFields(fieldNames: String*): JsObj = copy(values = values -- fieldNames)
    def addField(fieldName: String, value: JsVal) = copy(values = (values + (fieldName -> value)))
    def addFields(fields: (String,JsVal)*) = copy(values = values ++ fields)
  }

  def resolveAliases(aliases: Iterable[String], jsdoc: JsDoc): JsDoc = {
    jsdoc.value match {
      case jso: JsObj =>
        aliases.find(a => jso.values.contains(a)) match {
          case Some(a) =>
            jsdoc(a)
          case None =>
            jsdoc(aliases.head)
        }
      case _ =>
        jsdoc(aliases.head)
    }
  }

  object JsArr {
    val empty = JsArr(Nil)
  }
  case class JsArr(values: List[JsVal]) extends JsVal {
    val size = values.size
  }

  object JsBool {
    def apply(b: Boolean): JsBool =
      b match {
        case true =>
          JsTrue
        case false =>
          JsFalse
      }

    def unapply(value: JsBool): Option[Boolean] =
      Some(value.value)

  }
  sealed trait JsBool extends JsVal {
    val value: Boolean
  }
  case object JsTrue extends JsBool {
    override val value = true
  }
  case object JsFalse extends JsBool {
    override val value = false
  }

  object JsNum {
    def apply[A](a: A)(implicit constructor: SingleArgConstructor[A,BigDecimal]): JsNum =
      JsNum(constructor.construct(a))
  }
  case class JsNum(value: BigDecimal) extends JsVal
  case class JsStr(value: String) extends JsVal

}
