package a8.shared.app


import a8.shared.SharedImports._
import a8.shared.app.LoggerF.Pos
import cats.Monad
import wvlet.log.{LogLevel, LogRecord, LogSource, Logger}
import zio.{LogLevel => _, _}

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

  def wrap(logger: Logger): LoggerF =
    LoggerF.create(logger)

  def create(implicit fullName: sourcecode.FullName): LoggerF = {
    val loggerName = fullName.value.split("\\.").dropRight(1).mkString(".")
    create(Logger(loggerName))
  }

  def create(delegate: Logger): LoggerF = {
    new LoggerF {

      val logLevelMap: Map[LogLevel, zio.LogLevel] =
        Map(
          wvlet.log.LogLevel.DEBUG -> zio.LogLevel.Debug,
          wvlet.log.LogLevel.ERROR -> zio.LogLevel.Error,
          wvlet.log.LogLevel.INFO -> zio.LogLevel.Info,
          wvlet.log.LogLevel.TRACE -> zio.LogLevel.Trace,
          wvlet.log.LogLevel.WARN -> zio.LogLevel.Warning,
          wvlet.log.LogLevel.ALL -> zio.LogLevel.All,
          wvlet.log.LogLevel.OFF -> zio.LogLevel.None,
        )

      @inline def toZioLogLevel(logLevel: LogLevel): zio.LogLevel =
        logLevelMap(logLevel)

      override protected def isEnabled(logLevel: LogLevel): Boolean =
        delegate.isEnabled(logLevel)


      override protected def impl(logLevel: LogLevel, message: String, cause: Option[Throwable])(implicit trace: Trace): UIO[Unit] = {
        val zioLogLevel = toZioLogLevel(logLevel)
        ZIO.logLevel(zioLogLevel) {
          val causeSuffix =
            cause match {
              case None =>
                ""
              case Some(th) =>
                "\n" + th.stackTraceAsString.indent("        ")
            }
          ZIO.log(message + causeSuffix)
        }
      }

      override protected def impl(logLevel: LogLevel, message: String, cause: Cause[Any])(implicit trace: Trace): UIO[Unit] = {
        val zioLogLevel = toZioLogLevel(logLevel)
        ZIO.logLevel(zioLogLevel) {
          ZIO.logCause(message, cause)
        }
      }
//        ZIO
//          .attemptBlocking {
//            val logRecord = LogRecord(logLevel, Some(pos.asLogSource), message + "\n" + cause.prettyPrint.indent("    ") , None)
//            delegate.log(logRecord)
//          }
//          .catchAll(_ => ZIO.unit)
    }
  }

}

/**
 *
 * many of these methods are implemented to perform well as distinct from being as concise / DRY as possible
 *
 */
abstract class LoggerF {

  import wvlet.log.LogLevel

  protected def isEnabled(logLevel: LogLevel): Boolean
  protected def impl(logLevel: LogLevel, message: String, cause: Option[Throwable])(implicit trace: Trace): UIO[Unit]
  protected def impl(logLevel: LogLevel, message: String, cause: Cause[Any])(implicit trace: Trace): UIO[Unit]

  def error(message: String)(implicit trace: Trace): UIO[Unit] = {
    if ( isEnabled(LogLevel.ERROR) )
      impl(LogLevel.ERROR, message, None)
    else
      ZIO.unit
  }

  def error(message: String, cause: Throwable)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.ERROR) )
      impl(LogLevel.ERROR, message, Some(cause))
    else
      ZIO.unit

  def error(message: String, cause: zio.Cause[Any])(implicit trace: Trace): UIO[Unit] = {
    if ( isEnabled(LogLevel.ERROR) )
      impl(LogLevel.ERROR, message, cause)
    else
      ZIO.unit
  }

  def warn(message: String)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.WARN) )
      impl(LogLevel.WARN, message, None)
    else
      ZIO.unit

  def warn(message: String, cause: Throwable)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.WARN) )
      impl(LogLevel.WARN, message, Some(cause))
    else
      ZIO.unit

  def warn(message: String, cause: zio.Cause[Any])(implicit trace: Trace): UIO[Unit] = {
    if ( isEnabled(LogLevel.WARN) )
      impl(LogLevel.WARN, message, cause)
    else
      ZIO.unit
  }

  def info(message: String)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.INFO) )
      impl(LogLevel.INFO, message, None)
    else
      ZIO.unit

  def info(message: String, cause: Throwable)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.INFO) )
      impl(LogLevel.INFO, message, Some(cause))
    else
      ZIO.unit

  def info(message: String, cause: zio.Cause[Any])(implicit trace: Trace): UIO[Unit] = {
    if ( isEnabled(LogLevel.INFO) )
      impl(LogLevel.INFO, message, cause)
    else
      ZIO.unit
  }

  def debug(message: String)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.DEBUG) )
      impl(LogLevel.DEBUG, message, None)
    else
      ZIO.unit

  def debug(message: String, cause: zio.Cause[Any])(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.DEBUG) )
      impl(LogLevel.DEBUG, message, cause)
    else
      ZIO.unit

  def debug(message: String, cause: Throwable)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.DEBUG) )
      impl(LogLevel.DEBUG, message, Some(cause))
    else
      ZIO.unit

  def trace(message: String)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.TRACE) )
      impl(LogLevel.TRACE, message, None)
    else
      ZIO.unit

  def trace(message: String, cause: Throwable)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.TRACE) )
      impl(LogLevel.TRACE, message, Some(cause))
    else
      ZIO.unit

  def trace(message: String, cause: zio.Cause[Any])(implicit trace: Trace): UIO[Unit] = {
    if ( isEnabled(LogLevel.TRACE) )
      impl(LogLevel.TRACE, message, cause)
    else
      ZIO.unit
  }

}
