package a8.shared.app


import a8.common.logging.{Logger, LoggerFactory}
import a8.shared.SharedImports.*
import a8.shared.app.BootstrapConfig.{AppName, WorkDir}

import scala.concurrent.duration.DurationInt

object BootstrappedIOAppDemo extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit = {
    logger.info("something weird")
  }

}
