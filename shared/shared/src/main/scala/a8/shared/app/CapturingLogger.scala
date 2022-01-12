package a8.shared.app

import a8.shared.AtomicBuffer
import a8.shared.SharedImports.Sync
import a8.shared.app.LoggerF.Pos
import cats.Id
import wvlet.log.{LogLevel, LogRecord, Logger}

object CapturingLogger {

  def apply(name: String): CapturingLogger = {
    val accumulator = AtomicBuffer[LogRecord]
    val logger =
      new LoggerF[Id] {
        override protected def isEnabled(logLevel: LogLevel): Boolean = true
        override protected def impl(logLevel: LogLevel, message: String, cause: Option[Throwable], pos: Pos): Id[Unit] = {
          val logRecord = LogRecord(logLevel, Some(pos.asLogSource), message, cause)
          accumulator.append(logRecord)
        }
      }
    CapturingLogger(logger, accumulator)
  }

}


case class CapturingLogger(logger: LoggerF[Id], accumulator: AtomicBuffer[LogRecord])
