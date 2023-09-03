package a8.common.logging

trait LoggingF extends Logging {

  implicit lazy val loggerF: LoggerF = LoggerF.wrap(logger)

}
