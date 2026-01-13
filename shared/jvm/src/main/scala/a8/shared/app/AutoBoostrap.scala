package a8.shared.app

import a8.common.logging.{Logger, LoggerFactory, LoggingBootstrapConfig}
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
//  logger: Logger,
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

    val logger = LoggerFactory.logger(getClass.getName)
    logger.info(s"bootstrap config from ${ConfigMojo.rootSources.mkString("  ")}")

    configureLogLevels

    app.appInit

    // Add shutdown hook to handle external stop signals
    val shutdownLatch = new java.util.concurrent.CountDownLatch(1)
    @volatile var appCtxOpt: Option[AppCtx] = None

    val shutdownHook = new Thread(() => {
      logger.info("Received shutdown signal from supervisor")
      appCtxOpt.foreach { ctx =>
        logger.info("Cancelling application context...")
        ctx.cancel()
      }
      shutdownLatch.countDown()
    }, "shutdown-hook")

    Runtime.getRuntime.addShutdownHook(shutdownHook)

    try {
      supervised {
        given AppCtx =
          AppCtx(
            bootstrapper = bootstrapper,
            ox0 = summon[ox.Ox],
          )
        appCtxOpt = Some(summon[AppCtx])

        try {
          app.run()
        } catch {
          case th: Throwable =>
            logger.error(s"Application run() failed with error", th)
            throw th
        }
      }
    } catch {
      case th: Throwable =>
        logger.error(s"Supervised block failed", th)
        throw th
    } finally {
      try {
        Runtime.getRuntime.removeShutdownHook(shutdownHook)
      } catch {
        case _: IllegalStateException =>
          // Shutdown already in progress, ignore
      }
    }

  }

}
