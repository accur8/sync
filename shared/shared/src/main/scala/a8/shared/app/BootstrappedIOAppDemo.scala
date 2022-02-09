package a8.shared.app

import cats.effect.IO

object BootstrappedIOAppDemo extends BootstrappedIOApp {

  lazy val loggerIO2 = LoggerF.create[IO]

  override def run: IO[Unit] = (
    loggerIO.info("hello world from loggerIO")
      >> loggerIO2.info("hello from loggerIO2")
  )

}
