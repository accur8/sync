package a8.shared.app


import a8.shared.SharedImports._
import zio._

object BootstrappedIOAppDemo extends BootstrappedIOApp {

  lazy val loggerIO2 = LoggerF.create

  override def runT: Task[Unit] = (
    loggerF.info("hello world from loggerIO")
      *> loggerIO2.info("hello from loggerIO2")
  )

}
