package a8.shared.app

import a8.shared.FileSystem.Directory
import a8.shared.{CompanionGen, FileSystem, NamedToString, StringValue}
import a8.shared.app.BootstrapConfig._
import a8.shared.app.MxBootstrapConfig.MxBootstrapConfigDto

import java.nio.file.{Path, Paths}
import a8.shared.SharedImports._
import wvlet.log.LogLevel


/*
 *  + app will have a default name which will be it's default prefix
 *  + can override the default prefix w/ ?????
 */
object BootstrapConfig {

  object BootstrapConfigDto extends MxBootstrapConfigDto {

    val default =
      BootstrapConfigDto(
        consoleLogging = true.toSome,
        colorConsole = true.toSome,
        fileLogging = true.toSome,
        logsDir = "logs".toSome,
        cacheDir = "cache".toSome,
        dataDir = "data".toSome,
        tempDir = "temp".toSome,
        defaultLogLevel = LogLevel.DEBUG.name.toSome,
      ).copy(source = Some("default"))

    val empty =
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
    logsDir: Option[String] = None,
    tempDir: Option[String] = None,
    cacheDir: Option[String] = None,
    dataDir: Option[String] = None,
    defaultLogLevel: Option[String] = None,
  ) extends NamedToString {
    def +(right: BootstrapConfigDto): BootstrapConfigDto =
      BootstrapConfigDto(
        appName = right.appName.orElse(appName),
        consoleLogging = right.consoleLogging.orElse(consoleLogging),
        colorConsole = right.colorConsole.orElse(colorConsole),
        fileLogging = right.fileLogging.orElse(fileLogging),
        logsDir = right.logsDir.orElse(logsDir),
        tempDir = right.tempDir.orElse(tempDir),
        cacheDir = right.cacheDir.orElse(cacheDir),
        dataDir = right.dataDir.orElse(dataDir),
        defaultLogLevel = right.defaultLogLevel.orElse(defaultLogLevel),
      )
  }

  object AppName extends StringValue.Companion[AppName]
  case class AppName(value: String) extends StringValue

  case class LogsDir(unresolved: Directory) extends DirectoryValue

  case class CacheDir(unresolved: Directory) extends DirectoryValue

  case class TempDir(unresolved: Directory) extends DirectoryValue

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
  logsDir: LogsDir,
  tempDir: TempDir,
  cacheDir: CacheDir,
  dataDir: DataDir,
  defaultLogLevel: wvlet.log.LogLevel,
) extends NamedToString
