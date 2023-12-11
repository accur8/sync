package a8.shared.app


import a8.shared.SharedImports.*
import a8.shared.app.BootstrapConfig.{AppName, CacheDir, DataDir, LogsDir, TempDir, WorkDir}
import a8.shared.app.BootstrappedIOApp.BootstrapEnv
import a8.shared.json.JsonCodec
import zio.{Scope, Tag, UIO, ZIO, ZIOAppArgs, ZLayer}
import a8.shared.SharedImports.*
import a8.shared.json.ZJsonReader.ZJsonReaderOptions
import zio.ULayer
import a8.common.logging.{Level, LoggerFactory, LoggingBootstrapConfig, SyncZLogger}
import a8.shared.ConfigMojo
import ch.qos.logback.classic.LoggerContext
import net.model3.logging.logback.LogbackConfigurator

object BootstrappedIOApp {

  type BootstrapEnv = Scope with ZIOAppArgs with Bootstrapper with TempDir with CacheDir with DataDir with BootstrapConfig with AppName with LogsDir with WorkDir

}


abstract class BootstrappedIOApp
  extends zio.ZIOAppDefault
    with LoggingF
{

  val loggingLayer: ZLayer[Any, Nothing, Unit] = SyncZLogger.slf4jLayer(zioMinLevel)

  def loggingBootstrapConfigLayer: ZLayer[BootstrapConfig, Throwable, LoggingBootstrapConfig] =
    ZLayer.fromZIO(
      for {
        bootstrapConfig <- zservice[BootstrapConfig]
      } yield {
        bootstrapConfig
          .resolvedDto
          .logging
          .asLoggingBootstrapConfig(
            appName = bootstrapConfig.appName.value,
            configDirectory = new java.io.File(bootstrapConfig.configDir.unresolved.canonicalPath),
            logsDirectory = new java.io.File(bootstrapConfig.logsDir.unresolved.canonicalPath),
          )
      }
    )

  def initialLogLevels: Iterable[(String,Level)] = {
    val debugs =
      List(
        "io.undertow",
      ) map { n =>
        n -> Level.Debug
      }

    val infos =
      List(
        "javax.xml",
        "org.apache.parquet",
        "org.apache.hadoop",
        "a8.wsjdbc.client",
        "sun",
        "jdk",
        "com.sun.xml.bind",
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
        "net.snowflake.client",
        "com.linecorp.armeria",
      ) map { n =>
        n -> Level.Info
      }

    debugs ++ infos

  }

  lazy val defaultAppName: String =
    getClass.shortName.toLowerCase

  lazy val resolvedAppName: AppName =
    AppName(java.lang.System.getProperty("appname", defaultAppName))

  def appInit: zio.ZIO[Bootstrapper, Throwable, Unit] =
    for {
      bootstrapper <- zservice[Bootstrapper]
      _ <-
        zio.ZIO.attempt {

          bootstrapper.logs.foreach(m => logger.debug(m))

          logger.debug(s"config prefix is ${bootstrapper.bootstrapConfig.appName.value}")
          logger.debug(s"config files used ${bootstrapper.configFiles.map(_.toRealPath()).mkString(" ")}")
          logger.debug(s"directories searched ${bootstrapper.directoriesSearched.mkString(" ")}")
          logger.debug(s"bootstrap config is ${bootstrapper.bootstrapConfig}")

          def loadDriver(className: String): Unit = {
            try {
              Class.forName(className)
                .getConstructor()
                .newInstance(): @scala.annotation.nowarn
              logger.debug(s"loaded jdbc driver ${className}")
            } catch {
              case th: Throwable =>
            }
          }

          loadDriver("org.postgresql.Driver")
          loadDriver("com.ibm.as400.access.AS400JDBCDriver")
          loadDriver("a8.wsjdbc.Driver")

        }
    } yield ()


  object layers {

    type ConfigInitArgs = AppName & ZIOAppArgs

    lazy val appName: ULayer[AppName] = ZLayer.succeed(resolvedAppName)
    lazy val bootstrapConfig: ZLayer[ConfigInitArgs, Throwable, BootstrapConfig] = Bootstrapper.layer.project(_.bootstrapConfig)

    lazy val tempDir: ZLayer[ConfigInitArgs, Throwable, TempDir] = bootstrapConfig.project(_.tempDir)
    lazy val cacheDir: ZLayer[ConfigInitArgs, Throwable, CacheDir] = bootstrapConfig.project(_.cacheDir)
    lazy val logsDir: ZLayer[ConfigInitArgs, Throwable, LogsDir] = bootstrapConfig.project(_.logsDir)
    lazy val dataDir: ZLayer[ConfigInitArgs, Throwable, DataDir] = bootstrapConfig.project(_.dataDir)
    lazy val appArgs: ZLayer[ConfigInitArgs, Throwable, ZIOAppArgs] = bootstrapConfig.project(_.appArgs)

    lazy val workDir = WorkDir.layer

  }

  def appConfig[A : JsonCodec](implicit jsonReaderOptions: ZJsonReaderOptions): zio.ZIO[Bootstrapper, Throwable, A] =
    for {
      bootstrapper <- zservice[Bootstrapper]
      config <- bootstrapper.appConfig[A]
    } yield config

  def appConfigLayer[A: Tag: JsonCodec](implicit jsonReaderOptions: ZJsonReaderOptions): ZLayer[Bootstrapper,Throwable,A] = ZLayer(appConfig[A])

  def runT: zio.ZIO[BootstrapEnv, Throwable, Unit]

  def configureLogLevels(initialLogLevels: Iterable[(String, Level)]): zio.Task[Unit] =
    zblock(
      initialLogLevels
        .foreach(ll => a8.common.logging.LoggerFactory.logger(ll._1).setLevel(ll._2))
    )

  def zioMinLevel = Level.Debug

  final override def run: zio.ZIO[Any with zio.ZIOAppArgs with zio.Scope, Any, Any] = {

    val rawEffect: ZIO[BootstrapEnv with LoggingBootstrapConfig & LoggerContext, Throwable, Unit] =
      for {
        bootstrapper <- zservice[Bootstrapper]
        loggingBootstrapConfig <- zservice[LoggingBootstrapConfig]
        _ <- zblock(LoggingBootstrapConfig.finalizeConfig(loggingBootstrapConfig))
        _ <- LogbackConfigurator.configureLoggingZ
        _ <- loggerF.info(s"bootstrap config from ${ConfigMojo.rootSources.mkString("  ")}")
        _ <- configureLogLevels(initialLogLevels)
        _ <- appInit
        _ <- ZIO.scoped(runT)
      } yield ()

    val effectWithErrorLogging =
      rawEffect
        .onExit {
          case zio.Exit.Success(_) =>
            loggerF.info("natural shutdown")
          case zio.Exit.Failure(cause) if cause.isInterruptedOnly =>
            loggerF.info(s"shutdown because of interruption only", cause)
          case zio.Exit.Failure(cause) =>
            loggerF.warn(s"shutdown because of failure/interruption", cause)
        }

    provideLayers(effectWithErrorLogging)

  }

  def provideLayers(effect: ZIO[BootstrapEnv & LoggingBootstrapConfig & LoggerContext, Throwable, Unit]): zio.ZIO[Any with zio.ZIOAppArgs with zio.Scope, Any, Any] =
    effect
      .provideSome[zio.Scope & zio.ZIOAppArgs](
        ZLayer.succeed(org.slf4j.LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]),
        Bootstrapper.layer,
        loggingBootstrapConfigLayer,
        layers.appName,
        layers.logsDir,
        layers.tempDir,
        layers.dataDir,
        layers.cacheDir,
        layers.workDir,
        layers.bootstrapConfig,
        loggingLayer,
        zio.logging.removeDefaultLoggers,
      )


}
