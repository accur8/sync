package playground


import a8.shared.app.{AppCtx, BootstrappedIOApp}

object BootstrappedIOAppDemo extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit =
    logger.info("boom")

}
