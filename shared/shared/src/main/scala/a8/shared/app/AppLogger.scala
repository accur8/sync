package a8.shared.app

import org.slf4j.LoggerFactory
import wvlet.log.{Logger, _}

import java.util.logging.Level

trait AppLogger extends LazyLogger { self: BootstrappedIOApp =>

  val bootstrapConfig: BootstrapConfig

  lazy val bootstrapInit = {

    def configureLogLevels() = {

      Logger.setDefaultLogLevel(bootstrapConfig.defaultLogLevel)

      def logger(name: String): Logger = Logger(name)

      List(
        "io.undertow",
      ) foreach { n =>
        logger(n).setLogLevel(LogLevel.DEBUG)
      }

      List(
        "a8.wsjdbc.client",
        "sun",
        "jdk",
        "org.apache.http",
        "org.apache.pulsar.client.impl.ProducerImpl",
        "org.apache.pulsar.client.impl.PersistentAcknowledgmentsGroupingTracker",
        "org.apache.pulsar",
        "org.asynchttpclient.netty",
        "org.apache.log4j",
        "org.postgresql",
        "io.netty",
        "org.xnio",
        "com.oath.halodb",
        "org.jboss",
        "com.sun.mail",
        "com.zaxxer.hikari",
        "io.netty",
        "jakarta",
        "org.asynchttpclient",
        "software.amazon.awssdk",
      ) foreach { n =>
        logger(n).setLogLevel(LogLevel.INFO)
      }

    }

    Logger.setDefaultFormatter(A8LogFormatter.Console)
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

  bootstrapInit

}
