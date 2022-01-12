package a8.shared.ops

import java.io.{PrintWriter, StringWriter}


class ThrowableOps(private val _value: Throwable) extends AnyVal {

  def rootCause: Throwable = allCauses.last

  def allCauses: Vector[Throwable] = {
    _value.getCause match {
      case null => Vector(_value)
      case c if _value == c => Vector(_value)
      case c => Vector(_value) ++ new ThrowableOps(c).allCauses
    }
  }

  def stackTraceAsString = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    _value.printStackTrace(pw)
    pw.flush()
    pw.close()
    sw.toString
  }

}
