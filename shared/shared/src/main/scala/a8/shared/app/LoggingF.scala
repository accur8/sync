package a8.shared.app


abstract class LoggingF extends Logging {

  implicit lazy val loggerF = LoggerF.wrap(logger)

}
