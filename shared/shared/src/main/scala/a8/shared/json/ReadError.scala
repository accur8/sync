package a8.shared.json

import a8.shared.json.ReadError.{ParseError, ReadErrorException, SingleReadError}
import a8.shared.json.ast.{JsDoc, JsNothing}

object ReadError {

  case class SourceNotFoundError(sourceContext: String) extends ReadError
  case class UnexpectedException(exception: Throwable) extends ReadError
  case class ParseError(message: String) extends ReadError
  case class SingleReadError(message: String, jsonDoc: JsDoc) extends ReadError

  case class ReadErrorException(readError: ReadError) extends Exception(readError.prettyMessage)

}


sealed trait ReadError {

  def asException = ReadErrorException(this)

  def prettyMessage: String = {
    this match {
      case ReadError.SourceNotFoundError(ctx) =>
        s"source not found -- ${ctx}"
      case ue: ReadError.UnexpectedException =>
        Option(ue.exception.getMessage)
          .getOrElse(ue.exception.getClass.getName)
      case re: ParseError =>
        re.message
      case re: SingleReadError =>

        val compactJson =
          re.jsonDoc.value match {
            case JsNothing =>
              "nothing"
            case _ =>
              re.jsonDoc.compactJson
          }

        s"""${re.message}
           |    Path: ${re.jsonDoc.path}
           |    Found: ${compactJson}""".stripMargin
    }
  }

}
