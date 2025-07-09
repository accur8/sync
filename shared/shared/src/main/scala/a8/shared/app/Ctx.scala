package a8.shared.app

object Lifecycle {

  trait Listener {
    def onCancel(lifecycle: Lifecycle): Unit = ()
    def onFinish(lifecycle: Lifecycle): Unit = ()
    def onSuccess(lifecycle: Lifecycle): Unit = ()
    def onError(lifecycle: Lifecycle, error: Throwable): Unit = ()
  }

}

trait Lifecycle {

  def label: Option[String] = None

  def qualifiedLabel: String =
    ancestry
      .flatMap(_.label)
      .mkString(":")

  def ancestry: Iterator[Lifecycle] =
    Iterator(this) ++ parent.ancestry

  def parent: Lifecycle
  def appLifecycle: Lifecycle

  def cancel(): Unit
  def isCancelled(): Boolean
  def isRunning(): Boolean

  def register(listener: Lifecycle.Listener): Unit

}
