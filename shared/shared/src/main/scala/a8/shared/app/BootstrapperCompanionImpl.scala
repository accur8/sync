package a8.shared.app


import a8.shared.app.BootstrapConfig.AppName
import zio.{Task, ZIO, ZIOAppArgs, ZLayer}

trait BootstrapperCompanionImpl {
  val layer: ZLayer[AppName & BootstrappedIOApp.DefaultLogLevel & ZIOAppArgs, Throwable, Bootstrapper]
}
