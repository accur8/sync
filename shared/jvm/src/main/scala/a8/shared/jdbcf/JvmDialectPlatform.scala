package a8.shared.jdbcf

import sttp.model.Uri

trait JvmDialectPlatform {

  def apply(jdbcUri: Uri): Option[Dialect] = {
    val schemaParts = jdbcUri.toString.split(":/").head.split(":").toList
    schemaParts match {
      case "jdbc" :: "as400" :: _ =>
        Some(ISeriesDialect)
      case "jdbc" :: _ :: "as400" :: _ =>
        Some(ISeriesDialect)
      case _ =>
        None
    }
  }
}
