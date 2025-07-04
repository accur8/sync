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
  lazy val logs: Iterable[String]
  lazy val rootConfig: JsVal
  lazy val bootstrapConfig: BootstrapConfig
  lazy val directoriesSearched: Iterable[Path]
  lazy val configFiles: Iterable[Path]
  def appConfig[A : JsonCodec](implicit jsonReaderOptions: ZJsonReaderOptions): Task[A] =
    rootConfig
      .toRootDoc("app")
      .value
      .asF[A]
}
