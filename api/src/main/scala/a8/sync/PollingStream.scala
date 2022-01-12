package a8.sync


import scala.concurrent.duration.FiniteDuration
import Imports._
import a8.shared.app.Logging
import cats.effect.Async

import language.higherKinds

object PollingStream {

  def tailingStream[F[_] : Async, A](
    start: Option[A],
    pollFn: Option[A]=>fs2.Stream[F,A],
    interval: FiniteDuration,
  ): fs2.Stream[F,A] = {
    pollFn(start)
      .onLastO { last =>
        val delay = fs2.Stream.empty[F].delayBy(interval)
        delay ++ tailingStream[F,A](last, pollFn, interval)
      }
  }

  def fromStream[F[_] : Async, A](
    finitePollFn: =>fs2.Stream[F,A],
    interval: FiniteDuration,
  ): fs2.Stream[F,A] = {
    tailingStream(
      start = None,
      pollFn = { _: Option[A] => finitePollFn },
      interval = interval,
    )
  }

  def fromIterable[F[_] : Async, A](
    finitePollFn: =>F[Iterable[A]],
    pauseOnEmpty: FiniteDuration,
    onFailure: Throwable=>fs2.Stream[F, A],
  ): fs2.Stream[F,A] = {

    def runForever: fs2.Stream[F,A] =
      runTilEmpty ++ fs2.Stream.empty[F].delayBy(pauseOnEmpty) ++ runForever

    def runTilEmpty: fs2.Stream[F,A] = {
      fs2.Stream
        .eval(finitePollFn)
        .attempt
        .flatMap {
          case Left(th) =>
            onFailure(th)
          case Right(iter) if iter.isEmpty =>
            fs2.Stream.empty
          case Right(iter) =>
            fs2.Stream.iterable(iter) ++ runTilEmpty
        }
    }

    runForever

  }

}
