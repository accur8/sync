package a8.shared

import zio._



class ZioCollectOps[R, E, A, Collection[+Element] <: Iterable[Element]](
  in: Collection[ZIO[R,E,A]]
)(
  implicit
    bf: BuildFrom[Collection[ZIO[R, E, A]], A, Collection[A]],
    trace: Trace
) {

  def sequence: ZIO[R,E,Collection[A]] =
    ZIO.collectAll(in)

  def sequencePar: ZIO[R,E,Collection[A]] =
    ZIO.collectAllPar(in)

}
