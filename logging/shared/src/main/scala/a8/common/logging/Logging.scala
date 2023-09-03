package a8.common.logging


object Logging {

  def logger(implicit name: sourcecode.FullName): Logger =
    LoggerFactory.logger(name.value)

  def logger(clazz: Class[_]): Logger =
    LoggerFactory.logger(normalizeClassname(clazz))

}

trait Logging {

  @transient implicit protected lazy val logger: Logger = Logging.logger(getClass)

}

trait PublicLogging {

  @transient implicit lazy val logger: Logger = Logging.logger(getClass)

}
