package net.model3.logging.logback


import a8.common.logging.{LoggerFactory, LoggerFactoryServiceLoader}

class LogbackLoggerFactoryServiceLoader extends LoggerFactoryServiceLoader {

  /**
   * highest numbered priority takes precedence
   */
  override def priority: Int = 100

  override def loggerFactory: LoggerFactory =
    LogbackLoggerFactory
}
