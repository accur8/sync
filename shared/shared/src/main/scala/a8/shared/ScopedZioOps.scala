package a8.shared


import zio._
import zio.stream.ZStream

class ScopedZioOps[R, E, A](effect: ZIO[Scope with R,E,A])(implicit trace: Trace) {

  def scoped(implicit trace: Trace): ZIO[R,E,A] =
    ZIO.scoped[R].apply[E,A](effect)

  def use[A1](fn: A=>ZIO[Any,E,A1]): ZIO[R,E,A1] =
    ZIO.scoped[R](
      effect
        .flatMap(fn)
    )


}
