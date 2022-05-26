package a8.shared.app

import a8.shared.FileSystem.File
import a8.shared.json.JsonCodec
import a8.shared.json.ast.JsVal

import java.nio.file.Path
import scala.reflect.ClassTag

object Bootstrapper extends BootstrapperCompanionPlatform with BootstrapperCompanionImpl {
}

trait Bootstrapper {
  def logs: Iterable[String]
  def rootConfig: JsVal
  def bootstrapConfig: BootstrapConfig
  def directoriesSearched: Iterable[Path]
  def configFiles: Iterable[Path]
  def appConfig[A : JsonCodec : ClassTag]: A =
    rootConfig
      .toDoc("app")
      .unsafeAs[A]
}
