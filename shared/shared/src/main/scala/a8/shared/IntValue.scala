package a8.shared

import a8.shared.jdbcf.{RowReader, RowWriter}
import a8.shared.json.JsonTypedCodec
import a8.shared.ZString.ZStringer

import language.implicitConversions

object IntValue {

  abstract class Companion[A <: IntValue] extends NumberValue.Companion[A, Int]

}

trait IntValue extends NumberValue[Int] {
  val value: Int
}
