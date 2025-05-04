package a8.shared


import a8.shared.SharedImports._
import a8.shared.ZRefreshable.AutoExpire
import zio.{Ref, Task, UIO, ZIO}

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.FiniteDuration

/**
 * Something that expires and when it expires it will be refreshed, first use case is for authorization tokens
 */
object ZRefreshable {

  def make[A]: UIO[ZRefreshable[A]] =
    for {
      stateRef <- zio.Ref.make[State[A]](State.Empty[A]())
      lock <- zio.Semaphore.make(1)
    } yield RefreshableImpl[A](stateRef, _ => zsucceed(false), lock)


  case class RefreshableImpl[A](
    stateRef: Ref[State[A]],
    isExpiredFn: State.Initialized[A]=>UIO[Boolean],
    constructorLock: zio.Semaphore,
  ) extends ZRefreshable[A] {

    override def autoExpire(implicit autoExpire: AutoExpire[A]): ZRefreshable[A] =
      copy(
        isExpiredFn = autoExpire.isExpired
      )

    override def expireAfter(duration: FiniteDuration): ZRefreshable[A] = {

      def isExpired(state: State.Initialized[A]): UIO[Boolean] = {
        val expiresAt = state.createdAt.plus(duration.toMillis, ChronoUnit.MILLIS)
        val now = LocalDateTime.now()
        zsucceed(expiresAt.isBefore(now))
      }

      copy(
        isExpiredFn = isExpired
      )

    }

    def stateZ: UIO[State[A]] =
      for {
        state <- stateRef.get
        result <-
          state match {
            case si@ State.Initialized(_, _) =>
              isExpiredFn(si)
                .flatMap {
                  case true =>
                    val expired = State.Expired[A]()
                    stateRef
                      .set(expired)
                      .as(expired)
                  case false =>
                    zsucceed(state)
                }
            case _ =>
              ZIO.succeed(state)
          }
      } yield result


    override def invalidate: UIO[Unit] =
      stateRef.set(State.Empty[A]())

    def attemptConstructWithLock(constructor: Task[A]): ZIO[Any, Throwable, A] = {
      val effect =
        for {
          state <- stateZ
          value <- {
            state match {
              case State.Initialized(v, _) =>
                ZIO.succeed(v)
              case _ =>
                stateRef
                  .set(State.Initializing[A]())
                  .asZIO(
                    constructor
                      .tapEither {
                        case Left(_) =>
                          stateRef.set(State.Error())
                        case Right(v) =>
                          stateRef.set(State.Initialized(v, LocalDateTime.now()))
                      }
                  )
            }
          }
        } yield value
      constructorLock.withPermit(effect)
    }

    override def getOrCreate(constructor: Task[A]): Task[A] =
      for {
        state <- stateZ
        value <-
          state match {
            case State.Initialized(v, _) =>
              ZIO.succeed(v)
            case _ =>
              attemptConstructWithLock(constructor)
          }
      } yield value

    override def expireVia(isExpiredFn: State.Initialized[A] => UIO[Boolean]): UIO[ZRefreshable[A]] =
      zsucceed(copy(isExpiredFn = isExpiredFn))

  }

  sealed trait State[A]
  object State {
    case class Empty[A]() extends State[A]
    case class Initializing[A]() extends State[A]
    case class Error[A]() extends State[A]
    case class Initialized[A](value: A, createdAt: LocalDateTime) extends State[A]
    case class Expired[A]() extends State[A]
  }

  trait AutoExpire[A] {
    def isExpired(state: State.Initialized[A]): UIO[Boolean]
  }

  abstract class AbstractAutoExpire[A] extends AutoExpire[A] {

    def expiresAt(state: State.Initialized[A]): LocalDateTime

    override def isExpired(state: State.Initialized[A]): UIO[Boolean] = {
      val now = LocalDateTime.now()
      val expiresAt0 = expiresAt(state)
      zsucceed(expiresAt0.isBefore(now))
    }
  }

}



trait ZRefreshable[A] {
  def expireVia(isExpiredFn: ZRefreshable.State.Initialized[A] => UIO[Boolean]): UIO[ZRefreshable[A]]
  def autoExpire(implicit autoExpire: AutoExpire[A]): ZRefreshable[A]
  def expireAfter(duration: FiniteDuration): ZRefreshable[A]
  def getOrCreate(constructor: Task[A]): Task[A]
  def stateZ: UIO[ZRefreshable.State[A]]
  def invalidate: UIO[Unit]
}


