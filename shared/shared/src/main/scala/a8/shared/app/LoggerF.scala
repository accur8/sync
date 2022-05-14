package a8.shared.app


import a8.shared.app.Logger.LogLevel
import a8.shared.app.LoggerF.Pos
import cats.Monad
import zio._

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
//    def asLogSource = LogSource(file.value, fileName.value, line.value, 0)
  }

  def wrap(logger: Logger): LoggerF =
    LoggerF.create(logger)

  def create(implicit fullName: sourcecode.FullName): LoggerF = {
    val loggerName = fullName.value.split("\\.").dropRight(1).mkString(".")
    create(Logger.fromName(loggerName))
  }

  def create(delegate: Logger): LoggerF = {
    new LoggerF {

      override protected def isEnabled(logLevel: LogLevel): Boolean =
        delegate.isEnabled(logLevel)

      override protected def impl(logLevel: LogLevel, message: String, cause: Option[Throwable], pos: Pos): Task[Unit] = {
        ZIO.attemptBlocking {
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
abstract class LoggerF {

  protected def isEnabled(logLevel: LogLevel): Boolean
  protected def impl(logLevel: LogLevel, message: String, cause: Option[Throwable], pos: Pos): UIO[Unit]

  def error(message: String)(implicit pos: Pos): UIO[Unit] = {
    if ( isEnabled(LogLevel.ERROR) )
      impl(LogLevel.ERROR, message, None, pos)
    else
      ZIO.unit
  }

  def error(message: String, cause: Throwable)(implicit pos: Pos): UIO[Unit] =
    if ( isEnabled(LogLevel.ERROR) )
      impl(LogLevel.ERROR, message, Some(cause), pos)
    else
      ZIO.unit

  def warn(message: String)(implicit pos: Pos): UIO[Unit] =
    if ( isEnabled(LogLevel.WARN) )
      impl(LogLevel.WARN, message, None, pos)
    else
      ZIO.unit

  def warn(message: String, cause: Throwable)(implicit pos: Pos): UIO[Unit] =
    if ( isEnabled(LogLevel.WARN) )
      impl(LogLevel.WARN, message, Some(cause), pos)
    else
      ZIO.unit

  def info(message: String)(implicit pos: Pos): UIO[Unit] =
    if ( isEnabled(LogLevel.INFO) )
      impl(LogLevel.INFO, message, None, pos)
    else
      ZIO.unit

  def info(message: String, cause: Throwable)(implicit pos: Pos): UIO[Unit] =
    if ( isEnabled(LogLevel.INFO) )
      impl(LogLevel.INFO, message, Some(cause), pos)
    else
      ZIO.unit

  def debug(message: String)(implicit pos: Pos): UIO[Unit] =
    if ( isEnabled(LogLevel.DEBUG) )
      impl(LogLevel.DEBUG, message, None, pos)
    else
      ZIO.unit

  def debug(message: String, cause: Throwable)(implicit pos: Pos): UIO[Unit] =
    if ( isEnabled(LogLevel.DEBUG) )
      impl(LogLevel.DEBUG, message, Some(cause), pos)
    else
      ZIO.unit

  def trace(message: String)(implicit pos: Pos): UIO[Unit] =
    if ( isEnabled(LogLevel.TRACE) )
      impl(LogLevel.TRACE, message, None, pos)
    else
      ZIO.unit

  def trace(message: String, cause: Throwable)(implicit pos: Pos): UIO[Unit] =
    if ( isEnabled(LogLevel.TRACE) )
      impl(LogLevel.TRACE, message, Some(cause), pos)
    else
      ZIO.unit

}
