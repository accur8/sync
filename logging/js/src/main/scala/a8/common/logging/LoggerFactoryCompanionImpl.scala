package a8.common.logging

trait LoggerFactoryCompanionImpl extends LoggerFactoryCompanion {
  override protected def instantiateLoggerFactory(): LoggerFactory =
    JavascriptLoggingFactory
}
