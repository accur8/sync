package a8.shared.app

import cats.effect.Sync
import wvlet.log.{LazyLogger, LogEnv, Logger}

object Logging {

  /**
   * copied uncontrollably from wvlet.log.LogEnv since it is private
   */
  def loggerName(cl: Class[_]): String = {
    var name = cl.getName

    if (name.endsWith("$")) {
      // Remove trailing $ of Scala Object name
      name = name.substring(0, name.length - 1)
    }

    // When class is an anonymous trait
    if (name.contains("$anon$")) {
      val interfaces = cl.getInterfaces
      if (interfaces != null && interfaces.length > 0) {
        // Use the first interface name instead of the anonymous name
        name = interfaces(0).getName
      }
    }
    name
  }

}

trait Logging {

  implicit lazy val logger: Logger = Logger(Logging.loggerName(this.getClass))

}
