package a8.shared.app


import a8.shared.SharedImports.*


object BootstrappedIOAppDemo extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit = {
    logger.info("something weird")
  }

}
