package a8.shared

import a8.shared.jdbcf.{RowReader, RowWriter, SqlString}
import a8.shared.json.{JsonCodec, JsonTypedCodec, ast}
import SqlString._
import a8.shared.ZString.ZStringer

import language.implicitConversions

object IntValue {

  abstract class Companion[A <: IntValue] extends NumberValue.Companion[A, Int]

}

trait IntValue extends NumberValue[Int] {
  val value: Int
}
