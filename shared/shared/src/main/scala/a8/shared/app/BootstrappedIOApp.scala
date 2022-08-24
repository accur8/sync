package a8.shared.app


import a8.shared.SharedImports._
import a8.shared.app.BootstrapConfig.AppName
import wvlet.log.LogLevel
import zio._

abstract class BootstrappedIOApp
  extends ZIOAppDefault
    with AppLogger
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
      ) map { n =>
        n -> LogLevel.INFO
      }

    debugs ++ infos

  }

  lazy val defaultAppName: String =
    getClass.shortName.toLowerCase

  lazy val resolvedAppName: AppName =
    AppName(java.lang.System.getProperty("appname", defaultAppName))

  lazy val bootstrapper = Bootstrapper(resolvedAppName)
  lazy val bootstrapConfig = bootstrapper.bootstrapConfig

  def appInit: Task[Unit] = ZIO.attempt {

    // make sure bootstrap is complete
    bootstrapInit

    bootstrapper.logs.foreach(m => logger.debug(m))

    logger.debug(s"config prefix is ${bootstrapper.bootstrapConfig.appName.value}")
    logger.debug(s"config files used ${bootstrapper.configFiles.map(_.toRealPath()).mkString(" ")}")
    logger.debug(s"directories searched ${bootstrapper.directoriesSearched.mkString(" ")}")
    logger.debug(s"bootstrap config is ${bootstrapper.bootstrapConfig}")

    if (bootstrapConfig.logAppConfig) {
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


  def runT: Task[Unit]


  final override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    (
      for {
        _ <- appInit
        _ <- runT
      } yield ()
    ).catchAll(th =>
      loggerF.error("fatal error ending app", th)
    )
  }


}
