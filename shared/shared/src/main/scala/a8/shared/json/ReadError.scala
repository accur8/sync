package a8.shared.json

import a8.shared.json.ReadError.{ContextedReadError, ParseError, ReadErrorException, SingleReadError}
import a8.shared.json.ast.{JsDoc, JsNothing}

object ReadError {

  case class SourceNotFoundError(message: String) extends ReadError
  case class UnexpectedException(exception: Throwable) extends ReadError
  case class ParseError(message: String) extends ReadError
  case class SingleReadError(message: String, jsDoc: JsDoc) extends ReadError
  case class ContextedReadError(sourceContext: String, readError: ReadError) extends ReadError

  case class ReadErrorException(readError: ReadError) extends Exception(readError.prettyMessage)

  given CanEqual[ReadError,ReadError] = CanEqual.derived

}


sealed trait ReadError {

  def withContext(context: Option[String]): ReadError =
    context match {
      case Some(ctx) =>
        ContextedReadError(ctx, this)
      case None =>
        this
    }

  def asException: ReadErrorException = ReadErrorException(this)

  def prettyMessage: String = {
    this match {
      case ReadError.ContextedReadError(ctx, readError) =>
        val details =
          readError
            .prettyMessage
            .linesIterator.map("    " + _)
            .mkString("\n")
        ctx + "\n" + details
      case ReadError.SourceNotFoundError(ctx) =>
        s"source not found -- ${ctx}"
      case ue: ReadError.UnexpectedException =>
        Option(ue.exception.getMessage)
          .getOrElse(ue.exception.getClass.getName)
      case pe: ParseError =>
        pe.message
      case re: SingleReadError =>

        val compactJson =
          re.jsDoc.value match {
            case JsNothing =>
              "nothing"
            case _ =>
              re.jsDoc.compactJson
          }

        s"""${re.message}
           |    Path: ${re.jsDoc.path}
           |    Found: ${compactJson}""".stripMargin
    }
  }

}
