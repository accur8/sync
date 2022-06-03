package a8.shared


import a8.shared.app.LoggerF
import a8.shared.json.JsonCodec
import zio._
import zio.stream.ZStream

import scala.reflect.ClassTag
import SharedImports.jsonCodecOps

class ZioOps[R, E, A](effect: zio.ZIO[R,E,A])(implicit trace: Trace) {

  def catchAllAndLog(implicit loggerF: LoggerF, trace: Trace): ZIO[R,Nothing,Unit] =
    effect
      .catchAll(th =>
        loggerF.warn(s"catchAllAndLog caught error -- ${th}")
      )
      .map(_ => ())

  def zstreamEval(implicit trace: Trace): ZStream[R,E,A] =
    ZStream.fromZIO(effect)

  def zstreamExec(implicit trace: Trace): ZStream[R,E,Nothing] =
    ZStream.execute(effect)

  def zstreamFlat[B](implicit trace: Trace, evidence: A <:< ZStream[R,E,B]) =
    zstreamEval.flatten

//  def withJsonContext[A: JsonCodec: ClassTag](a: A)(implicit loggerF: LoggerF, trace: Trace): zio.ZIO[R,E,A] = {
//    val ct = implicitly[ClassTag[A]]
//    lazy val context = s"${ct.runtimeClass.getSimpleName} ${a.compactJson}"
//    val wrappedEffect =
//      effect
//        .onError(cause =>
//          loggerF.warn(s"error -- ${context}", cause)
//        )
//    (
//      loggerF.debug(s"start ${ct.runtimeClass.getName} ${a.compactJson}")
//        *> wrappedEffect
//      )
//  }

  // ???
//  def logError(implicit loggerF: LoggerF, trace: Trace, causeHandler: CauseHandler[A]): ZStream[R,E,Nothing] =
//    effect
//      .onError(cause =>
//        causeHandler.prepareForLogs(cause) match {
//          case Right(th) =>
//            loggerF.warn("logging and passing through error", th)
//          case Left(msg) =>
//            loggerF.warn(s"logging and passing through error -- ${msg}")
//        }
//      )

}
