package a8.common.logging

class UnitTestsLoggingBootstrapConfigServiceLoader extends LoggingBootstrapConfigServiceLoader {

  override def priority: Int =
    super.priority + 100

  override def loggingBootstrapConfig: LoggingBootstrapConfig =
    LoggingBootstrapConfig(
      overrideSystemErr = false,
      overrideSystemOut = false,
      setDefaultUncaughtExceptionHandler = true,
      fileLogging = false,
      consoleLogging = true,
      hasColorConsole = LoggingBootstrapConfig.defaultHasColorConsole,
      appName = "unittests",
      defaultLogLevel = Level.Debug,
    )

}
