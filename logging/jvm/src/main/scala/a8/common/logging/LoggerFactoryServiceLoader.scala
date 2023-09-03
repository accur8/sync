package a8.common.logging

import a8.common.logging.LoggerFactory
import a8.common.logging.println.PrintlnLoggerFactory

import java.util.ServiceLoader
import scala.jdk.CollectionConverters._

object LoggerFactoryServiceLoader {

  def loadLoggerFactory(): LoggerFactory = {
    ServiceLoader
      .load(classOf[LoggerFactoryServiceLoader])
      .iterator()
      .asScala
      .toList
      .sortBy(_.priority)
      .reverse
      .headOption
      .map(_.loggerFactory)
      .getOrElse(PrintlnLoggerFactory)
  }

}

trait LoggerFactoryServiceLoader {
  /**
   * highest numbered priority takes precedence
   */
  def priority: Int
  def loggerFactory: LoggerFactory

}
