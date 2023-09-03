package a8.common.logging


private[logging] trait LoggerFactoryCompanion {

  protected def instantiateLoggerFactory(): LoggerFactory

}
