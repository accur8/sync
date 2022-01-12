package a8.shared.app

import a8.shared.SharedImports._
import a8.shared.app.LoggerF.Pos
import wvlet.log.{LazyLogger, LogLevel, LogRecord, LogSource, Logger}

trait IOLogger extends LazyLogger {
  implicit lazy val loggerIO: LoggerF[IO] = LoggerF.create[IO](logger)
}
