package net.model3.logging.logback

import a8.common.logging.LoggingBootstrapConfig
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.jul.LevelChangePropagator
import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import ch.qos.logback.classic.layout.TTLLLayout
import ch.qos.logback.classic.spi.Configurator.ExecutionStatus
import ch.qos.logback.classic.spi.{Configurator, ILoggingEvent, LoggerContextListener}
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.encoder.LayoutWrappingEncoder
import ch.qos.logback.core.spi.ContextAwareBase
import ch.qos.logback.core.status.{InfoStatus, Status, StatusBase, StatusListener, StatusUtil}
import ch.qos.logback.core.util.StatusPrinter
import org.slf4j.bridge.SLF4JBridgeHandler

import java.io.{File, FileOutputStream}
import java.nio.file.Paths
import java.util
import scala.jdk.CollectionConverters._

object LogbackConfigurator {

  def statusMessages(): (a8.common.logging.Level,String) = {
    val status =
      org.slf4j.LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
        .getStatusManager
        .getCopyOfStatusList
        .asScala
    highestLevel(status) -> statusMessages(status)
  }

  def statusMessages(loggerContext: LoggerContext): String = {
    val sb = new java.lang.StringBuilder()
    loggerContext
      .getStatusManager
      .getCopyOfStatusList()
      .asScala
      .foreach(s => StatusPrinter.buildStr(sb, "", s))
    sb.toString
  }

  def statusMessages(status: Iterable[Status]): String = {
    val sb = new java.lang.StringBuilder()
    status
      .foreach(s => StatusPrinter.buildStr(sb, "", s))
    sb.toString
  }
  def statusMessage(status: Status): (a8.common.logging.Level,String) = {
    val sb = new java.lang.StringBuilder()
    StatusPrinter.buildStr(sb, "", status)
    highestLevel(status) -> sb.toString
  }

  def highestLevel(status: Status): a8.common.logging.Level =
    impl.statusLevel(impl.highestLevel(status))

  def highestLevel(status: Iterable[Status]): a8.common.logging.Level =
    impl.statusLevel(impl.highestLevel(status.iterator))

  object impl {

    def highestLevel(status: Status): Int = {
      val iter = status.iterator()
      if (iter != null) {
        Math.max(status.getLevel, highestLevel(iter.asScala))
      } else {
        status.getLevel
      }
    }
    def statusLevel(statusLevelInt: Int): a8.common.logging.Level = {
      statusLevelInt match {
        case 0 =>
          a8.common.logging.Level.Info
        case 1 =>
          a8.common.logging.Level.Warn
        case 2 =>
          a8.common.logging.Level.Error
        case _ =>
          a8.common.logging.Level.Fatal
      }
    }

    def highestLevel(status: Iterator[Status]): Int = {
      status
        .foldLeft(0) { (level, status) =>
          Math.max(level, highestLevel(status))
        }
    }
  }

}

class LogbackConfigurator extends ContextAwareBase with Configurator { outer =>

  lazy val configDirectory: java.io.File =
    new File(System.getProperty("config.dir"), "./config")

  lazy val logsDirectory: java.io.File =
    new File(System.getProperty("logs.dir"), "./logs")

  lazy val archivesDirectory: java.io.File =
    new File(System.getProperty("archives.dir"), new File(logsDirectory, "archives").getAbsolutePath)

  // ??? setup logback-default.xml
  // ??? setup logback-sample.xml
      // ??? uses bootstrap.fileLoging and bootstrap.consoleLoging

  override def configure(loggerContext: LoggerContext): Configurator.ExecutionStatus = {

    val bootstrapConfig = LoggingBootstrapConfig.globalBootstrapConfig

    addInfo(s"""using bootstrapConfig ${bootstrapConfig.asProperties("").mkString("  ")}""")

    val joranConfigurator = new JoranConfigurator
    joranConfigurator.setContext(loggerContext)

    val bootstrapConfigProperties = bootstrapConfig.asProperties("bootstrap.")

    bootstrapConfigProperties
      .foreach(t => System.setProperty(t._1, t._2))

    // propagate logging level changes to java.util.logging
    loggerContext.addListener(new LevelChangePropagator() {
      setContext(loggerContext)
      override def isResetResistant: Boolean = true
    })

    val configFile = new File(configDirectory, "logback.xml")

    if ( configFile.exists() ) {
      addInfo(s"configuring logging using ${configFile.getAbsolutePath}")
      joranConfigurator.doConfigure(configFile)
    } else {
      val input = getClass.getResourceAsStream("/logback-default.xml")
      joranConfigurator.doConfigure(input)
      input.close()
      addInfo(s"logging configured using default file from classpath /logback-default.xml")
    }

    if ( configDirectory.exists() ) {
      configDirectory.mkdirs(): @scala.annotation.nowarn
      val sampleFile = configDirectory.toPath.resolve("logback-sample.xml").toFile
      val input = getClass.getResourceAsStream("/logback-sample.xml")
      val output = new FileOutputStream(sampleFile)
      output.write(input.readAllBytes())
      output.close()
    }

    // dummy status listener to avoid logging logback status messages to console
    context.getStatusManager.add(
      new StatusListener:
        override def addStatusEvent(status: Status): Unit = ()
    )

//    to test error logging
//    addError("boom")

    SLF4JBridgeHandler.removeHandlersForRootLogger // (since SLF4J 1.6.5)

    // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
    // the initialization phase of your application
    SLF4JBridgeHandler.install

    ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;

  }

}
