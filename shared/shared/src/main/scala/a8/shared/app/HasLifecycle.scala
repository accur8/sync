package a8.shared.app



object HasLifecycle {



}

trait HasLifecycle[A] { hlc =>
  def register(ctx: Ctx, a: A): Unit = {
    ctx.register(
      new Ctx.Listener {
        override def onCancel(ctx0: Ctx): Unit = hlc.onCancel(ctx0, a)
        override def onSuccess(ctx0: Ctx): Unit = hlc.onSuccess(ctx0, a)
        override def onError(ctx0: Ctx, error: Throwable): Unit = hlc.onError(ctx0, a, error)
      }
    )
  }
  def onCancel(lifecycle: Ctx, a: A): Unit = ()
  def onSuccess(lifecycle: Ctx, a: A): Unit = ()
  def onError(lifecycle: Ctx, a: A, error: Throwable): Unit = ()
}
