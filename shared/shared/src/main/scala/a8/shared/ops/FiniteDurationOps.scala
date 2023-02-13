package a8.shared.ops

import scala.concurrent.duration.FiniteDuration
import zio.Duration

class FiniteDurationOps(private val value: FiniteDuration) extends AnyVal {
  def toZio: Duration = zio.Duration(value._1, value._2)
}
