package a8.shared.json


import a8.shared.SingleArgConstructor
import a8.shared.json.impl.{JawnFacade, JsDocMixin, JsValMixin}
import org.typelevel.jawn.Facade

import language.implicitConversions
import scala.util.Try

object ast {

  object JsVal {
    implicit def jawnFacade: Facade[JsVal] = JawnFacade
  }
  sealed trait JsVal extends JsValMixin

  object JsDoc {
    val empty = JsDoc(JsNothing, None)
  }
  case class JsDoc(value: JsVal, parent: Option[(JsDoc, Either[String,Int])] = None) extends JsVal with JsDocMixin

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
