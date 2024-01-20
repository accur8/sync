package net.model3.logging.logback

import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.spi.FilterReply
import org.slf4j.Marker

import java.util.regex.Pattern

class Log4jdbcTurboFilter extends TurboFilter {

  lazy val patterns =
    List(
      """.*select 1 from INFORMATION_SCHEMA.SYSTEM_USERS LIMIT 1.*""",
      """.*select 1\+1.*""",
      """.*select 1.*""",
      """.*Connection\.setNetworkTimeout.*""",
    ).map(Pattern.compile)

  override def decide(marker: Marker, logger: Logger, level: Level, format: String, params: Array[AnyRef], t: Throwable): FilterReply = {
    if ( format == null ) {
      return FilterReply.NEUTRAL
    } else {
      patterns.find(_.matcher(format).matches()).nonEmpty match {
        case true =>
          FilterReply.DENY
        case false =>
          FilterReply.NEUTRAL
      }
    }
  }

}
