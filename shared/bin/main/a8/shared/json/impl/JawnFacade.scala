package a8.shared.json.impl

import a8.shared.json.ast._
import org.typelevel.jawn.Facade.SimpleFacade

object JawnFacade extends SimpleFacade[JsVal] {
  override def jarray(vs: List[JsVal]): JsVal = JsArr(vs)
  override def jobject(vs: Map[String, JsVal]): JsVal = JsObj(vs)
  override def jnull: JsVal = JsNull
  override def jfalse: JsVal = JsFalse
  override def jtrue: JsVal = JsTrue
  override def jnum(s: CharSequence, decIndex: Int, expIndex: Int): JsVal = JsNum(BigDecimal(s.toString))
  override def jstring(s: CharSequence): JsVal = JsStr(s.toString)
}
