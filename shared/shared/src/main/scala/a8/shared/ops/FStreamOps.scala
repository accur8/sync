package a8.shared.ops


import a8.shared.SharedImports._

class FStreamOps[F[_] : Async,A](streamF: F[fs2.Stream[F,A]]) {

  def toStream =
    fs2.Stream.eval(streamF).flatten

}
