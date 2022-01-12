package a8.shared.jdbcf


import a8.shared.SharedImports._
import a8.shared.jdbcf.Conn.impl
import sttp.model.Uri

import javax.sql.DataSource


object ConnFactory extends ConnFactoryPlatform with ConnFactoryImpl {
}

trait ConnFactory[F[_]] {

  val config: DatabaseConfig
  def connR: Resource[F,Conn[F]]

}
