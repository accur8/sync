package a8.shared.app


object Logger {

  def fromName(name: String): Logger = ???
  def of[A]: Logger = ???

  sealed trait LogLevel
  object LogLevel {
    val TRACE = new LogLevel {}
    val DEBUG = new LogLevel {}
    val INFO = new LogLevel {}
    val WARN = new LogLevel {}
    val ERROR = new LogLevel {}
  }

}


trait Logger {

  def trace(args: Any*): Unit
  def debug(args: Any*): Unit
  def info(args: Any*): Unit
  def warn(args: Any*): Unit
  def error(args: Any*): Unit

}
