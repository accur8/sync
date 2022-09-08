package a8.shared.app


import a8.shared.SharedImports._
import a8.shared.app.BootstrapConfig.{AppName, CacheDir, DataDir, LogsDir, TempDir, WorkDir}
import a8.shared.app.BootstrappedIOApp.BootstrapEnv
import a8.shared.json.JsonCodec
import wvlet.log.LogLevel
import zio.{Scope, Tag, ZIO, ZIOAppArgs, ZLayer}

object BootstrappedIOApp {

  type BootstrapEnv = Scope with ZIOAppArgs with Bootstrapper with TempDir with CacheDir with DataDir with BootstrapConfig with AppName with LogsDir with WorkDir

}


abstract class BootstrappedIOApp
  extends zio.ZIOAppDefault
    with LoggingF
{

  def initialLogLevels: Iterable[(String,wvlet.log.LogLevel)] = {
    val debugs =
      List(
        "io.undertow",
      ) map { n =>
        n -> LogLevel.DEBUG
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
      ) map { n =>
        n -> LogLevel.INFO
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

          if (bootstrapper.bootstrapConfig.logAppConfig) {
            logger.debug(s"using config ${bootstrapper.rootConfig.prettyJson}")
          }

          def loadDriver(className: String): Unit = {
            try {
              Class.forName(className)
                .getConstructor()
                .newInstance()
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
    lazy val appName = ZLayer.succeed(resolvedAppName)
    lazy val bootstrapConfig = Bootstrapper.layer.project(_.bootstrapConfig)

    lazy val tempDir = bootstrapConfig.project(_.tempDir)
    lazy val cacheDir = bootstrapConfig.project(_.cacheDir)
    lazy val logsDir = bootstrapConfig.project(_.logsDir)
    lazy val dataDir = bootstrapConfig.project(_.dataDir)
    lazy val appArgs = bootstrapConfig.project(_.appArgs)

    lazy val workDir = WorkDir.layer

  }

  def appConfig[A : JsonCodec]: zio.ZIO[Bootstrapper, Throwable, A] =
    for {
      bootstrapper <- zservice[Bootstrapper]
      config <- bootstrapper.appConfig[A]
    } yield config

  def appConfigLayer[A: Tag: JsonCodec] = ZLayer(appConfig[A])

  def runT: zio.ZIO[BootstrapEnv, Throwable, Unit]


  final override def run: zio.ZIO[Any with zio.ZIOAppArgs with zio.Scope, Any, Any] =
    zservice[ZIOAppArgs]
      .zip(zservice[Scope])
      .flatMap { case (appArgs, scope) =>

        val effect =
          for {
            _ <- AppLogger.configure(initialLogLevels)
            _ <- appInit
            _ <- ZIO.scoped(runT)
          } yield ()

        val loggingLayer = SyncZLogger.slf4jLayer(zio.LogLevel.Debug)

        effect
          .onExit {
            case zio.Exit.Success(_) =>
              loggerF.info("natural shutdown")
            case zio.Exit.Failure(cause) if cause.isInterruptedOnly =>
              loggerF.info(s"shutdown because of interruption only", cause)
            case zio.Exit.Failure(cause) =>
              loggerF.warn(s"shutdown because of failure/interruption", cause)
          }
          .provide(
            Bootstrapper.layer,
            ZLayer.succeed(scope),
            ZLayer.succeed(appArgs),
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


}
