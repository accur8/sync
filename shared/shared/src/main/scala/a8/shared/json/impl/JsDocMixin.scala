package a8.shared.json.impl

import a8.shared.json.ReadError
import a8.shared.json.ReadError.SingleReadError
import a8.shared.json.ast._

trait JsDocMixin { self: JsDoc =>
  def isEmpty: Boolean =
    value match {
      case JsNull | JsNothing =>
        true
      case _ =>
        false
    }

  def merge(right: JsDoc): JsDoc =
    merge(right.value)

  // ??? TODO fixme
  def merge(right: JsVal): JsDoc =
    (value, right) match {
      case (l: JsObj, r: JsObj) =>
        copy(value = JsObj(l.values ++ r.values))
      case t =>
        sys.error(s"don't know how to handle ${t}")
    }

  def error(message: String): ReadError = SingleReadError(message, this)
  def errorL(message: String): Left[ReadError,Nothing] = Left(error(message))

  def isRoot = parent.isEmpty

  def path: String = {
    parent match {
      case None =>
        ""
      case Some(t) =>
        val prefix = Some(t._1).filterNot(_.isRoot).map(_.path)
        t._2 match {
          case Left(n) =>
            prefix match {
              case Some(p) =>
                p + "." + n
              case None =>
                n
            }
          case Right(i) =>
            prefix match {
              case Some(p) =>
                p + "[" + i + "]"
              case None =>
                i.toString
            }
        }
    }
  }
//
//  def apply(name: String): JsDoc = {
//    val selectedValue =
//      value match {
//        case jobj: JsObj =>
//          jobj
//            .values
//            .get(name)
//            .getOrElse(JsNothing)
//        case _ =>
//          JsNothing
//      }
//    JsDoc(selectedValue, Some(this -> Left(name)))
//  }
//
//  def apply(index: Int): JsDoc = {
//    val selectedValue =
//      value match {
//        case jarr: JsArr =>
//          if (index >= 0 && index <= jarr.values.size)
//            jarr.values(index)
//          else
//            JsNothing
//        case _ =>
//          JsNothing
//      }
//    JsDoc(selectedValue, Some(this -> Right(index)))
//  }

}
