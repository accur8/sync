package a8.shared.json


import a8.shared.SingleArgConstructor
import a8.shared.json.ast.JsDoc.JsDocRoot
import a8.shared.json.impl.{HasJsValOps, JawnFacade, JsDocMixin}
import org.typelevel.jawn.Facade

import language.implicitConversions
import scala.util.Try
import zio.prelude.Equal

import scala.annotation.targetName

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
    implicit val equal: Equal[JsVal] = zio.prelude.Equal.default[JsVal]

    given [A <: JsVal, B <: JsVal]: CanEqual[A,B] = CanEqual.derived
    
  }

  sealed trait JsVal extends HasJsVal {
    override def actualJsVal: JsVal = this
  }

  object JsDoc {
    val empty: JsDoc = JsDocRoot(JsNothing)

    implicit val codec: JsonCodec[JsDoc] =
      new JsonCodec[JsDoc] {
        override def write(jsd: JsDoc): JsVal = jsd.value
        override def read(doc: JsDoc)(implicit readOptions: JsonReadOptions): Either[ReadError, JsDoc] = Right(doc)
      }

    case class JsDocRoot(value: JsVal) extends JsDoc {
      override def root: JsDocRoot = this
      override def parentOpt: Option[JsDoc] = None
      override def isRoot: Boolean = true
      override def path: String = ""
      override def actualJsVal: JsVal = value
      override def withValue(value: JsVal): JsDoc = copy(value = value)
    }

    case class JsDocPath(value: JsVal, parentDoc: JsDoc, pathInParent: Either[String,Int]) extends JsDoc {
      override def root: JsDocRoot = parentDoc.root
      override def actualJsVal: JsVal = value
      override def isRoot: Boolean = false
      override def path: String = {
        def suffix(prefixNameWithDot: Boolean) =
          pathInParent match {
            case Left(n) =>
              if (prefixNameWithDot)
                "." + n
              else
                n
            case Right(i) =>
              "[" + i + "]"
          }
        parentDoc match {
          case _: JsDocRoot =>
            suffix(false)
          case _ =>
            parentDoc.path + suffix(true)
        }
      }

      override def parentOpt: Option[JsDoc] = Some(parentDoc)
      override def withValue(value: JsVal): JsDoc = copy(value = value)
    }

  }
  sealed trait JsDoc extends HasJsVal with JsDocMixin {
    def root: JsDocRoot
    def parentOpt: Option[JsDoc]
    val value: JsVal
    def withValue(value: JsVal): JsDoc
    def removeField(fieldName: String): JsDoc = {
      value match {
        case jso: JsObj =>
          withValue(jso.removeField(fieldName))
        case _ =>
          this
      }
    }
  }

  case object JsNothing extends JsVal
  case object JsNull extends JsVal

  object JsObj {
    val empty: JsObj = JsObj(Map.empty)
    def from(values: (String,JsVal)*): JsObj = new JsObj(values.toMap)
  }
  case class JsObj(values: Map[String,JsVal]) extends JsVal {
    val size = values.size
    def removeField(fieldName: String): JsObj = copy(values = values - fieldName)
    def removeFields(fieldNames: String*): JsObj = copy(values = values -- fieldNames)
    def addField(fieldName: String, value: JsVal): JsObj = copy(values = (values + (fieldName -> value)))
    def addFields(fields: (String,JsVal)*): JsObj = copy(values = values ++ fields)
    def merge(right: JsObj): JsObj = JsObj(values ++ right.values)
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
    val empty: JsArr = JsArr(Nil)
  }
  case class JsArr(values: List[JsVal]) extends JsVal {
    val size = values.size
    def asJsObj = JsObj(values.zipWithIndex.map { case (v,i) => i.toString -> v }.toMap)
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
