package a8.shared.json

import a8.shared.json.JsonReadOptions.UnusedFieldAction


object JsonReadOptions {

  sealed trait UnusedFieldAction
  object UnusedFieldAction {
    case object Ignore extends UnusedFieldAction
    case object LogWarning extends UnusedFieldAction
    case object LogDebug extends UnusedFieldAction
    case object Fail extends UnusedFieldAction
  }

  implicit val default: JsonReadOptions =
    JsonReadOptions(UnusedFieldAction.LogWarning)

}


case class JsonReadOptions(unusedFieldAction: UnusedFieldAction)
