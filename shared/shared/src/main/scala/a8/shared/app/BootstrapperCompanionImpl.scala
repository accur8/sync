package a8.shared.app


import a8.shared.app.BootstrapConfig.AppName

trait BootstrapperCompanionImpl {
  def apply(appName: AppName): Bootstrapper
}
