package a8.shared.app


import a8.shared.SharedImports._
import a8.shared.app.BootstrapConfig.AppName
import cats.effect.{ExitCode, IO}
import wvlet.log.LogLevel

abstract class BootstrappedIOApp(defaultAppName: String = getClass.shortName.toLowerCase)
  extends IOApp
    with AppLogger
    with IOLogger
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
        "a8.wsjdbc.client",
        "sun",
        "jdk",
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

  lazy val resolvedAppName: AppName =
    AppName(System.getProperty("appname", defaultAppName))

  lazy val bootstrapper = Bootstrapper(resolvedAppName)
  lazy val bootstrapConfig = bootstrapper.bootstrapConfig

  lazy val appInit = {

    // make sure bootstrap is complete
    bootstrapInit

    bootstrapper.logs.foreach(m => logger.debug(m))

    logger.debug(s"config prefix is ${bootstrapper.bootstrapConfig.appName.value}")
    logger.debug(s"config files used ${bootstrapper.configFiles.mkString(" ")}")
    logger.debug(s"directories searched ${bootstrapper.directoriesSearched.mkString(" ")}")
    logger.debug(s"bootstrap config is ${bootstrapper.bootstrapConfig}")
    logger.debug(s"using config ${bootstrapper.rootConfig.prettyJson}")

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

  appInit

  def run: IO[Unit]
  def run(args: List[String]): IO[ExitCode] = run.as(ExitCode.Success)


}
