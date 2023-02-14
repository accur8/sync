package a8.shared.app

import a8.shared.{FileSystem, HoconOps}
import a8.shared.FileSystem.Directory
import a8.shared.app.BootstrapConfig._
import a8.shared.json.{JsonCodec, ast}

import java.nio.file.{Path, Paths}
import a8.shared.SharedImports._
import a8.shared.json.JsonReader.{JsonReaderOptions, ReadResult}
import a8.shared.json.ZJsonReader.ZJsonReaderOptions
import a8.shared.json.ast.JsDoc
import wvlet.log.LogLevel
import zio.{Task, ZIO, ZIOAppArgs, ZLayer}

import scala.collection.mutable
import scala.reflect.ClassTag

trait JvmBootstrapperCompanionPlatform extends BootstrapperCompanionImpl {


  override val layer: ZLayer[AppName with ZIOAppArgs, Throwable, Bootstrapper] =
    ZLayer(live)


  val live: ZIO[AppName with ZIOAppArgs, Throwable, Bootstrapper] = {

    for {
      appName <- zservice[AppName]
      args <- zservice[ZIOAppArgs]
    } yield {

      val configMojoRoot = a8.shared.ConfigMojo.root.mojoRoot
      val configMojo = configMojoRoot(appName.value)

      val bootstrapLogs = mutable.Buffer[String]()

      new Bootstrapper {

        override lazy val logs = bootstrapLogs.toList

        override lazy val rootConfig: ast.JsVal =
          HoconOps.impl.toJsVal(configMojo.hoconValue)

        override lazy val directoriesSearched: Iterable[Path] =
          configMojoRoot.root.directoriesChecked

        override lazy val configFiles: Iterable[Path] =
          configMojoRoot.root.sources

        override lazy val bootstrapConfig: BootstrapConfig = {

          val globalBootstrapDto =
            (configMojoRoot("global_bootstrap").asReadResult[BootstrapConfig.BootstrapConfigDto] match {
              case ReadResult.Success(v, _, _, _) =>
                v
              case ReadResult.Error(re, _, _) =>
                sys.error(re.prettyMessage)
            }).copy(source = Some("config.hocon - global_bootstrap"))

          val bootstrapConfigPropertyName = appName.value + ".bootstrap"
          val bootstrapDto =
            (configMojo("bootstrap").asReadResult[BootstrapConfig.BootstrapConfigDto] match {
              case ReadResult.Success(v, _, _, _) =>
                v
              case ReadResult.Error(re, _, _) =>
                sys.error(re.prettyMessage)
            }).copy(source = Some("config.hocon - " + bootstrapConfigPropertyName))

          val dtoChain = List(BootstrapConfigDto.default, globalBootstrapDto, bootstrapDto)

          bootstrapLogs.append(s"bootstrap chain = ${dtoChain.mkString("List(\n  ", ",\n  ", ",\n)")}")

          val resolvedDto = dtoChain.reduce(_ + _).copy(source = Some("resolved"))
          //        bootstrapLogs.append(s"resolved dto ${resolvedDto}")

          val defaultLogLevel = {

            def find(nameOpt: Option[String]): Option[LogLevel] = {
              nameOpt
                .map(_.toLowerCase)
                .map { defaultLogLevelName =>
                  wvlet.log.LogLevel.values.find(_.name.toLowerCase == defaultLogLevelName).get
                }
            }

            find(Option(System.getProperty("defaultLogLevel")))
              .orElse(find(resolvedDto.defaultLogLevel))
              .get

          }

          BootstrapConfig(
            appName = resolvedDto.appName.getOrElse(appName),
            consoleLogging = resolvedDto.consoleLogging.get,
            colorConsole = resolvedDto.colorConsole.get,
            fileLogging = resolvedDto.fileLogging.get,
            logAppConfig = resolvedDto.logAppConfig.get,
            logsDir = LogsDir(FileSystem.dir(resolvedDto.logsDir.get)),
            tempDir = TempDir(FileSystem.dir(resolvedDto.tempDir.get)),
            cacheDir = CacheDir(FileSystem.dir(resolvedDto.cacheDir.get)),
            dataDir = DataDir(FileSystem.dir(resolvedDto.dataDir.get)),
            defaultLogLevel = defaultLogLevel,
            appArgs = args,
            logLevels = resolvedDto.logLevels,
            configFilePollInterval = resolvedDto.configFilePollInterval.get,
          )
        }

        override def appConfig[A: JsonCodec](implicit jsonReaderOptions: ZJsonReaderOptions): Task[A] =
          configMojo.app.asF[A]
      }
    }

  }
}
