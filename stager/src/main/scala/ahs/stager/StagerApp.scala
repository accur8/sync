package ahs.stager

import a8.shared.app.BootstrappedIOApp
import ahs.stager.CopyDataDemo.appConfig
import ahs.stager.model.StagerConfig

abstract class StagerApp extends BootstrappedIOApp {

  override lazy val defaultAppName: String = "ahsstager"

  lazy val config = appConfig[StagerConfig]


}
