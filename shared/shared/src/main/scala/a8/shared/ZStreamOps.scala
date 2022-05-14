package a8.shared


import zio.stream.ZStream

import java.util.concurrent.atomic.AtomicReference

class ZStreamOps[R,E,A](stream: ZStream[R,E,A]) {

  def onLast(fn: A => ZStream[R, E, A]): ZStream[R, E, A] =
    onLastO {
      case None =>
        ZStream.empty
      case Some(a) =>
        fn(a)
    }

  def onLastO(tailFn: Option[A] => ZStream[R, E, A]): ZStream[R, E, A] = {
    val latest = new AtomicReference[Option[A]](None)
    val head =
      stream
        .map { a =>
          latest.set(Some(a))
          a
        }
    head ++ tailFn(latest.get)
  }
}
