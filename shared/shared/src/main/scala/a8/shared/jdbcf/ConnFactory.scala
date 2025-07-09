package a8.shared.jdbcf


import a8.shared.SharedImports._
import a8.shared.jdbcf.Conn.impl
import sttp.model.Uri

import javax.sql.DataSource

object ConnFactory extends ConnFactoryPlatform with ConnFactoryCompanion {
}

trait ConnFactory {

  val config: DatabaseConfig
  def connR: zio.Resource[Conn]
  def safeClose(): Unit

}
