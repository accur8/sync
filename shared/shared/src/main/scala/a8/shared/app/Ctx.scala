package a8.shared.app

import a8.shared.app.Ctx.{ChildCtx, InternalCtx}
import a8.shared.zreplace.Resource.AttributeKey
import ox.Ox

import scala.collection.concurrent.TrieMap
import scala.collection.mutable

object Ctx:

  enum State derives CanEqual:
    case NotStarted
    case Running
    case Cancelled
    case Completed(result: CompletionResult)

  enum CompletionResult derives CanEqual:
    case Success
    case Error(th: Throwable)
    case Cancellation

  trait Listener:
    def onCancel(ctx: Ctx): Unit = ()
    def onSuccess(ctx: Ctx): Unit = ()
    def onError(ctx: Ctx, error: Throwable): Unit = ()
    def onCompletion(ctx: Ctx, result: CompletionResult): Unit =
      result match
        case CompletionResult.Success =>
          onSuccess(ctx)
        case CompletionResult.Cancellation =>
          onCancel(ctx)
        case CompletionResult.Error(th) =>
          onError(ctx, th)

  case class ChildCtx(
    parent: Ctx,
    ox0: ox.Ox,
  ) extends Ctx with InternalCtx:

    override def parentOpt: Option[Ctx] =
      Some(parent)

    override def appCtx: AppCtx = parent.appCtx

    override def label: Option[String] =
      parent.label

    override def ancestry: Iterator[Ctx] =
      Iterator(this) ++ parent.ancestry

  trait InternalCtx:
    self: Ctx =>

    protected var state: Ctx.State = Ctx.State.NotStarted
    protected val listeners: mutable.Buffer[Listener] = mutable.Buffer.empty[Ctx.Listener]

    def unsafeRun[A](fn: ()=>A): A =
      state match
        case Ctx.State.NotStarted =>
          state = Ctx.State.Running
          try
            val a = fn()
            // Check if cancelled during execution
            state match
              case Ctx.State.Cancelled =>
                state = Ctx.State.Completed(Ctx.CompletionResult.Cancellation)
                listeners.foreach(_.onCompletion(this, Ctx.CompletionResult.Cancellation))
              case Ctx.State.Running =>
                state = Ctx.State.Completed(Ctx.CompletionResult.Success)
                listeners.foreach(_.onCompletion(this, Ctx.CompletionResult.Success))
              case _ =>
                // Should not happen, but handle gracefully
                ()
            a
          catch
            case th: Throwable =>
              state = Ctx.State.Completed(Ctx.CompletionResult.Error(th))
              listeners.foreach(_.onCompletion(this, Ctx.CompletionResult.Error(th)))
              throw th
        case _ =>
          sys.error(s"invalid state transition - cannot run from state $state")

    override def withSubCtx[A](fn: Ctx ?=> A): A =
      ox.supervised:
        val childCtx = ChildCtx(this, ox0 = summon[ox.Ox])
        def impl() = fn(using childCtx)
        childCtx.unsafeRun(() => impl())



trait Ctx:
  self: InternalCtx =>

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

  def cancel(): Unit =
    state match
      case Ctx.State.NotStarted | Ctx.State.Running =>
        state = Ctx.State.Cancelled
        listeners.foreach(_.onCancel(this))
      case _ =>
        // Already cancelled or completed, do nothing

  def isCancelled(): Boolean =
    state == Ctx.State.Cancelled || (state match {
      case Ctx.State.Completed(Ctx.CompletionResult.Cancellation) => true
      case _ => false
    })

  def isRunning(): Boolean =
    state == Ctx.State.Running

  def currentState: Ctx.State =
    state

  def register(listener: Ctx.Listener): Unit =
    listeners += listener

  def withSubCtx[A](fn: Ctx ?=> A): A


  private lazy val attributes = TrieMap.empty[AttributeKey,Any]
  def get[A](key: AttributeKey): Option[A] =
    parentOpt
      .flatMap(_.get[A](key))
      .orElse(
        attributes.get(key).asInstanceOf[Option[A]]
      )

  def put[A](key: AttributeKey, value: A): Unit =
    attributes.put(key, value)
