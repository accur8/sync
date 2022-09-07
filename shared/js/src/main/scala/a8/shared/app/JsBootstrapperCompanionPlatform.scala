package a8.shared.app
import zio.ZLayer

trait JsBootstrapperCompanionPlatform extends BootstrapperCompanionImpl {

  override val layer: ZLayer[BootstrapConfig.AppName, Throwable, Bootstrapper] = ???

}
