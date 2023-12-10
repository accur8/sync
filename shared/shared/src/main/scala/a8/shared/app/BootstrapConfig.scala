package a8.shared.app

import a8.common.logging.{Level, LoggingBootstrapConfig}
import a8.shared.FileSystem.Directory
import a8.shared.{CompanionGen, ConfigMojo, FileSystem, NamedToString, StringValue, ZFileSystem}
import a8.shared.app.BootstrapConfig.*
import a8.shared.app.MxBootstrapConfig.*

import java.nio.file.{Path, Paths}
import a8.shared.SharedImports.*
import zio.{Duration, Scope, Task, ZIO, ZLayer}

import scala.concurrent.duration.FiniteDuration
import a8.common.logging.LoggingBootstrapConfig.LoggingBootstrapConfigDto
import a8.shared.json.JsonCodec
import a8.shared.json.JsonReader.{JsonReaderOptions, ReadResult}

/*
 *  + app will have a default AppName which will be it's default prefix
 *  + can override the default AppName with the appname System property
 *
 *  + app will have default log level
 *  + default log level can be overridden via system prop and bootstrap config
 *
 */
object BootstrapConfig {

  object LogLevelConfig extends MxLogLevelConfig {
  }
  @CompanionGen
  case class LogLevelConfig(
    name: String,
    level: String,
  ) {
    lazy val resolvedLevel: Option[Level] =
      Level
        .values
        .find(_.name =:= level)
  }

  object BootstrapConfigDto { //extends MxBootstrapConfigDto {

    val default: BootstrapConfigDto =
      BootstrapConfigDto(
        logsDir = "logs".toSome,
        cacheDir = "cache".toSome,
        dataDir = "data".toSome,
        tempDir = "temp".toSome,
        configDir = "config".toSome,
      ).copy(source = Some("default"))

    val empty: BootstrapConfigDto =
      BootstrapConfigDto()
        .copy(source = Some("empty"))

    def fromConfigMojo(configMojo: ConfigMojo): BootstrapConfigDto = {

      implicit val jsonReaderOptions: JsonReaderOptions = JsonReaderOptions.NoLogWarnings

      def v[A: JsonCodec](name: String): A =
        configMojo(name).as[A]

      BootstrapConfigDto(
        source = v[Option[String]]("source"),
        appName = v[Option[AppName]]("appName"),
        logsDir = v[Option[String]]("logsDir"),
        tempDir = v[Option[String]]("tempDir"),
        cacheDir = v[Option[String]](("cacheDir")),
        dataDir = v[Option[String]]("dataDir"),
        configDir = v[Option[String]]("configDir"),
        logging = loggingBootstrapConfigDtoFromConfigMojo(configMojo("logging")),
      )
    }

    def loggingBootstrapConfigDtoFromConfigMojo(configMojo: ConfigMojo): LoggingBootstrapConfigDto = {

      implicit val jsonReaderOptions: JsonReaderOptions = JsonReaderOptions.NoLogWarnings

      def v[A: JsonCodec](name: String): A =
        configMojo(name).as[A]

      LoggingBootstrapConfigDto(
        overrideSystemErr = v[Option[Boolean]]("overrideSystemErr"),
        overrideSystemOut = v[Option[Boolean]]("overrideSystemOut"),
        setDefaultUncaughtExceptionHandler = v[Option[Boolean]]("setDefaultUncaughtExceptionHandler"),
        fileLogging = v[Option[Boolean]]("fileLogging"),
        consoleLogging = v[Option[Boolean]]("consoleLogging"),
        hasColorConsole = v[Option[Boolean]]("hasColorConsole"),
      )
    }

  }
  case class BootstrapConfigDto(
    source: Option[String] = None,
    appName: Option[AppName] = None,
    logsDir: Option[String] = None,
    tempDir: Option[String] = None,
    cacheDir: Option[String] = None,
    dataDir: Option[String] = None,
    configDir: Option[String] = None,
    autoCreateConfigDir: Option[Boolean] = None,
    logging: LoggingBootstrapConfigDto = LoggingBootstrapConfigDto.default,
  ) extends NamedToString {
    def +(right: BootstrapConfigDto): BootstrapConfigDto =
      BootstrapConfigDto(
        appName = right.appName.orElse(appName),
        logsDir = right.logsDir orElse logsDir,
        tempDir = right.tempDir orElse tempDir,
        cacheDir = right.cacheDir orElse cacheDir,
        dataDir = right.dataDir orElse dataDir,
        configDir = right.configDir orElse configDir,
      )
  }

  object AppName extends StringValue.Companion[AppName]
  case class AppName(value: String) extends StringValue

  case class LogsDir(unresolved: Directory) extends DirectoryValue

  case class CacheDir(unresolved: Directory) extends DirectoryValue

  case class TempDir(unresolved: Directory) extends DirectoryValue

  case class ConfigDir(unresolved: Directory) extends DirectoryValue

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
    lazy val unresolvedZ: ZFileSystem.Directory = ZFileSystem.dir(unresolved.absolutePath)
    lazy val resolved: Directory = {
      unresolved.makeDirectories()
      unresolved
    }
    lazy val resolvedZ: Task[ZFileSystem.Directory] = {
      unresolvedZ.makeDirectories
        .as(unresolvedZ)
    }
  }

}

case class BootstrapConfig(
  appName: AppName,
  logsDir: LogsDir,
  tempDir: TempDir,
  cacheDir: CacheDir,
  dataDir: DataDir,
  configDir: ConfigDir,
  appArgs: zio.ZIOAppArgs,
  resolvedDto: BootstrapConfigDto,
) extends NamedToString { self =>

}
