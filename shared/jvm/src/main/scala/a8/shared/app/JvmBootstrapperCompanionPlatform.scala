package a8.shared.app

import a8.shared.ConfigMojoOps.ReadResult
import a8.shared.{ConfigMojo, FileSystem, HoconOps}
import a8.shared.FileSystem.Directory
import a8.shared.app.BootstrapConfig._
import a8.shared.json.{JsonCodec, ast}

import java.nio.file.{Path, Paths}
import a8.shared.SharedImports._
import a8.shared.json.ast.JsDoc

import scala.collection.mutable
import scala.reflect.ClassTag

class JvmBootstrapperCompanionPlatform extends BootstrapperCompanionImpl {

  override def apply(appName: BootstrapConfig.AppName): Bootstrapper = {

    val configMojoRoot = ConfigMojo().mojoRoot
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
            case ReadResult.NoValue() =>
              BootstrapConfig.BootstrapConfigDto()
            case ReadResult.Value(v) =>
              v
            case ReadResult.Error(msg) =>
              sys.error(msg)
          }).copy(source = Some("config.hocon - global_bootstrap"))

        val bootstrapConfigPropertyName = appName.value + ".bootstrap"
        val bootstrapDto =
          (configMojo("bootstrap").asReadResult[BootstrapConfig.BootstrapConfigDto] match {
            case ReadResult.NoValue() =>
              BootstrapConfig.BootstrapConfigDto()
            case ReadResult.Value(v) =>
              v
            case ReadResult.Error(msg) =>
              sys.error(msg)
          }).copy(source = Some("config.hocon - " + bootstrapConfigPropertyName))

        val dtoChain = List(BootstrapConfigDto.default, globalBootstrapDto, bootstrapDto)

        bootstrapLogs.append(s"bootstrap chain = ${dtoChain.mkString("List(\n  ", ",\n  ", ",\n)")}")

        val resolvedDto = dtoChain.reduce(_ + _).copy(source = Some("resolved"))
//        bootstrapLogs.append(s"resolved dto ${resolvedDto}")

        BootstrapConfig(
          appName = resolvedDto.appName.getOrElse(appName),
          consoleLogging = resolvedDto.consoleLogging.get,
          colorConsole = resolvedDto.colorConsole.get,
          fileLogging = resolvedDto.fileLogging.get,
          logsDir = LogsDir(FileSystem.dir(resolvedDto.logsDir.get)),
          tempDir = TempDir(FileSystem.dir(resolvedDto.tempDir.get)),
          cacheDir = CacheDir(FileSystem.dir(resolvedDto.cacheDir.get)),
          dataDir = DataDir(FileSystem.dir(resolvedDto.dataDir.get)),
          defaultLogLevel = wvlet.log.LogLevel.values.find(_.name.toLowerCase == resolvedDto.defaultLogLevel.get.toLowerCase).get,
        )
      }

      override def appConfig[A : JsonCodec : ClassTag]: A =
        configMojo.app.as[A]
    }

  }
}
