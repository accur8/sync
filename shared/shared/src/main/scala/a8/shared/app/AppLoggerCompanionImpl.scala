package a8.shared.app

import a8.shared.app.BootstrapConfig.AppName
import zio.ZIO

trait AppLoggerCompanionImpl {
  def configure(initialLogLevels: Iterable[(String, wvlet.log.LogLevel)]): ZIO[AppName & BootstrappedIOApp.DefaultLogLevel & BootstrapConfig, Throwable, Unit]
}
