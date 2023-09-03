package a8.common.logging

object JsonApi {
  def apply[A: JsonApi]: JsonApi[A] = implicitly[JsonApi[A]]
}

trait JsonApi[A] {
  def toJsonStr(a: A): String
}
