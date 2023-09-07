package a8.common.logging


import scala.jdk.CollectionConverters._
import a8.common.logging.{LoggerFactory, LoggingBootstrapConfig}
import java.util.ServiceLoader

object LoggingBootstrapConfigServiceLoader {

  lazy val loggingBootstrapConfig = loadLoggingBootstrapConfig()

  private def loadLoggingBootstrapConfig(): LoggingBootstrapConfig = {
    ServiceLoader
      .load(classOf[LoggingBootstrapConfigServiceLoader])
      .iterator()
      .asScala
      .toList
      .sortBy(_.priority)
      .reverse
      .headOption
      .map(_.loggingBootstrapConfig)
      .getOrElse(sys.error("no a8.common.logging.LoggingBootstrapConfigServiceLoader found"))
  }


}

class LoggingBootstrapConfigServiceLoader {

  /**
   * highest numbered priority takes precedence
   */
  def priority: Int = 0

  def loggingBootstrapConfig: LoggingBootstrapConfig =
    LoggingBootstrapConfig.finalizedConfig

}
