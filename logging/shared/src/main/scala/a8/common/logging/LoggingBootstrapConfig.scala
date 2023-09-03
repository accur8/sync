package a8.common.logging


import a8.common.logging.LoggingBootstrapConfig.fileFromProperty

object LoggingBootstrapConfig {

  private var _globalBootstrapConfig: Option[LoggingBootstrapConfig] =
    None

  def finalizeConfig(bootstrapConfig: LoggingBootstrapConfig, applySystemPropertyOverrides: Boolean = true): Unit = {
    _globalBootstrapConfig match {
      case Some(c) =>
        sys.error("bootstrap config already initialized")
      case None =>

        def propFn(name: String, value: Boolean): Boolean =
          System.getProperty(name, value.toString)
            .toBoolean

        val resolvedConfig = bootstrapConfig.overrideWith(propFn)

        _globalBootstrapConfig = Some(resolvedConfig)
    }
  }

  def globalBootstrapConfig =
    _globalBootstrapConfig match {
      case None =>
        sys.error("globalBootstrapConfig has not been configured")
      case Some(c) =>
        c
    }

  def fileFromProperty(propertyName: String, default: String): java.io.File =
    new java.io.File(System.getProperty(propertyName, default))

  lazy val insideOfIntellij =
    try {
      getClass.getClassLoader().loadClass("com.intellij.rt.execution.application.AppMainV2"): @scala.annotation.nowarn
      true
    } catch {
      case _: Throwable =>
        false
    }

  lazy val defaultHasColorConsole: Boolean = {
    System.console() != null || insideOfIntellij
  }

  object LoggingBootstrapConfigDto {
//    val empty = LoggingBootstrapConfigDto()
    val default =
      LoggingBootstrapConfigDto(
        overrideSystemErr = Some(true),
        overrideSystemOut = Some(true),
        setDefaultUncaughtExceptionHandler = Some(true),
        autoCreateConfigDirectory = Some(false),
        fileLogging = Some(true),
        consoleLogging = Some(true),
        hasColorConsole = Some(defaultHasColorConsole),
        defaultLogLevel = Some(Level.Debug.name),
      )
  }

  case class LoggingBootstrapConfigDto(
    overrideSystemErr: Option[Boolean] = None,
    overrideSystemOut: Option[Boolean] = None,
    setDefaultUncaughtExceptionHandler: Option[Boolean] = None,
    autoCreateConfigDirectory: Option[Boolean] = None,
    fileLogging: Option[Boolean] = None,
    consoleLogging: Option[Boolean] = None,
    hasColorConsole: Option[Boolean] = None,
    defaultLogLevel: Option[String] = None,
  ) {

    def asLoggingBootstrapConfig(
      appName: String,
      configDirectory: java.io.File,
      logsDirectory: java.io.File,
    ): LoggingBootstrapConfig = {

      def v[A](fn: LoggingBootstrapConfigDto=>Option[A]): A =
        fn(this).get

      LoggingBootstrapConfig(
        overrideSystemErr = v(_.overrideSystemErr),
        overrideSystemOut = v(_.overrideSystemOut),
        setDefaultUncaughtExceptionHandler = v(_.setDefaultUncaughtExceptionHandler),
        autoCreateConfigDirectory = v(_.autoCreateConfigDirectory),
        fileLogging = v(_.fileLogging),
        consoleLogging = v(_.consoleLogging),
        hasColorConsole = v(_.hasColorConsole),
        appName = appName,
        configDirectory = configDirectory,
        logsDirectory = logsDirectory,
        defaultLogLevel = Level.valuesByLc(v(_.defaultLogLevel)),
      )
    }


    def +(right: LoggingBootstrapConfigDto): LoggingBootstrapConfigDto = {
      def resolveValue[A](getter: LoggingBootstrapConfigDto=>Option[A]): Option[A] =
        getter(right).orElse(getter(this))
      copy(
        overrideSystemErr = resolveValue(_.overrideSystemErr),
        overrideSystemOut = resolveValue(_.overrideSystemOut),
        setDefaultUncaughtExceptionHandler = resolveValue(_.setDefaultUncaughtExceptionHandler),
        autoCreateConfigDirectory = resolveValue(_.autoCreateConfigDirectory),
        fileLogging = resolveValue(_.fileLogging),
        consoleLogging = resolveValue(_.consoleLogging),
        hasColorConsole = resolveValue(_.hasColorConsole),
      )
    }

  }

}

/**
 * This is a pretty raw config.  Contradictory settings are allowed.  If you are using this it is assumed you
 * know what you are doing.  As these settings need to compose properly with each environment things run in.
 */
case class LoggingBootstrapConfig(
  overrideSystemErr: Boolean,
  overrideSystemOut: Boolean,
  setDefaultUncaughtExceptionHandler: Boolean,
  autoCreateConfigDirectory: Boolean,
  fileLogging: Boolean,
  consoleLogging: Boolean,
  hasColorConsole: Boolean = LoggingBootstrapConfig.defaultHasColorConsole,
  appName: String = "",
  configDirectory: java.io.File = fileFromProperty("config.dir", "./config"),
  logsDirectory: java.io.File = fileFromProperty("log.dir", "./logs"),
  defaultLogLevel: Level,
) {

  lazy val archivesDirectory = new java.io.File(logsDirectory, "archives")

  def overrideWith(propFn: (String,Boolean)=>Boolean) =
    copy(
      overrideSystemErr = propFn("overrideSystemErr", overrideSystemErr),
      overrideSystemOut = propFn("overrideSystemOut", overrideSystemOut),
      setDefaultUncaughtExceptionHandler = propFn("setDefaultUncaughtExceptionHandler", setDefaultUncaughtExceptionHandler),
      autoCreateConfigDirectory = propFn("autoCreateConfigDirectory", autoCreateConfigDirectory),
      fileLogging = propFn("fileLogging", fileLogging),
      consoleLogging = propFn("consoleLogging", consoleLogging),
      hasColorConsole = propFn("hasColorConsole", hasColorConsole),
    )

  def asProperties(prefix: String): Map[String,String] =
    Map(
      prefix + "overrideSystemErr" -> overrideSystemErr.toString,
      prefix + "overrideSystemOut" -> overrideSystemOut.toString,
      prefix + "setDefaultUncaughtExceptionHandler" -> setDefaultUncaughtExceptionHandler.toString,
      prefix + "autoCreateConfigDirectory" -> autoCreateConfigDirectory.toString,
      prefix + "fileLogging" -> fileLogging.toString,
      prefix + "consoleLogging" -> consoleLogging.toString,
      prefix + "hasColorConsole" -> hasColorConsole.toString,
      prefix + "appName" -> appName,
      prefix + "configDirectory" -> configDirectory.getAbsolutePath,
      prefix + "logsDirectory" -> logsDirectory.getAbsolutePath,
      prefix + "archivesDirectory" -> archivesDirectory.getAbsolutePath,
      prefix + "defaultLogLevel" -> defaultLogLevel.name,
    )

}
