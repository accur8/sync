package a8.shared.app


import a8.shared.FileSystem.File
import a8.shared.SharedImports._
import a8.shared.app.BootstrapConfig.AppName
import wvlet.log.{LogLevel, LogRotationHandler, Logger}
import zio.{Chunk, Duration, Scope, Task, ZIO, ZLayer}
import zio.stream.ZStream

import java.io.IOException
import java.nio.file.{Path, StandardWatchEventKinds}

class JvmAppLoggerCompanionPlatform extends AppLoggerCompanionImpl with LoggingF {


  override def configure(initialLogLevels: Iterable[(String, wvlet.log.LogLevel)]): ZIO[AppName & BootstrapConfig, Throwable, Unit] =
    configure0(initialLogLevels)
      .asZIO(
        scheduleLogLevelScan
          .forkDaemon
          .as(())
      )


  private def configure0(initialLogLevels: Iterable[(String, wvlet.log.LogLevel)]): ZIO[BootstrapConfig, Throwable, Unit] =
    for {
      bootstrapConfig <- ZIO.service[BootstrapConfig]
    } yield {

      def configureLogLevels(): Unit = {

        if ( bootstrapConfig.defaultLogLevel == LogLevel.TRACE )
          Logger.setDefaultLogLevel(LogLevel.ALL)
        else
          Logger.setDefaultLogLevel(bootstrapConfig.defaultLogLevel)


        initialLogLevels
          .foreach { case (name, level) =>
            LoggerF.impl.setLogLevel(name, level)
          }

      }

      if (bootstrapConfig.colorConsole) {
        Logger.setDefaultFormatter(A8LogFormatter.ColorConsole)
      } else {
        Logger.setDefaultFormatter(A8LogFormatter.MonochromeConsole)
      }

      configureLogLevels()

      def enableRollingFileLogging(suffix: String, logLevel: java.util.logging.Level, maxNumberOfFiles: Int = 30, maxSizeInBytes: Long = 104857600 /*100 MB*/): Unit = {
        val logFile = bootstrapConfig.logsDir.resolved.file(bootstrapConfig.appName.value.toLowerCase + "-" + suffix)
        val handler =
          new LogRotationHandler(
            fileName = logFile.toString,
            maxNumberOfFiles = maxNumberOfFiles,
            maxSizeInBytes = maxSizeInBytes,
            formatter = A8LogFormatter.File
          )
        handler.setLevel(logLevel)
        Logger.rootLogger.addHandler(handler)
      }

      if (!bootstrapConfig.consoleLogging) {
        Logger.rootLogger.clearAllHandlers
      }

      if (bootstrapConfig.fileLogging) {
        enableRollingFileLogging("details.log", bootstrapConfig.defaultLogLevel.jlLevel)
        enableRollingFileLogging("errors.log", LogLevel.WARN.jlLevel)
      }

      logger.info("logging initialized")

    }

  def watchFiles(files: Vector[File], pollDelay: Duration): XStream[Unit] = {

    type State = Option[Vector[(File, Option[Long])]]

    def currentState: Task[State] = {
      ZIO.attemptBlocking {
        files
          .map { file =>
            file -> file.exists().toOption(file.lastModified())
          }
          .some
      }
    }

    def poll(previousState: State): XStream[Unit] = {
      val pollEffect =
        for {
          _ <- ZIO.sleep(pollDelay)
          newState <- currentState
        } yield newState
      pollEffect
        .zstreamEval
        .flatMap { newState =>
          val head = {
            (previousState, newState) match {
              case (None, _) =>
                ZStream.empty
              case _ if newState == previousState =>
                ZStream.empty
              case _ =>
                ZStream.succeed(())
              }
          }
          head ++ poll(newState)
        }
    }

    poll(None)

  }

  def scheduleLogLevelScan: ZIO[AppName, Nothing, Unit] = {
    val stream =
      for {
        bootstrapper <- ZStream.service[Bootstrapper]
        _ <- watchFiles(bootstrapper.configFiles.toVector.map(p => a8.shared.FileSystem.file(p.toFile.getCanonicalPath)), Duration.fromScala(15.seconds))
        _ <- reloadLogLevelConfig.zstreamExec
      } yield ()

    stream
      .runDrain
      .provideSome[AppName](
        ZLayer.succeed(zio.ZIOAppArgs(Chunk.empty)) >>> Bootstrapper.layer,
      )
      .logVoid

  }


  /**
   * will load a fresh bootstrapper and apply the log levels in bootstrapper.bootstrapConfig.logLevels
   */
  def reloadLogLevelConfig: ZIO[Bootstrapper, Nothing, Unit] = {
    val effect =
      zservice[Bootstrapper]
        .map(_.bootstrapConfig)
        .tap(bc =>
          if (bc.invalidLogLevels.isEmpty) {
            loggerF.info(s"reloading log levels -- \n${bc.logLevels.mkString("\n")}")
          } else
            loggerF.info(s"reloading log levels (with config errors) -- \n${bc.logLevels.mkString("\n")}")
              .asZIO(loggerF.warn(s"invalid log levels -- \n${bc.invalidLogLevels.mkString("\n")}"))
        )
        .flatMap { bootstrapConfig =>
          ZIO.attemptBlocking {
            bootstrapConfig.resolvedLogLevels.foreach { case (name, level) =>
              LoggerF.impl.setLogLevel(name, level)
            }
          }
        }
    effect
      .logVoid
  }

}
