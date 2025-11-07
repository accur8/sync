package a8.shared.app

import a8.shared.app.BootstrapConfig.*
import a8.shared.app.Ctx.InternalCtx
import a8.shared.zreplace.Chunk

case class AppCtx(
  bootstrapper: Bootstrapper,
  ox0: ox.Ox,
) extends Ctx with InternalCtx {

  override def parentOpt: Option[Ctx] =
    None

  override def appCtx: AppCtx = this

  override def label: Option[String] =
    Some(bootstrapper.bootstrapConfig.appName.value)

  override def ancestry: Iterator[Ctx] =
    Iterator(this)

  override def parent: Ctx =
    this

}
