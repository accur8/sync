package a8.common.logging


object LoggerFactory extends LoggerFactory with LoggerFactoryCompanion with LoggerFactoryCompanionImpl {

  private lazy val delegate: LoggerFactory =
    instantiateLoggerFactory()

  override def logger(name: String): Logger =
    delegate.logger(name)

  override def withContext[A](context: String)(fn: => A): A =
    delegate.withContext(context)(fn)

}

trait LoggerFactory {

  def logger(name: String): Logger
  def withContext[A](context: String)(fn: => A): A

}
