package a8.shared.ops

import scala.concurrent.duration.FiniteDuration

class FiniteDurationOps(private val value: FiniteDuration) extends AnyVal {
  def toZio = zio.Duration(value._1, value._2)
}
