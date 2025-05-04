package net.model3.logging.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.encoder.Encoder
import ch.qos.logback.core.{AppenderBase, OutputStreamAppender}
import net.model3.logging.logback.ConsoleAppender.*
import ConsoleAppender.*
import a8.common.logging.{LoggingBootstrapConfig, LoggingBootstrapConfigServiceLoader}

import java.io.{FileDescriptor, FileOutputStream}
import scala.compiletime.uninitialized

object ConsoleAppender {
  sealed trait Kind
  object Kind {

    given CanEqual[Kind, Kind] = CanEqual.derived

    case object Stdout extends Kind
    case object Stderr extends Kind
    case object Daemon extends Kind

    lazy val default = {
      val btc = LoggingBootstrapConfigServiceLoader.loggingBootstrapConfig
      if (btc.hasColorConsole) {
        Kind.Stdout
      } else {
        Kind.Daemon
      }
    }
  }


}

class ConsoleAppender extends AppenderBase[ILoggingEvent] {

  var stdoutAppender: OutputStreamAppender[ILoggingEvent] = uninitialized
  var stderrAppender: OutputStreamAppender[ILoggingEvent] = uninitialized

  var encoder: Encoder[ILoggingEvent] = uninitialized

  val hasColorConsole = LoggingBootstrapConfigServiceLoader.loggingBootstrapConfig.hasColorConsole

  var botherLevel: Level = Level.WARN

  var kind: Kind = Kind.default

  def setBotherLevel(l: Level): Unit =
    this.botherLevel = l

  def setKind(kindStr: String): Unit = {
    this.kind =
      kindStr.trim.toLowerCase match {
        case "stdout" =>
          Kind.Stdout
        case "stderr" =>
          Kind.Stderr
        case "daemon" =>
          Kind.Daemon
        case "default" =>
          Kind.default
        case _ =>
          addWarn(s"invalid ConsoleAppender.kind ${kindStr} defaulting to ${Kind.default}")
          Kind.default
      }
  }

  override def start(): Unit = {
    def appender(name: String, fd: FileDescriptor): OutputStreamAppender[ILoggingEvent] = {
      val osa = new OutputStreamAppender[ILoggingEvent]
      osa.setName(name)
      osa.setContext(getContext)
      osa.setEncoder(encoder)
      osa.setOutputStream(new FileOutputStream(fd))
      osa.start()
      osa
    }

    stdoutAppender = appender("stdout", FileDescriptor.out)
    stderrAppender = appender("stderr", FileDescriptor.err)

    super.start()

  }

  def getEncoder: Encoder[ILoggingEvent] =
    this.encoder

  def setEncoder(encoder: Encoder[ILoggingEvent]): Unit =
    this.encoder = encoder

  override def append(eventObject: ILoggingEvent): Unit = {
    kind match {
      case Kind.Stdout =>
        stdoutAppender.doAppend(eventObject)
      case Kind.Stderr =>
        stderrAppender.doAppend(eventObject)
      case Kind.Daemon =>
        stdoutAppender.doAppend(eventObject)
        if ( eventObject.getLevel.isGreaterOrEqual(botherLevel) )
          stderrAppender.doAppend(eventObject)
    }
  }

}
