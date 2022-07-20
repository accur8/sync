package a8.shared

import a8.shared.json.{JsonCodec, JsonTypedCodec, ast}

object SecretValue {

  abstract class Companion[A <: SecretValue] extends StringValue.Companion[A] {
    override implicit lazy val jsonTypedCodec: JsonTypedCodec[A, ast.JsStr] =
      JsonCodec.string.dimap[A](apply, _.masked)
  }

}

trait SecretValue extends StringValue {
  lazy val masked: String = value.map(_ => '*').mkString

  override def toString: String =
    s"SecretValue(${masked})"
}
