package a8.shared.app


import a8.shared.SharedImports._
import a8.shared.app.BootstrapConfig.{AppName, WorkDir}
import a8.shared.app.BootstrappedIOApp.BootstrapEnv
import zio._

object BootstrappedIOAppDemo extends BootstrappedIOApp {

//  lazy val loggerIO2 = LoggerF.create

//  override def runT: Task[Unit] = (
//    loggerF.info("hello world from loggerIO")
//      *> loggerIO2.info("hello from loggerIO2")
//  )


  override def runT =
    for {
      appName <- zservice[AppName]
      workDir <- zservice[WorkDir]
      appArgs <- zservice[ZIOAppArgs]
      _ <- ZIO.debug(s"appArgs: $appArgs")
      _ <- ZIO.debug(s"appName: $appName")
      _ <- ZIO.debug(s"workDir: $workDir")
    } yield ()


}
