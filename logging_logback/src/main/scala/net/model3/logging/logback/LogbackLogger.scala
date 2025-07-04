package net.model3.logging.logback

import a8.common.logging.{Level, Logger, Trace}
import net.model3.logging.logback.LogbackLogger.TraceMarker
import org.slf4j.Marker
import a8.common.logging.LoggingOps.*

import java.util
import java.util.Collections

object LogbackLogger {

  case class TraceMarker(trace: Trace) extends Marker {

    val wrapper = trace.wrap

    override def getName: String = "trace"
    override def add(reference: Marker): Unit = ()
    override def remove(reference: Marker): Boolean = false
    override def hasChildren: Boolean = false
    override def hasReferences: Boolean = false
    override def iterator(): util.Iterator[Marker] = Collections.emptyIterator()
    override def contains(other: Marker): Boolean = false
    override def contains(name: String): Boolean = getName == name

    override def toString: String =
      s"(${wrapper.filename}:${wrapper.lineNo})"

  }

}

case class LogbackLogger(factory: LogbackLoggerFactory.type, delegate: ch.qos.logback.classic.Logger) extends Logger {

  override def log(level: Level, msg: String, th: Throwable)(implicit trace: Trace): Unit = {
    if ( isLevelEnabled(level) ) {
      val logbackLevel = factory.m3ToLogbackLevelIntMap(level)
      val traceMarker = TraceMarker(trace)
      delegate.log(traceMarker, null, logbackLevel, msg, null, th)
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
