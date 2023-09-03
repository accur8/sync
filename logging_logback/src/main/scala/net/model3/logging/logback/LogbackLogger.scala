package net.model3.logging.logback

import a8.common.logging.{Level, Logger}

case class LogbackLogger(factory: LogbackLoggerFactory.type, delegate: ch.qos.logback.classic.Logger) extends Logger {

  override def log(level: Level, msg: String, th: Throwable): Unit = {
    if ( isLevelEnabled(level) ) {
      val logbackLevel = factory.m3ToLogbackLevelIntMap(level)
      delegate.log(null, null, logbackLevel, msg, null, th)
    }
  }


  override val name: String =
    delegate.getName

  override def isLevelEnabled(level: Level): Boolean = {
    level match {
      case Level.Trace =>
        delegate.isTraceEnabled
      case Level.Debug =>
        delegate.isDebugEnabled
      case Level.Info =>
        delegate.isInfoEnabled
      case Level.Warn =>
        delegate.isWarnEnabled
      case Level.Fatal | Level.Error =>
        delegate.isErrorEnabled
      case Level.Off =>
        false
      case Level.All =>
        true
    }
  }

  override def setLevel(l: Level): Unit =
    delegate.setLevel(factory.logbackLevel(l))

}
