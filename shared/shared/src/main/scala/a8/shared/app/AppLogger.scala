package a8.shared.app

import org.slf4j.LoggerFactory
import wvlet.log.{Logger, _}

import java.util.logging.Level

trait AppLogger extends LazyLogger { self: BootstrappedIOApp =>

  lazy val bootstrapInit = {

    def configureLogLevels() = {

      Logger.setDefaultLogLevel(bootstrapConfig.defaultLogLevel)

      self
        .initialLogLevels
        .foreach { case (name, level) =>
          Logger(name).setLogLevel(level)
        }

    }

    if (bootstrapConfig.colorConsole) {
      Logger.setDefaultFormatter(A8LogFormatter.ColorConsole)
    } else {
      Logger.setDefaultFormatter(A8LogFormatter.MonochromeConsole)
    }

    Logger.scheduleLogLevelScan

    configureLogLevels()

    def enableRollingFileLogging(suffix: String, logLevel: Level, maxNumberOfFiles: Int = 30, maxSizeInBytes: Long = 104857600 /*100 MB*/): Unit = {
      val logFile = bootstrapConfig.logsDir.resolved.file(bootstrapConfig.appName.value.toLowerCase + "-" + suffix)
      val handler =
        new LogRotationHandler(
          fileName = logFile.toString,
          maxNumberOfFiles = maxNumberOfFiles,
          maxSizeInBytes = maxSizeInBytes,
          formatter = A8LogFormatter.File
        )
      handler.setLevel(logLevel)
      Logger.rootLogger.addHandler(handler)
    }

    if ( !bootstrapConfig.consoleLogging ) {
      Logger.rootLogger.clearAllHandlers
    }

    if ( bootstrapConfig.fileLogging ) {
      enableRollingFileLogging("details.log", bootstrapConfig.defaultLogLevel.jlLevel)
      enableRollingFileLogging("errors.log", LogLevel.WARN.jlLevel)
    }

    logger.info("logging initialized")

  }

}
