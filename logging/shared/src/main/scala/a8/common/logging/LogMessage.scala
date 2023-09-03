package a8.common.logging


import a8.common.logging.LogMessage.impl.LogPart.LogMessageImplicits
import a8.common.logging.LogMessage.impl.ResolvedLogMessage

import scala.collection.mutable
import scala.language.implicitConversions

object LogMessage {

  object impl {

    sealed trait LogPart

    object LogPart {

      case class Message(value: String) extends LogPart

      case class Throw(th: Throwable) extends LogPart

      case class ConsoleValue(value: Any) extends LogPart

      case class JsonConsoleValue[A: JsonApi](value: A) extends LogPart {
        def asJsonStr = JsonApi[A].toJsonStr(value)
      }

      case class Expr[A](value: sourcecode.Text[A]) extends LogPart {
        def asString = value.source + " = " + value.value.toString
      }

      case class ConsoleMessage(value: String) extends LogPart


      object LogMessageImplicits {
        implicit def implicitSupplier(
          implicit
            filename: sourcecode.File,
            line: sourcecode.Line,
            fullName: sourcecode.FullName,
        ): LogMessageImplicits = {
          new LogMessageImplicits(
            filename,
            line,
            fullName,
          )
        }

      }

      case class LogMessageImplicits(
        file: sourcecode.File,
        line: sourcecode.Line,
        fullName: sourcecode.FullName,
      ) extends LogPart {

        def filename: String = {
          file.value.lastIndexOf("/") match {
            case -1 =>
              file.value
            case i =>
              file.value.substring(i+1)
          }
        }
      }

    }


    case class ResolvedLogMessage(
      consoleMessage: Option[String],
      message: String,
      throwable: Option[Throwable],
      consoleValues: Seq[Any],
    )


    case class LogMessageInternal(
      parts: List[LogPart],
    ) extends LogMessage {

      def message(value: String): LogMessage =
        copy(parts = LogPart.Message(value) :: parts)

      def throwable(th: Throwable): LogMessage =
        copy(parts = LogPart.Throw(th) :: parts)

      def exception(th: Throwable): LogMessage =
        copy(parts = LogPart.Throw(th) :: parts)

      def consoleValue(value: Any): LogMessage =
        copy(parts = LogPart.ConsoleValue(value) :: parts)

      def expr[A](value: sourcecode.Text[A]): LogMessage =
        copy(parts = LogPart.Expr(value) :: parts)

      def jsonConsoleValue[A: JsonApi](value: A): LogMessage =
        copy(parts = LogPart.JsonConsoleValue(value) :: parts)

      def consoleMessage(message: String): LogMessage =
        copy(parts = LogPart.ConsoleMessage(message) :: parts)

      def resolve: ResolvedLogMessage = {

        // completely imperitive code for optimal performance
        var message0: String = null
        var throwable0: Throwable = null
        var consoleValues0: mutable.Buffer[Any] = null
        var consoleMessage0: String = null

        def appendMessage(message: String) = {
          if ( message0 == null )
            message0 = message
          else
            message0 = message0 + " - " + message
        }

        parts.reverse.foreach {
          case p: LogPart.LogMessageImplicits =>
            appendMessage(p.filename + ":" + p.line.value)
            appendMessage(p.fullName.value)
          case p: LogPart.Message =>
            appendMessage(p.value)
          case p: LogPart.ConsoleMessage =>
            consoleMessage0 = p.value
          case p: LogPart.ConsoleValue =>
            if ( consoleValues0 == null )
              consoleValues0 = mutable.Buffer[Any]()
            consoleValues0 += p.value
          case p: LogPart.JsonConsoleValue[_] =>
            if ( consoleValues0 == null )
              consoleValues0 = mutable.Buffer[Any]()
            consoleValues0 += p
          case p: LogPart.Throw =>
            throwable0 = p.th
          case p: LogPart.Expr[_] =>
            appendMessage("(" + p.asString + ")")
        }

        ResolvedLogMessage(
          message = message0,
          consoleMessage = Option(consoleMessage0),
          throwable = Option(throwable0),
          consoleValues = if ( consoleValues0 == null ) Seq.empty else consoleValues0.toSeq
        )

      }

    }

  }

  def create(
    implicit
      logMessageImplicits: LogMessageImplicits,
  ): LogMessage =
    impl.LogMessageInternal(
      parts = List(logMessageImplicits),
    )


  def apply(
    message: String
  )(
    implicit
      logMessageImplicits: LogMessageImplicits,
  ): LogMessage =
    impl.LogMessageInternal(
      impl.LogPart.Message(message) :: logMessageImplicits :: Nil,
    )

}


trait LogMessage {

  def message(value: String): LogMessage
  def throwable(th: Throwable): LogMessage
  def exception(th: Throwable): LogMessage
  def consoleValue(value: Any): LogMessage

  def expr[A](value: sourcecode.Text[A]): LogMessage

  /**
   * logs the json representation of the value
   * @param value
   * @return
   */
  def jsonConsoleValue[A : JsonApi](value: A): LogMessage
  def consoleMessage(message: String): LogMessage

  def resolve: ResolvedLogMessage

}
