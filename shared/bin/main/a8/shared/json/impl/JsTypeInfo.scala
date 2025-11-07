package a8.shared.json.impl


import a8.shared.json.ast.{JsArr, JsBool, JsDoc, JsNum, JsObj, JsStr, JsVal}

import scala.reflect.{ClassTag, classTag}
import a8.shared.SharedImports._

object JsTypeInfo {
  implicit case object str extends JsTypeInfo[JsStr]("string")
  implicit case object obj extends JsTypeInfo[JsObj]("object")
  implicit case object arr extends JsTypeInfo[JsArr]("array")
  implicit case object bool extends JsTypeInfo[JsBool]("bool")
  implicit case object num extends JsTypeInfo[JsNum]("number")
}

abstract class JsTypeInfo[A <: JsVal : ClassTag](
  val name: String,
) {
  val classTagA: ClassTag[A] = classTag[A]
  def isInstance(jsv: JsVal): Boolean = classTagA.runtimeClass.isInstance(jsv)
  def cast(jsv: JsVal): Option[A] = isInstance(jsv).toOption(jsv.asInstanceOf[A])
}
