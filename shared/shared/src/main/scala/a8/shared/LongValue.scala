package a8.shared


import a8.shared.jdbcf.{RowReader, RowWriter}
import a8.shared.json.JsonTypedCodec
import a8.shared.ZString.ZStringer

import language.implicitConversions

object LongValue {

  abstract class Companion[A <: LongValue] extends NumberValue.Companion[A, Long]

}

trait LongValue extends NumberValue[Long] {
  val value: Long
}
