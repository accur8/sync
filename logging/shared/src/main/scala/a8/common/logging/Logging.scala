package a8.common.logging


import zio.Trace
import LoggingOps._

object Logging {

  def logger(implicit trace: Trace): Logger =
    LoggerFactory.logger(trace.wrap.scalaName)

  def logger(clazz: Class[?]): Logger =
    LoggerFactory.logger(normalizeClassname(clazz))

}

trait Logging {

  @transient implicit protected lazy val logger: Logger = Logging.logger(getClass)

}

trait PublicLogging {

  @transient implicit lazy val logger: Logger = Logging.logger(getClass)

}

