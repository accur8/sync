package a8.shared.ops

import a8.shared.Fs2StreamUtils
import java.util.concurrent.atomic.AtomicReference
import a8.shared.SharedImports._

class StreamOps[F[_] : Async,A](stream: fs2.Stream[F,A]) {

  private lazy val F = Async[F]

  def onLast(fn: A=>fs2.Stream[F,A]): fs2.Stream[F,A] =
    onLastO {
      case None =>
        fs2.Stream.empty
      case Some(a) =>
        fn(a)
    }

  def onLastO(fn: Option[A]=>fs2.Stream[F,A]): fs2.Stream[F,A] = {
    val latest = new AtomicReference[Option[A]](None)
    stream
      .map { a =>
        latest.set(Some(a))
        a
      }
      .onComplete {
        fn(latest.get)
      }
  }

  def materialize: fs2.Stream[F,Fs2StreamUtils.Event[A]] =
    stream.through(Fs2StreamUtils.materialize)

  def onLastWithState[B](stateGetter: A=>Option[B])(fn: Option[B]=>fs2.Stream[F,A]): fs2.Stream[F,A] = {

    fs2.Stream.eval {
      F.ref(none[B]).map { lastR =>
        stream
          .evalMap { a =>
            stateGetter(a) match {
              case Some(v) =>
                lastR.set(Some(v)).map(_ => a)
              case None =>
                F.pure(a)
            }
          }
          .onComplete {
            lastR.get.map(fn).toStream
          }
      }
    }.flatten
  }

  def onLastF(fn: A=>F[Unit]): fs2.Stream[F,A] =
    onLast { o =>
      fs2.Stream.exec(F.defer(fn(o)))
    }

}
