package a8.sync


import Imports._

object Warnings {
  def create[F[_] : Async,A]: F[Warnings[F,A]] = {
    val F = Async[F]
    for {
      ref <- F.ref(Chain.empty[A])
    } yield
      new Warnings[F,A] {
        override def append(warning: A): F[Unit] =
          ref.update(_.append(warning))
        override def get: F[Chain[A]] =
          ref.get
      }
  }
}


trait Warnings[F[_],A] {
  def append(warning: A): F[Unit]
  def get: F[Chain[A]]
}
