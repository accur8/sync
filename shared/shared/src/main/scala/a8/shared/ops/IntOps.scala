package a8.shared.ops


import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class IntOps(private val i: Int) extends AnyVal {
  def millis: FiniteDuration = FiniteDuration(i, TimeUnit.MILLISECONDS)
  def ms: FiniteDuration = FiniteDuration(i, TimeUnit.MILLISECONDS)
  def second: FiniteDuration = FiniteDuration(i, TimeUnit.SECONDS)
  def seconds: FiniteDuration = FiniteDuration(i, TimeUnit.SECONDS)
  def minute: FiniteDuration = FiniteDuration(i, TimeUnit.MINUTES)
  def minutes: FiniteDuration = FiniteDuration(i, TimeUnit.MINUTES)
  def hour: FiniteDuration = FiniteDuration(i, TimeUnit.HOURS)
  def hours: FiniteDuration = FiniteDuration(i, TimeUnit.HOURS)
  def day: FiniteDuration = FiniteDuration(i, TimeUnit.DAYS)
  def days: FiniteDuration = FiniteDuration(i, TimeUnit.DAYS)
}
