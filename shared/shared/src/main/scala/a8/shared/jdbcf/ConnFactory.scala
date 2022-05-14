package a8.shared.jdbcf


import a8.shared.SharedImports._
import a8.shared.jdbcf.Conn.impl
import sttp.model.Uri

import javax.sql.DataSource
import zio._

object ConnFactory extends ConnFactoryPlatform with ConnFactoryImpl {
}

trait ConnFactory {

  val config: DatabaseConfig
  def connR: ZIO[Scope,Throwable,Conn]

}
