package a8.shared.jdbcf

import sttp.model.Uri

trait JsDialectPlatform {

  def apply(jdbcUri: Uri): Option[Dialect] = None

}
