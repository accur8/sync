package a8.shared


import a8.shared.app.LoggerF
import a8.shared.json.JsonCodec
import zio._
import zio.stream.ZStream

import scala.reflect.ClassTag
import SharedImports.jsonCodecOps

import java.util.UUID

class ZioOps[R, E, A](effect: zio.ZIO[R,E,A])(implicit trace: Trace) {

  def toLayer(implicit tag: Tag[A]): ZLayer[R, E, A] = ZLayer(effect)

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

  final def asZIO[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, B] =
    effect.flatMap(_ => that)

  def correlateWith0(context: String, details: Option[String] = None)(implicit trace: Trace, loggerF: LoggerF): zio.ZIO[R, Nothing, Unit] = {

    import java.lang.System

    val wrappedJob =
      for {
        _ <- loggerF.debug(s"job started - ${context}${details.map(" - " + _).getOrElse("")}")
        started = System.currentTimeMillis()
        result <- effect *> loggerF.debug("")
        _ <- loggerF.debug(s"job completed in ${System.currentTimeMillis() - started}ms - ${context}")
      } yield result

    val wrappedJobWithErrorsLogged: zio.ZIO[R, Nothing, Unit] =
      wrappedJob
        .catchAllCause(cause =>
          loggerF.debug(s"job error will get rethrown - ${context}", cause)
        )
        .as(())

    for {
      _ <- zio.ZIO.unit
      jobId = UUID.randomUUID().toString.replace("-", "").substring(0, 12)
      result <- zio.ZIO.logAnnotate("job", jobId)(wrappedJobWithErrorsLogged)
    } yield result

  }

  def correlateWith(context: String, details: Option[String] = None)(implicit trace: Trace, loggerF: LoggerF): zio.ZIO[R, E, A] = {

    import java.lang.System

    val wrappedJob =
      for {
        _ <- loggerF.debug(s"job started - ${context}${details.map(" - " + _).getOrElse("")}")
        started = System.currentTimeMillis()
        result <- effect
        _ <- loggerF.debug(s"job completed in ${System.currentTimeMillis() - started}ms - ${context}")
      } yield result

    val wrappedJobWithErrorsLogged =
      wrappedJob
        .onError(cause =>
          loggerF.warn(s"job error will get rethrown - ${context}", cause)
        )

    for {
      _ <- zio.ZIO.unit
      jobId = UUID.randomUUID().toString.replace("-", "").substring(0, 12)
      result <- zio.ZIO.logAnnotate("job", jobId)(wrappedJobWithErrorsLogged)
    } yield result
  }


  /**
   * wraps the effect to log the start of the effect and
   * it's success value and/or its error value
   */
  def trace(context: String)(implicit loggerF: LoggerF, trace: Trace): ZIO[R, E, A] =
    loggerF.trace(s"start ${context}")
      .flatMap(_ => effect)
      .flatMap { v =>
        loggerF.trace(s"success ${context} -- ${v}")
          .as(v)
      }
      .onError { cause =>
        loggerF.trace(s"error ${context}", cause)
      }

  /**
   * wraps the effect to log the start of the effect and
   * it's success without the value and/or its error value
   */
  def trace0(context: String)(implicit loggerF: LoggerF, trace: Trace): ZIO[R, E, A] =
    loggerF.trace(s"start ${context}")
      .flatMap(_ => effect)
      .flatMap { v =>
        loggerF.trace(s"success ${context}")
          .as(v)
      }
      .onError { cause =>
        loggerF.trace(s"error ${context}", cause)
      }

  /**
   * wraps the effect to log the start of the effect and
   * it's success value (as compact json) and/or its error value
   */
  def jsonTrace(implicit jsonCodec: JsonCodec[A], loggerF: LoggerF, trace: Trace): zio.ZIO[R,E,A] =
    jsonTraceC("")

  /**
   * wraps the effect to log context and the start of the effect and
   * it's success value (as compact json) and/or its error value
   */
  def jsonTraceC(context: String)(implicit jsonCodec: JsonCodec[A], loggerF: LoggerF, trace: Trace): zio.ZIO[R, E, A] =
    loggerF.trace(s"start ${context}")
      .flatMap(_ => effect)
      .flatMap { v =>
        loggerF.trace(s"success ${context} -- ${v.compactJson}")
          .as(v)
      }
      .onError { cause =>
        loggerF.trace(s"error ${context}", cause)
      }

}
