package a8.shared.json


import a8.shared.json.JsonReadOptions.UnusedFieldAction
import a8.shared.json.ast.JsDoc
import a8.common.logging.Logger

object JsonReadOptions {

  case class UnusedFieldsInfo[A](
    doc: JsDoc,
    successFn: () => Either[ReadError, A],
    errorFn: () => Either[ReadError, A],
    unusedFields: Map[String, ast.JsVal],
    messageFn: () => String,
    logger: Logger,
  )

  trait UnusedFieldAction {
    def apply[A](unusedFieldsInfo: UnusedFieldsInfo[A])(implicit readOptions: JsonReadOptions): Either[ReadError, A]
  }
  object UnusedFieldAction {
    case object Ignore extends UnusedFieldAction {
      override def apply[A](unusedFieldsInfo: UnusedFieldsInfo[A])(implicit readOptions: JsonReadOptions): Either[ReadError, A] =
        unusedFieldsInfo.successFn()
    }
    case object LogWarning extends UnusedFieldAction {
      override def apply[A](unusedFieldsInfo: UnusedFieldsInfo[A])(implicit readOptions: JsonReadOptions): Either[ReadError, A] = {
        unusedFieldsInfo.logger.warn(unusedFieldsInfo.messageFn())
        unusedFieldsInfo.successFn()
      }
    }
    case object LogDebug extends UnusedFieldAction {
      override def apply[A](unusedFieldsInfo: UnusedFieldsInfo[A])(implicit readOptions: JsonReadOptions): Either[ReadError, A] = {
        unusedFieldsInfo.logger.debug(unusedFieldsInfo.messageFn())
        unusedFieldsInfo.successFn()
      }
    }
    case object Fail extends UnusedFieldAction {
      override def apply[A](unusedFieldsInfo: UnusedFieldsInfo[A])(implicit readOptions: JsonReadOptions): Either[ReadError, A] = {
        unusedFieldsInfo.errorFn()
      }
    }
  }

  implicit val default: JsonReadOptions =
    JsonReadOptions(UnusedFieldAction.LogWarning)

}


case class JsonReadOptions(unusedFieldAction: UnusedFieldAction)

