package a8.shared.app


import a8.common.logging.{Logger, LoggerFactory}
import a8.shared.SharedImports.*
import a8.shared.app.BootstrapConfig.{AppName, WorkDir}
import a8.shared.app.BootstrappedIOApp.BootstrapEnv

import scala.concurrent.duration.DurationInt

object BootstrappedIOAppDemo extends BootstrappedIOApp with Logging {

//  lazy val loggerIO2 = LoggerF.create

//  override def runT: Task[Unit] = (
//    loggerF.info("hello world from loggerIO")
//      *> loggerIO2.info("hello from loggerIO2")
//  )

  lazy val weirdLoggerF = Logger.create(LoggerFactory.logger("weird"))

  override def runT: ZIO[BootstrapEnv,Throwable,Unit] =
    for {
//      appName <- zservice[AppName]
//      workDir <- zservice[WorkDir]
//      appArgs <- zservice[ZIOAppArgs]
//      _ <- loggerF.info(s"appArgs: $appArgs")
//      _ <- loggerF.info(s"appName: $appName")
//      _ <- loggerF.info(s"workDir: $workDir")
//      _ <- loggerF.warn("boom", new Throwable())
      _ <- weirdLoggerF.info("something weird")
      _ <-
        ZStream
          .fromIterable(1 to Integer.MAX_VALUE)
          .mapZIO { i =>
//            val trace = implicitly[zio.Trace]
//            logger.info(trace.toString)
            logger.info("direct logger call")
            loggerF.info(s"hello $i")
              .asZIO(ZIO.sleep(zio.Duration.fromSeconds(1)))
          }
          .runDrain
    } yield ()


}
