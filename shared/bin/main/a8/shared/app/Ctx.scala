package a8.shared.app

import a8.shared.app.Ctx.{ChildCtx, InternalCtx}
import a8.shared.zreplace.Resource.AttributeKey
import ox.Ox

import scala.collection.concurrent.TrieMap
import scala.collection.mutable

object Ctx {

  trait Listener {
    def onCancel(ctx: Ctx): Unit = ()
    def onSuccess(ctx: Ctx): Unit = ()
    def onError(ctx: Ctx, error: Throwable): Unit = ()
    def onCompletion(ctx: Ctx, completion: Completion): Unit = {
      completion match {
        case Completion.Success() =>
          onSuccess(ctx)
        case Completion.Cancelled() =>
          onCancel(ctx)
        case Completion.Thrown(th) =>
          onError(ctx, th)
      }
    }
  }

  object Completion {
    case class Success() extends Completion {
      override def isSuccess: Boolean = true
    }
    case class Cancelled() extends Completion {
      override def isCancelled: Boolean = true
    }
    case class Thrown(th: Throwable) extends Completion {
      override def isThrown: Boolean = true
    }
  }

  sealed trait Completion {
    def isCancelled: Boolean = false
    def isSuccess: Boolean = false
    def isThrown: Boolean = false
  }

  case class ChildCtx(
    parent: Ctx,
    ox0: ox.Ox,
  ) extends Ctx with InternalCtx {

    override def parentOpt: Option[Ctx] =
      Some(parent)

    override def appCtx: AppCtx = parent.appCtx

    override def label: Option[String] =
      parent.label

    override def ancestry: Iterator[Ctx] =
      Iterator(this) ++ parent.ancestry
  }

  trait InternalCtx { self: Ctx =>

    protected var cancelled = false
    protected var running = false
    protected val listeners: mutable.Buffer[Listener] = mutable.Buffer.empty[Ctx.Listener]

    override def withSubCtx[A](fn: Ctx ?=> A): A = {
      ox.supervised {
        val childCtx = ChildCtx(this, ox0 = summon[ox.Ox])
        try {
          fn(using childCtx)
        } finally {
          childCtx.running = false
          childCtx.listeners.foreach(_.onCompletion(childCtx, Completion.Success()))
        }
      }
    }

  }

}

trait Ctx { self: InternalCtx =>

  def label: Option[String] = None

  def qualifiedLabel: String =
    ancestry
      .flatMap(_.label)
      .mkString(":")

  def ancestry: Iterator[Ctx] =
    Iterator(this) ++ parent.ancestry

  def parent: Ctx
  def appCtx: AppCtx
  def parentOpt: Option[Ctx]

  def cancel(): Unit = {
    if (!cancelled) {
      cancelled = true
      listeners.foreach(_.onCancel(this))
    }
  }

  def isCancelled(): Boolean =
    cancelled

  def isRunning(): Boolean =
    running

  def register(listener: Ctx.Listener): Unit =
    listeners += listener

  def withSubCtx[A](fn: Ctx ?=> A): A


  private lazy val attributes = TrieMap.empty[AttributeKey,Any]
  def get[A](key: AttributeKey): Option[A] = {
    parentOpt
      .flatMap(_.get[A](key))
      .orElse(
        attributes.get(key).asInstanceOf[Option[A]]
      )
  }

  def put[A](key: AttributeKey, value: A): Unit = {
    attributes.put(key, value)
  }

}
