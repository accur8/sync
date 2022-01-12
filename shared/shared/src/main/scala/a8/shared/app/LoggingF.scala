package a8.shared.app

import cats.effect.Sync
import wvlet.log.LazyLogger

abstract class LoggingF[F[_] : Sync] extends Logging {

  implicit lazy val loggerF = LoggerF.wrap[F](logger)

}
