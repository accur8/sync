package net.model3.logging.logback

import a8.common.logging.{Level, LoggingBootstrapConfig}
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.jul.LevelChangePropagator
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.classic.spi.Configurator.ExecutionStatus
import ch.qos.logback.core.spi.ContextAwareBase
import ch.qos.logback.core.status.{Status, StatusListener}
import org.slf4j.bridge.SLF4JBridgeHandler

import java.io.{File, FileOutputStream}
import scala.jdk.CollectionConverters.*

object UnitTestsLogbackConfigurator {


}

class UnitTestsLogbackConfigurator extends LogbackConfigurator with Configurator { outer =>

  override def configure(loggerContext: LoggerContext): Configurator.ExecutionStatus = {

    LoggingBootstrapConfig.finalizeConfig(
      LoggingBootstrapConfig(
        overrideSystemErr = false,
        overrideSystemOut = false,
        setDefaultUncaughtExceptionHandler = true,
        fileLogging = false,
        consoleLogging = false,
        hasColorConsole = LoggingBootstrapConfig.defaultHasColorConsole,
        appName = "unittests",
        defaultLogLevel = Level.Debug,
      )
    )

    super.configure(loggerContext)

  }

}
