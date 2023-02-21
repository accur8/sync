package a8.shared.app

import a8.shared.FileSystem.Directory
import a8.shared.{CompanionGen, FileSystem, NamedToString, StringValue}
import a8.shared.app.BootstrapConfig._
import a8.shared.app.MxBootstrapConfig._

import java.nio.file.{Path, Paths}
import a8.shared.SharedImports._
import wvlet.log.LogLevel
import zio.{Duration, Scope, ZIO, ZLayer}

import scala.concurrent.duration.FiniteDuration


/*
 *  + app will have a default AppName which will be it's default prefix
 *  + can override the default AppName with the appname System property
 */
object BootstrapConfig {

  object LogLevelConfig extends MxLogLevelConfig {
  }
  @CompanionGen
  case class LogLevelConfig(
    name: String,
    level: String,
  ) {
    lazy val resolvedLevel: Option[LogLevel] =
      LogLevel
        .values
        .find(_.name =:= level)
  }

  object BootstrapConfigDto extends MxBootstrapConfigDto {

    val default: BootstrapConfigDto =
      BootstrapConfigDto(
        consoleLogging = true.toSome,
        colorConsole = true.toSome,
        fileLogging = true.toSome,
        logAppConfig = true.toSome,
        logsDir = "logs".toSome,
        cacheDir = "cache".toSome,
        dataDir = "data".toSome,
        tempDir = "temp".toSome,
        defaultLogLevel = LogLevel.DEBUG.name.toSome,
        logLevels = Vector.empty[LogLevelConfig],
        configFilePollInterval = 1.minute.some,
      ).copy(source = Some("default"))

    val empty: BootstrapConfigDto =
      BootstrapConfigDto()
        .copy(source = Some("empty"))

  }
  @CompanionGen
  case class BootstrapConfigDto(
    source: Option[String] = None,
    appName: Option[AppName] = None,
    consoleLogging: Option[Boolean] = None,
    colorConsole: Option[Boolean] = None,
    fileLogging: Option[Boolean] = None,
    logAppConfig: Option[Boolean] = None,
    logsDir: Option[String] = None,
    tempDir: Option[String] = None,
    cacheDir: Option[String] = None,
    dataDir: Option[String] = None,
    defaultLogLevel: Option[String] = None,
    logLevels: Vector[LogLevelConfig] = Vector.empty[LogLevelConfig],
    configFilePollInterval: Option[FiniteDuration] = None,
) extends NamedToString {
    def +(right: BootstrapConfigDto): BootstrapConfigDto =
      BootstrapConfigDto(
        appName = right.appName.orElse(appName),
        consoleLogging = right.consoleLogging.orElse(consoleLogging),
        colorConsole = right.colorConsole.orElse(colorConsole),
        fileLogging = right.fileLogging.orElse(fileLogging),
        logAppConfig = right.logAppConfig.orElse(logAppConfig),
        logsDir = right.logsDir orElse logsDir,
        tempDir = right.tempDir orElse tempDir,
        cacheDir = right.cacheDir orElse cacheDir,
        dataDir = right.dataDir orElse dataDir,
        defaultLogLevel = right.defaultLogLevel orElse defaultLogLevel,
        logLevels = logLevels ++ right.logLevels,
        configFilePollInterval = configFilePollInterval orElse right.configFilePollInterval,
      )
  }

  object UnifiedLogLevel {
    def apply(logLevel: LogLevel): UnifiedLogLevel = ???
  }

  case class UnifiedLogLevel(wvletLogLevel: wvlet.log.LogLevel, zioLogLevel: zio.LogLevel) {

    import a8.shared.SharedImports.canEqual.given

    lazy val isTrace = wvletLogLevel == wvlet.log.LogLevel.TRACE

    lazy val resolvedWvletLogLevel: wvlet.log.LogLevel = {
      if (isTrace)
        LogLevel.ALL
      else
        wvletLogLevel
    }

  }

  object AppName extends StringValue.Companion[AppName]
  case class AppName(value: String) extends StringValue

  case class LogsDir(unresolved: Directory) extends DirectoryValue

  case class CacheDir(unresolved: Directory) extends DirectoryValue

  case class TempDir(unresolved: Directory) extends DirectoryValue

  object WorkDir extends LoggingF {

    val layer: ZLayer[TempDir with Scope, Throwable, WorkDir] = ZLayer(live)

    val live: ZIO[TempDir with Scope, Throwable, WorkDir] = {
      for {
        tempDir <- zservice[TempDir]
        workDir <-
          ZIO.acquireRelease(
            ZIO.attempt(WorkDir(tempDir.unresolved.subdir(FileSystem.fileSystemCompatibleTimestamp())))
          )(
            workDir =>
              ZIO.attemptBlocking {
                if ( workDir.unresolved.exists() )
                  workDir.unresolved.delete()
              }.logVoid
          )
      } yield workDir
    }
  }
  case class WorkDir(unresolved: Directory) extends DirectoryValue

  case class DataDir(unresolved: Directory) extends DirectoryValue

  trait DirectoryValue {
    val unresolved: Directory
    lazy val resolved: Directory = {
      unresolved.makeDirectories()
      unresolved
    }
  }

}

case class BootstrapConfig(
  appName: AppName,
  consoleLogging: Boolean,
  colorConsole: Boolean,
  fileLogging: Boolean,
  logAppConfig: Boolean,
  logsDir: LogsDir,
  tempDir: TempDir,
  cacheDir: CacheDir,
  dataDir: DataDir,
  appArgs: zio.ZIOAppArgs,
  defaultLogLevel: UnifiedLogLevel,
  logLevels: Vector[LogLevelConfig],
  configFilePollInterval: FiniteDuration,
) extends NamedToString { self =>

  lazy val invalidLogLevels: Vector[LogLevelConfig] =
    logLevels
      .flatMap { ll =>
        ll.resolvedLevel match {
          case None =>
            Some(ll)
          case _ =>
            None
        }
      }

  lazy val resolvedLogLevels: Vector[(String, LogLevel)] =
    for {
      ll <- logLevels
      level <- ll.resolvedLevel
    } yield ll.name -> level

}
