package a8.shared.json.impl

import a8.shared.json.{JsonReadOptions, ReadError}
import a8.shared.json.ReadError.SingleReadError
import a8.shared.json.ast.JsDoc.JsDocRoot
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
        JsDocRoot(value = JsObj(l.values ++ r.values))
      case t =>
        sys.error(s"don't know how to handle ${t}")
    }

  def error(message: String)(implicit readOptions: JsonReadOptions): ReadError = SingleReadError(message, this)
  def errorL(message: String)(implicit readOptions: JsonReadOptions): Left[ReadError,Nothing] = Left(error(message))

  def isRoot: Boolean
  def path: String

}
