package a8.common.logging

import java.io.{PrintWriter, StringWriter}

implicit class LoggingThrowableOps(private val _value: Throwable) extends AnyVal {

  def rootCause: Throwable = allCauses.last

  def allCauses: Vector[Throwable] = {
    _value.getCause match {
      case null => Vector(_value)
      case c if _value eq c => Vector(_value)
      case c => Vector(_value) ++ new LoggingThrowableOps(c).allCauses
    }
  }

  def stackTraceAsString: String = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    _value.printStackTrace(pw)
    pw.flush()
    pw.close()
    sw.toString
  }

}

