package a8.shared.ops


import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class IntOps(private val i: Int) extends AnyVal {
  def millis = FiniteDuration(i, TimeUnit.MILLISECONDS)
  def ms = FiniteDuration(i, TimeUnit.MILLISECONDS)
  def second = FiniteDuration(i, TimeUnit.SECONDS)
  def seconds = FiniteDuration(i, TimeUnit.SECONDS)
  def minute = FiniteDuration(i, TimeUnit.MINUTES)
  def minutes = FiniteDuration(i, TimeUnit.MINUTES)
  def hour = FiniteDuration(i, TimeUnit.HOURS)
  def hours = FiniteDuration(i, TimeUnit.HOURS)
  def day = FiniteDuration(i, TimeUnit.DAYS)
  def days = FiniteDuration(i, TimeUnit.DAYS)
}
