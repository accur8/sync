package a8.shared.app

import wvlet.log._

import java.io.{PrintWriter, StringWriter}
import java.time.{Instant, ZonedDateTime}
import java.time.format.DateTimeFormatter

object A8LogFormatter {

  object ColorConsole extends A8LogFormatter

  object MonochromeConsole extends A8LogFormatter {
    override def withColor(prefix: String, s: String): String = s
    override def highlightLog(level: LogLevel, message: String): String = message
  }

  object File extends A8LogFormatter {
    override val levelWidth = 5
    override def withColor(prefix: String, s: String): String = s
    override def highlightLog(level: LogLevel, message: String): String = message
  }

}

trait A8LogFormatter extends LogFormatter {

  protected val timestampFormatter = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss.SSS")
  protected val levelWidth = 14

  object impl {
    // bypass the deprecation warning
    object Outer { @deprecated("", "") class Inner { def getThreadID(r: LogRecord) = r.getThreadID }; object Inner extends Inner }
  }

  override def formatLog(r: LogRecord): String = {
    val dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(r.getMillis), LogTimestampFormatter.systemZone)
    val timestamp = withColor(Console.BLUE, timestampFormatter.format(dateTime))
    val level = highlightLog(r.level, r.level.name.toUpperCase).padTo(levelWidth, ' ')
    val thread = withColor(Console.WHITE, impl.Outer.Inner.getThreadID(r).toString)
    val loggerName = withColor(Console.WHITE, r.getLoggerName)
    val message = highlightLog(r.level, r.getMessage)
    val location = r.source.map(source => s" ${withColor(Console.BLUE, s"- (${source.fileLoc})")}").getOrElse("")
    val log = s"${timestamp} | ${level} | ${thread} | ${loggerName} | ${message}${location}"
    appendStackTrace(log, r)
  }

  protected def withColor(prefix: String, s: String): String =
    LogFormatter.withColor(prefix, s)

  protected def highlightLog(level: LogLevel, message: String): String =
    LogFormatter.highlightLog(level, message)

  protected def appendStackTrace(m: String, r: LogRecord): String = {
    r.cause match {
      case Some(ex) =>
        s"${m}\n${highlightLog(r.level, formatStacktrace(ex))}"
      case None =>
        m
    }
  }

  protected def formatStacktrace(e: Throwable): String = {
    e match {
      case null =>
        // Exception cause can be null
        ""
      case _ =>
        val trace = new StringWriter()
        e.printStackTrace(new PrintWriter(trace))
        trace.toString
    }
  }
}
