package a8.common.logging.println

import a8.common.logging.{Level, Logger}
import zio.Trace

import java.text.SimpleDateFormat
import java.util.Calendar

object PrintlnLogger {

  val sdf = new SimpleDateFormat("HH:mm:ss.SSS")
  var loggingLevel: Level = Level.Debug

  var globalLevel = Level.Trace

}

class PrintlnLogger(override val name: String) extends Logger {

  import PrintlnLogger._


  override def log(level: Level, msg: String, th: Throwable)(implicit trace: Trace): Unit = {
    if (isLevelEnabled(level)) {
      val printStream = if (level.ordinal >= Level.Warn.ordinal) System.err else System.out

      val time = sdf.format(Calendar.getInstance().getTime)
      printStream.println(f"${time} | ${level.toString.toUpperCase}%-5s | ${Thread.currentThread().getName} | ${name} | ${msg}")

      if (th != null) {
        th.printStackTrace(printStream)
      }
    }
  }


  override def setLevel(l: Level): Unit =
    ()

  override def isLevelEnabled(level: Level): Boolean =
    level >= globalLevel

//  override def factory: Factory = PrintlnLogger.impl.factory

}
