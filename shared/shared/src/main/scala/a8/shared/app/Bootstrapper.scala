package a8.shared.app

import a8.shared.FileSystem.File
import a8.shared.json.JsonCodec
import a8.shared.json.ast.JsVal
import zio.Task

import java.nio.file.Path
import scala.reflect.ClassTag

object Bootstrapper extends BootstrapperCompanionPlatform with BootstrapperCompanionImpl {
}

trait Bootstrapper {
  val logs: Iterable[String]
  val rootConfig: JsVal
  val bootstrapConfig: BootstrapConfig
  val directoriesSearched: Iterable[Path]
  val configFiles: Iterable[Path]
  def appConfig[A : JsonCodec]: Task[A] =
    rootConfig
      .toDoc("app")
      .value
      .asF[A]
}
