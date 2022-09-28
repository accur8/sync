package a8.shared.app

import wvlet.log.LogLevel
import zio.{&, ZIO}

// scala js
trait AppLoggerCompanionPlatform extends AppLoggerCompanionImpl {

  override def configure(initialLogLevels: Iterable[(String, LogLevel)]): ZIO[BootstrapConfig.AppName & BootstrapConfig, Throwable, Unit] =
    ???

}
