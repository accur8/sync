package playground


import a8.shared.app.BootstrappedIOApp
import a8.shared.app.BootstrappedIOApp.BootstrapEnv
import zio.ZIO

object BootstrappedIOAppDemo extends BootstrappedIOApp {

  override def runT: ZIO[BootstrapEnv, Throwable, Unit] =
    loggerF.info("boom")

}
