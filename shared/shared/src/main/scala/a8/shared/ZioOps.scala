package a8.shared

import zio.Trace
import zio.stream.ZStream

class ZioOps[R, E, A](effect: zio.ZIO[R,E,A])(implicit trace: Trace) {

  def zstreamEval(implicit trace: Trace): ZStream[R,E,A] =
    ZStream.fromZIO(effect)

  def zstreamExec(implicit trace: Trace): ZStream[R,E,Nothing] =
    ZStream.execute(effect)

}
