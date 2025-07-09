package a8.common.logging


import a8.common.logging.LogMessage.impl.LogPart.JsonConsoleValue
import org.scalajs.dom
import LoggingOps._
import scala.scalajs.js

case class JavascriptLogger(name: String, contextStack: js.Array[String]) extends Logger {
  
  override def log(level: Level, msg: String, th: Throwable)(implicit trace: Trace): Unit = {
    impl(level, msg, th, IndexedSeq.empty)
  }

  override def log(level: Level, msg: LogMessage)(implicit trace: Trace): Unit = {
    val resolvedMsg = msg.resolve
    impl(level, resolvedMsg.consoleMessage.getOrElse(resolvedMsg.message), resolvedMsg.throwable.getOrElse(null), resolvedMsg.consoleValues)
  }

  def impl(level: Level, msg: String, th: Throwable, consoleValues: Seq[Any])(implicit trace: Trace): Unit = {
    if (isLevelEnabled(level)) {

      val seqValues: Seq[js.Any] =
        consoleValues
          .map {
            case jscv: JsonConsoleValue[?] =>
              val json = jscv.asJsonStr
              js.JSON.parse(json)
            case v =>
              v.asInstanceOf[js.Any]
          }

      val time = new scalajs.js.Date()

      val contextStackStr =
        if (contextStack.nonEmpty)
          " | " + contextStack.mkString(" ")
        else
          ""

      lazy val stackTrace = th.stackTraceAsString
      val formattedMessage = f"""${time.getHours().toInt}%02d:${time.getMinutes().toInt}%02d:${time.getSeconds().toInt}%02d.${time.getMilliseconds().toInt}%03d${contextStackStr} | ${level.name.toUpperCase}%5s | ${name} | ${msg}${if (th == null) "" else "\n" + stackTrace}${if (consoleValues.isEmpty) "" else "\n"} - (${trace.file.value}:${trace.line.value})"""

      level match {
        case Level.Error | Level.Fatal =>
          dom.console.error(formattedMessage, seqValues*)
        case Level.Warn =>
          dom.console.warn(formattedMessage, seqValues*)
        case Level.Info =>
          dom.console.info(formattedMessage, seqValues*)
        case _ =>
          dom.console.log(formattedMessage, seqValues*)
      }
    }
  }


  override def setLevel(l: Level): Unit =
    ()

  override def isLevelEnabled(level: Level): Boolean =
    level.ordinal >= JavascriptLoggingFactory.globalLevel.ordinal

}
