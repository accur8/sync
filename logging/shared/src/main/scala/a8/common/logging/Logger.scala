package a8.common.logging

import scala.reflect.{ClassTag, classTag}

object Logger {

  def of[A: ClassTag]: Logger =
    logger(classTag[A].runtimeClass)

  def logger(implicit name: sourcecode.FullName): Logger =
    LoggerFactory.logger(name.value)

  def logger(clazz: Class[_]): Logger =
    LoggerFactory.logger(normalizeClassname(clazz.getName))

}

trait Logger {

  def log(msg: String, th: Option[Throwable]): scala.Unit = {
    log(Level.Debug, msg, th.getOrElse(null))
  }

  def log(level: Level, msg: String = null, th: Throwable = null): Unit
  def name: String

  def log(level: Level, msg: LogMessage): Unit = {
    val resolvedMessage = msg.resolve
    log(level, resolvedMessage.message, resolvedMessage.throwable.getOrElse(null))
  }

  def trace(msg: String) = log(level=Level.Trace, msg = msg)
  def trace(msg: String, th: Throwable) = log(level=Level.Trace, msg = msg, th = th)
  def trace(th: Throwable) = log(level=Level.Trace, th = th)
  def trace(msg: LogMessage) = log(level=Level.Trace, msg = msg)
  final def trace(msg: ()=>String, th: Throwable = null) =
    if ( isTraceEnabled ) log(level=Level.Trace, msg = msg(), th = th)

  def debug(msg: String) = log(level=Level.Debug, msg = msg)
  def debug(msg: String, th: Throwable) = log(level=Level.Debug, msg = msg, th = th)
  def debug(th: Throwable) = log(level=Level.Debug, th = th)
  def debug(msg: LogMessage) = log(level=Level.Debug, msg = msg)
  final def debug(msg: ()=>String, th: Throwable = null) =
    if ( isDebugEnabled ) log(level=Level.Debug, msg = msg(), th = th)

  def info(msg: String) = log(level=Level.Info, msg = msg)
  def info(msg: String, th: Throwable) = log(level=Level.Info, msg = msg, th = th)
  def info(th: Throwable) = log(level=Level.Info, th = th)
  def info(msg: LogMessage) = log(level=Level.Info, msg = msg)
  final def info(msg: ()=>String, th: Throwable = null) =
    if ( isInfoEnabled ) log(level=Level.Info, msg = msg(), th = th)

  def warn(msg: String) = log(level=Level.Warn, msg = msg)
  def warn(msg: String, th: Throwable) = log(level=Level.Warn, msg = msg, th = th)
  def warn(th: Throwable) = log(level=Level.Warn, th = th)
  def warn(msg: LogMessage) = log(level=Level.Warn, msg = msg)
  final def warn(msg: ()=>String, th: Throwable = null) =
    if ( isWarnEnabled ) log(level=Level.Warn, msg = msg(), th = th)

  def error(msg: String) = log(level=Level.Error, msg = msg)
  def error(msg: String, th: Throwable) = log(level=Level.Error, msg = msg, th = th)
  def error(th: Throwable) = log(level=Level.Error, th = th)
  def error(msg: LogMessage) = log(level=Level.Error, msg = msg)
  final def error(msg: ()=>String, th: Throwable = null) =
    if ( isErrorEnabled ) log(level=Level.Error, msg = msg(), th = th)

  def fatal(msg: String) = log(level=Level.Fatal, msg = msg)
  def fatal(msg: String, th: Throwable) = log(level=Level.Fatal, msg = msg, th = th)
  def fatal(th: Throwable) = log(level=Level.Fatal, th = th)
  def fatal(msg: LogMessage) = log(level=Level.Fatal, msg = msg)
  final def fatal(msg: ()=>String, th: Throwable = null) =
    if ( isFatalEnabled ) log(level=Level.Fatal, msg = msg(), th = th)

  def isTraceEnabled = isLevelEnabled(Level.Trace)
  def isDebugEnabled = isLevelEnabled(Level.Debug)
  def isInfoEnabled = isLevelEnabled(Level.Info)
  def isWarnEnabled = isLevelEnabled(Level.Warn)
  def isErrorEnabled = isLevelEnabled(Level.Error)
  def isFatalEnabled = isLevelEnabled(Level.Fatal)
  def isLevelEnabled(level: Level): Boolean

  def lazyLog(level: Level, msg: =>String, th: Throwable = null) =
    if ( isLevelEnabled(level) ) log(level, msg, th)

  def lazyLog(level: Level, msg: =>LogMessage) =
    if ( isLevelEnabled(level) ) log(level, msg)

  def setLevel(l: Level): Unit

}
