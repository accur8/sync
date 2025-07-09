package a8.shared.app

import a8.shared.app.BootstrapConfig.*
import a8.shared.zreplace.Chunk

case class AppLifecycle(
  bootstrapper: Bootstrapper,
) extends Lifecycle {

  override def label: Option[String] = Some(bootstrapper.bootstrapConfig.appName.value)

  override def ancestry: Iterator[Lifecycle] =
    Iterator(this)

  override def parent: Lifecycle =
    this

  override def appLifecycle: Lifecycle =
    this

  override def cancel(): Unit =
    ???

  override def isCancelled(): Boolean =
    ???

  override def isRunning(): Boolean =
    ???

  override def register(listener: Lifecycle.Listener): Unit =
    ???

}
