package a8.shared.ops


import java.time.LocalDateTime
import scala.concurrent.duration.Duration

class LocalDateTimeOps(private val localDateTime: LocalDateTime) extends AnyVal {
  def +(duration: Duration): LocalDateTime = {
    localDateTime.plus(java.time.Duration.ofMillis(duration.toMillis))
  }
}
