package a8.shared.app


import a8.common.logging.{Level, LoggingBootstrapConfig}
import a8.shared.ConfigMojo
import a8.shared.SharedImports.*
import a8.shared.app.BootstrapConfig.*
import a8.shared.json.JsonCodec
import a8.shared.json.JsonReader.JsonReaderOptions
import ch.qos.logback.classic.LoggerContext
import net.model3.logging.logback.LogbackConfigurator
import zio.*

object BootstrappedIOApp


abstract class BootstrappedIOApp
  extends Logging
{

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

  def appInit(using bootstrapper: Bootstrapper): Unit = {

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


  def appConfig[A : JsonCodec](bootstrapper: Bootstrapper)(using JsonReaderOptions): A =
    bootstrapper.appConfig[A]

  def run()(using AppCtx): Unit

  object uncaughtExceptionHandler extends Thread.UncaughtExceptionHandler {
    override def uncaughtException(t: Thread, e: Throwable): Unit = {
      e.printStackTrace(System.err)
      logger.error(s"Uncaught exception in thread ${t.getName}", e)
    }
  }

  def main(args: Array[String]): Unit = {

    Thread.currentThread().setUncaughtExceptionHandler(uncaughtExceptionHandler)

    try {
      val bootstrapper: Bootstrapper =
        BootstrapperCompanionPlatform
          .constructBootstrapper(resolvedAppName, CommandLineArgs(args))

      val autoBootstrap =
        AutoBoostrap(
          bootstrapper = bootstrapper,
          app = this,
          logger = logger,
        )

      autoBootstrap.runBootstrap()

    } catch {
      case th: Throwable =>
        logger.error(s"error during bootstrap", th)
        th.printStackTrace(System.err)
    }

  }

}
