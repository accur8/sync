package a8.shared.app

case class AutoBoostrap(
  bootstrapper: Bootstrapper,
  app: BootstrappedIOApp,
) {

  lazy val loggingBootstrapConfig = bootstrapper.bootstrapConfig.loggingBootstrapConfig

  lazy val finalizeLoggingConfig = LoggingBootstrapConfig.finalizeConfig(loggingBootstrapConfig)
  lazy val configureLogging = LogbackConfigurator.configureLogging(loggingBootstrapConfig, logbackLoggerContext)

  lazy val configureLogLevels: Unit =
    initialLogLevels
      .foreach(ll => a8.common.logging.LoggerFactory.logger(ll._1).setLevel(ll._2))

  def runBootstrap(): Unit = {

    loggingBootstrapConfig
    finalizeLoggingConfig
    configureLogging

    logger.info(s"bootstrap config from ${ConfigMojo.rootSources.mkString("  ")}")

    configureLogLevels

    app.run(appCtx)

  }

  lazy val appCtx =
    AppCtx(
      bootstrapper = bootstrapper,
    )

}
