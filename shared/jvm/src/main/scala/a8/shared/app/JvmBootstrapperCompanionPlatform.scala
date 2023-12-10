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
import zio.{Task, ZIO, ZIOAppArgs, ZLayer}

import scala.collection.mutable
import scala.reflect.ClassTag
import a8.common.logging.{ Level => LogLevel }

@scala.annotation.nowarn
trait JvmBootstrapperCompanionPlatform extends BootstrapperCompanionImpl {


  override val layer: ZLayer[AppName & ZIOAppArgs, Throwable, Bootstrapper] =
    ZLayer(live)


  val live: ZIO[AppName & ZIOAppArgs, Throwable, Bootstrapper] = {

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
            BootstrapConfigDto.fromConfigMojo(configMojoRoot("global_bootstrap"))
              .copy(source = Some("config.hocon - global_bootstrap"))

          val bootstrapConfigPropertyName: String = appName.value + ".bootstrap"

          val bootstrapDto =
            BootstrapConfigDto.fromConfigMojo(configMojo("bootstrap"))
              .copy(source = Some("config.hocon - " + bootstrapConfigPropertyName))

          lazy val resolveAppName: AppName = resolvedDto.appName.getOrElse(appName)

          lazy val dtoChain = List(BootstrapConfigDto.default, globalBootstrapDto, bootstrapDto)

          lazy val resolvedDto = dtoChain.reduce(_ + _).copy(source = Some("resolved"))

          bootstrapLogs.append(s"bootstrap chain = ${dtoChain.mkString("List(\n  ", ",\n  ", ",\n)")}")

          //        bootstrapLogs.append(s"resolved dto ${resolvedDto}")

          val logsDir = LogsDir(FileSystem.dir(resolvedDto.logsDir.get))
          val configDir = ConfigDir(FileSystem.dir(resolvedDto.configDir.get))

          BootstrapConfig(
            appName = resolveAppName,
            logsDir = logsDir,
            tempDir = TempDir(FileSystem.dir(resolvedDto.tempDir.get)),
            cacheDir = CacheDir(FileSystem.dir(resolvedDto.cacheDir.get)),
            dataDir = DataDir(FileSystem.dir(resolvedDto.dataDir.get)),
            configDir = configDir,
            appArgs = args,
            resolvedDto = resolvedDto,
          )
        }

        override def appConfig[A: JsonCodec](implicit jsonReaderOptions: ZJsonReaderOptions): Task[A] =
          configMojo.app.asF[A]
      }
    }

  }
}
