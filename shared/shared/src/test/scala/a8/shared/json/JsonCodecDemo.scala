package a8.shared.json

import a8.shared.app.BootstrappedIOApp
import a8.shared.app.BootstrappedIOApp.BootstrapEnv
import zio.ZIO
import a8.shared.SharedImports._

object JsonCodecDemo extends BootstrappedIOApp {

  override def runT: ZIO[BootstrapEnv, Throwable, Unit] = zunit

}
