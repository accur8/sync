package a8.shared.app

import a8.shared.SharedImports.Sync
import a8.shared.app.LoggerF.{Pos}
import cats.Monad
import wvlet.log.{LogEnv, LogLevel, LogRecord, LogSource, Logger}


object LoggerF {

  object Pos {
    implicit def implicitPos(
      implicit
        file: sourcecode.File,
        fileName: sourcecode.FileName,
        line: sourcecode.Line,
    ): Pos =
      Pos(file, fileName, line)

  }

  case class Pos(
    file: sourcecode.File,
    fileName: sourcecode.FileName,
    line: sourcecode.Line,
  ) {
    def asLogSource = LogSource(file.value, fileName.value, line.value, 0)
  }

  def wrap[F[_] : Sync](logger: Logger): LoggerF[F] =
    LoggerF.create[F](logger)

  def create[F[_] : Sync](implicit fullName: sourcecode.FullName): LoggerF[F] = {
    val loggerName = fullName.value.split("\\.").dropRight(1).mkString(".")
    create(Logger(loggerName))
  }

  def create[F[_] : Sync](delegate: Logger): LoggerF[F] = {
    new LoggerF[F] {

      override protected def isEnabled(logLevel: LogLevel): Boolean =
        delegate.isEnabled(logLevel)

      override protected def impl(logLevel: LogLevel, message: String, cause: Option[Throwable], pos: Pos): F[Unit] = {
        Sync[F].blocking {
          val logRecord = LogRecord(logLevel, Some(pos.asLogSource), message, cause)
          delegate.log(logRecord)
        }
      }

    }
  }

}

/**
 *
 * many of these methods are implemented to perform well as distinct from being as concise / DRY as possible
 *
 */
abstract class LoggerF[F[_] : Monad] {

  val F = Monad[F]

  protected def isEnabled(logLevel: LogLevel): Boolean
  protected def impl(logLevel: LogLevel, message: String, cause: Option[Throwable], pos: Pos): F[Unit]

  def error(message: String)(implicit pos: Pos): F[Unit] = {
    if ( isEnabled(LogLevel.ERROR) )
      impl(LogLevel.ERROR, message, None, pos)
    else
      Monad[F].unit
  }

  def error(message: String, cause: Throwable)(implicit pos: Pos): F[Unit] =
    if ( isEnabled(LogLevel.ERROR) )
      impl(LogLevel.ERROR, message, Some(cause), pos)
    else
      F.unit

  def warn(message: String)(implicit pos: Pos): F[Unit] =
    if ( isEnabled(LogLevel.WARN) )
      impl(LogLevel.WARN, message, None, pos)
    else
      F.unit

  def warn(message: String, cause: Throwable)(implicit pos: Pos): F[Unit] =
    if ( isEnabled(LogLevel.WARN) )
      impl(LogLevel.WARN, message, Some(cause), pos)
    else
      F.unit

  def info(message: String)(implicit pos: Pos): F[Unit] =
    if ( isEnabled(LogLevel.INFO) )
      impl(LogLevel.INFO, message, None, pos)
    else
      F.unit

  def info(message: String, cause: Throwable)(implicit pos: Pos): F[Unit] =
    if ( isEnabled(LogLevel.INFO) )
      impl(LogLevel.INFO, message, Some(cause), pos)
    else
      F.unit

  def debug(message: String)(implicit pos: Pos): F[Unit] =
    if ( isEnabled(LogLevel.DEBUG) )
      impl(LogLevel.DEBUG, message, None, pos)
    else
      F.unit

  def debug(message: String, cause: Throwable)(implicit pos: Pos): F[Unit] =
    if ( isEnabled(LogLevel.DEBUG) )
      impl(LogLevel.DEBUG, message, Some(cause), pos)
    else
      F.unit

  def trace(message: String)(implicit pos: Pos): F[Unit] =
    if ( isEnabled(LogLevel.TRACE) )
      impl(LogLevel.TRACE, message, None, pos)
    else
      F.unit

  def trace(message: String, cause: Throwable)(implicit pos: Pos): F[Unit] =
    if ( isEnabled(LogLevel.TRACE) )
      impl(LogLevel.TRACE, message, Some(cause), pos)
    else
      F.unit

}
