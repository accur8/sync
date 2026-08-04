package net.model3.logging.logback

import a8.common.logging.{Level, LoggingBootstrapConfig}
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.classic.spi.Configurator.ExecutionStatus


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
