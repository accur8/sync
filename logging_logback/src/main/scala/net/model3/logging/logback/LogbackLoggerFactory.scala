package net.model3.logging.logback

import a8.common.logging.{Level, Logger, LoggerFactory, LoggingBootstrapConfig}
import ch.qos.logback.classic.LoggerContext
import org.slf4j.MDC
import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.core.status.{Status, StatusListener}
import org.slf4j.event.EventConstants

import scala.collection.concurrent.TrieMap
import scala.concurrent.Promise

object LogbackLoggerFactory extends LoggerFactory {

  type LogbackLevel = ch.qos.logback.classic.Level

  val nestedContextThreadLocal = new ThreadLocal[Vector[String]]

  val loggerContext: LoggerContext = org.slf4j.LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

  private lazy val loggingConfiguredPromise = Promise[Unit]()

  lazy val loggingConfiguredFuture = loggingConfiguredPromise.future

  private final lazy val m3ToLogbackLevelMap: Map[Level, LogbackLevel] =
    Map(
      Level.All -> LogbackLevel.ALL,
      Level.Debug -> LogbackLevel.DEBUG,
      Level.Error -> LogbackLevel.ERROR,
      Level.Fatal -> LogbackLevel.ERROR,
      Level.Info -> LogbackLevel.INFO,
      Level.Off -> LogbackLevel.OFF,
      Level.Trace -> LogbackLevel.TRACE,
      Level.Warn -> LogbackLevel.WARN
    )

  final lazy val m3ToLogbackLevelIntMap: Map[Level, Int] =
    Map(
      Level.Debug -> EventConstants.DEBUG_INT,
      Level.Error -> EventConstants.ERROR_INT,
      Level.Fatal -> EventConstants.ERROR_INT,
      Level.Info -> EventConstants.INFO_INT,
      Level.Trace -> EventConstants.TRACE_INT,
      Level.Warn -> EventConstants.WARN_INT,
    )

  private final lazy val logbackToM3LevelMap =
    m3ToLogbackLevelMap.map(t => t._2 -> t._1)

  final val loggerMap = TrieMap.empty[String,Logger]

  override def logger(category: String): Logger = {
    loggerMap.getOrElseUpdate(category, createLoggerImpl(category))
  }

  protected def createLoggerImpl(category: String): Logger = {
    val delegate = loggerContext.getLogger(category)
    LogbackLogger(this, delegate)
  }

  def model3Level(level: LogbackLevel): Level =
    logbackToM3LevelMap(level)

  def logbackLevel(level: Level): LogbackLevel =
    m3ToLogbackLevelMap(level)

  /**
   * simulating NDC behaviour in logback
   *
   * @param ndcValue
   * @param fn
   * @tparam A
   * @return
   */
  override def withContext[A](ndcValue: String)(fn: => A): A = {

    val mdcKey = "ndc"

    val savedMdc = Option(MDC.get(mdcKey))

    val newNdc =
      savedMdc match {
        case None =>
          ndcValue
        case Some(s) =>
          s + " " + ndcValue
      }

    try {
      MDC.put(mdcKey, newNdc)
      fn
    } finally {
      savedMdc match {
        case None =>
          MDC.remove(mdcKey)
        case Some(v) =>
          MDC.put(mdcKey, v)
      }
    }
  }

  override def postConfig(): Unit = {

    val (level, statusStr) = LogbackConfigurator.statusMessages()
    val indentedStatusStr = statusStr.linesIterator.map("        " + _).mkString("\n")
    Logger.logger(getClass).log(level, s"logging config results\n${indentedStatusStr}")

    loggingConfiguredPromise.success(()): @scala.annotation.nowarn

    loggerContext.getStatusManager.add(
      new StatusListener {
        lazy val logger = LogbackLoggerFactory.logger("net.model3.logging.logback.LogbackLoggerFactory")
        override def addStatusEvent(status: Status): Unit = {
          val t = LogbackConfigurator.statusMessage(status)
          logger.log(t._1, t._2.trim)
        }
        override def isResetResistant: Boolean = true
      }
    )
  }

}

