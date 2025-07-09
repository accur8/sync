package a8.shared.app

import a8.common.logging.{Logger, LoggingBootstrapConfig}
import a8.shared.ConfigMojo
import ch.qos.logback.classic.LoggerContext
import net.model3.logging.logback.LogbackConfigurator
import ox.supervised

/**
 * takes care of various dependency ordering with lazy loading
 */
case class AutoBoostrap(
  bootstrapper: Bootstrapper,
  app: BootstrappedIOApp,
  logger: Logger,
) {

  private lazy val loggingBootstrapConfig = bootstrapper.bootstrapConfig.loggingBootstrapConfig

  private lazy val finalizeLoggingConfig = LoggingBootstrapConfig.finalizeConfig(loggingBootstrapConfig)
  private lazy val configureLogging = LogbackConfigurator.configureLogging(loggingBootstrapConfig, logbackLoggerContext)

  private lazy val logbackLoggerContext =
    org.slf4j.LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

  private lazy val configureLogLevels: Unit =
    app
      .initialLogLevels
      .foreach(ll => a8.common.logging.LoggerFactory.logger(ll._1).setLevel(ll._2))

  def runBootstrap(): Unit = {

    given Bootstrapper = bootstrapper

    loggingBootstrapConfig
    finalizeLoggingConfig
    configureLogging

    logger.info(s"bootstrap config from ${ConfigMojo.rootSources.mkString("  ")}")

    configureLogLevels

    app.appInit

    supervised {
      given AppCtx =
        AppCtx(
          bootstrapper = bootstrapper,
          ox0 = summon[ox.Ox],
        )
      app.run()
    }


    //    !!! ???
    //    val rawEffect: ZIO[BootstrapEnv & LoggingBootstrapConfig & LoggerContext, Throwable, Unit] =
    //      for {
    //        bootstrapper <- zservice[Bootstrapper]
    //        loggingBootstrapConfig <- zservice[LoggingBootstrapConfig]
    //        _ <- zblock(LoggingBootstrapConfig.finalizeConfig(loggingBootstrapConfig))
    //        _ <- LogbackConfigurator.configureLoggingZ
    //        _ <- loggerF.info(s"bootstrap config from ${ConfigMojo.rootSources.mkString("  ")}")
    //        _ <- configureLogLevels(initialLogLevels)
    //        _ <- appInit
    //        _ <- ZIO.scoped(runT)
    //      } yield ()
    //
    //    val effectWithErrorLogging =
    //      rawEffect
    //        .onExit {
    //          case zio.Exit.Success(_) =>
    //            loggerF.info("natural shutdown")
    //          case zio.Exit.Failure(cause) if cause.isInterruptedOnly =>
    //            loggerF.info(s"shutdown because of interruption only", cause)
    //          case zio.Exit.Failure(cause) =>
    //            loggerF.warn(s"shutdown because of failure/interruption", cause)
    //        }
    //
    //    provideLayers(effectWithErrorLogging)

  }

}
