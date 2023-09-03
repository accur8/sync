package a8.common.logging

import a8.common.logging.LoggerFactoryServiceLoader

trait LoggerFactoryCompanionImpl {
  protected def instantiateLoggerFactory(): LoggerFactory = {
    val lf = LoggerFactoryServiceLoader.loadLoggerFactory()
    LoggingSetupOps.doOneTimeSetup(lf)
    lf
  }
}
