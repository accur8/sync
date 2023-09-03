package a8.shared.app


import a8.shared.SharedImports.*
import cats.Monad
import zio.{LogLevel as _, *}
import a8.common.logging.{Logger, LoggerFactory, Level as LogLevel}

object LoggerF {

  object impl {

    /**
     * set log level so that TRACE works from slf4j since when using java.util.logging as the
     * underlying logger slf4j treats trait as jul.Level.FINEST where wvlet treats it as jul.Level.FINER
     */
    def setLogLevel(loggerName: String, level: LogLevel): Unit = {
      val ll = if ( level equals LogLevel.Trace ) LogLevel.All else level
      LoggerFactory.logger(loggerName).setLevel(ll)
    }

    val logLevelMap: Map[LogLevel, zio.LogLevel] =
      Map(
        LogLevel.Debug -> zio.LogLevel.Debug,
        LogLevel.Error -> zio.LogLevel.Error,
        LogLevel.Info -> zio.LogLevel.Info,
        LogLevel.Trace -> zio.LogLevel.Trace,
        LogLevel.Warn -> zio.LogLevel.Warning,
        LogLevel.All -> zio.LogLevel.All,
        LogLevel.Off -> zio.LogLevel.None,
      )

    val zioLogLevelMap: Map[zio.LogLevel,LogLevel] =
      logLevelMap
        .map(t => t._2 -> t._1)

    @inline def fromZioLogLevel(zioLogLevel: zio.LogLevel): LogLevel =
      zioLogLevelMap(zioLogLevel)

    @inline def toZioLogLevel(logLevel: LogLevel): zio.LogLevel =
      logLevelMap(logLevel)

  }

  def wrap(logger: Logger): LoggerF =
    LoggerF.create(logger)

  def create(implicit fullName: sourcecode.FullName): LoggerF = {
    val loggerName = fullName.value.split("\\.").dropRight(1).mkString(".")
    create(LoggerFactory.logger(loggerName))
  }

  def create(delegate: Logger): LoggerF = {
    new LoggerF {

      override protected def isEnabled(logLevel: LogLevel): Boolean =
        delegate.isLevelEnabled(logLevel)


      override def log(logLevel: LogLevel, message: String, cause: Option[Throwable])(implicit trace: Trace): UIO[Unit] = {
        val zioLogLevel = LoggerF.impl.toZioLogLevel(logLevel)
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

      override def log(logLevel: LogLevel, message: String, cause: Cause[Any])(implicit trace: Trace): UIO[Unit] = {
        val zioLogLevel = LoggerF.impl.toZioLogLevel(logLevel)
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
trait LoggerF {

  protected def isEnabled(logLevel: LogLevel): Boolean
  def log(logLevel: LogLevel, message: String, cause: Option[Throwable])(implicit trace: Trace): UIO[Unit]
  def log(logLevel: LogLevel, message: String, cause: Cause[Any])(implicit trace: Trace): UIO[Unit]

  def error(message: String)(implicit trace: Trace): UIO[Unit] = {
    if ( isEnabled(LogLevel.Error) )
      log(LogLevel.Error, message, None)
    else
      ZIO.unit
  }

  def error(message: String, cause: Throwable)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.Error) )
      log(LogLevel.Error, message, Some(cause))
    else
      ZIO.unit

  def error(message: String, cause: zio.Cause[Any])(implicit trace: Trace): UIO[Unit] = {
    if ( isEnabled(LogLevel.Error) )
      log(LogLevel.Error, message, cause)
    else
      ZIO.unit
  }

  def warn(message: String)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.Warn) )
      log(LogLevel.Warn, message, None)
    else
      ZIO.unit

  def warn(message: String, cause: Throwable)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.Warn) )
      log(LogLevel.Warn, message, Some(cause))
    else
      ZIO.unit

  def warn(message: String, cause: zio.Cause[Any])(implicit trace: Trace): UIO[Unit] = {
    if ( isEnabled(LogLevel.Warn) )
      log(LogLevel.Warn, message, cause)
    else
      ZIO.unit
  }

  def info(message: String)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.Info) )
      log(LogLevel.Info, message, None)
    else
      ZIO.unit

  def info(message: String, cause: Throwable)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.Info) )
      log(LogLevel.Info, message, Some(cause))
    else
      ZIO.unit

  def info(message: String, cause: zio.Cause[Any])(implicit trace: Trace): UIO[Unit] = {
    if ( isEnabled(LogLevel.Info) )
      log(LogLevel.Info, message, cause)
    else
      ZIO.unit
  }

  def debug(message: String)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.Debug) )
      log(LogLevel.Debug, message, None)
    else
      ZIO.unit

  def debug(message: String, cause: zio.Cause[Any])(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.Debug) )
      log(LogLevel.Debug, message, cause)
    else
      ZIO.unit

  def debug(message: String, cause: Throwable)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.Debug) )
      log(LogLevel.Debug, message, Some(cause))
    else
      ZIO.unit

  def trace(message: String)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.Trace) )
      log(LogLevel.Trace, message, None)
    else
      ZIO.unit

  def trace(message: String, cause: Throwable)(implicit trace: Trace): UIO[Unit] =
    if ( isEnabled(LogLevel.Trace) )
      log(LogLevel.Trace, message, Some(cause))
    else
      ZIO.unit

  def trace(message: String, cause: zio.Cause[Any])(implicit trace: Trace): UIO[Unit] = {
    if ( isEnabled(LogLevel.Trace) )
      log(LogLevel.Trace, message, cause)
    else
      ZIO.unit
  }

}
