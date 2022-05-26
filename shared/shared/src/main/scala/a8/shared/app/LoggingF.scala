package a8.shared.app


trait LoggingF extends Logging {

  implicit lazy val loggerF: LoggerF = LoggerF.wrap(logger)

}
