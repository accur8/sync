package a8.shared.jdbcf

import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import a8.shared.{CompanionGen, StringValue}
import a8.shared.jdbcf.MxDatabaseConfig.MxDatabaseConfig
import org.typelevel.ci.CIString
import sttp.model.Uri


object DatabaseConfig extends MxDatabaseConfig {
  object DatabaseId extends StringValue.CIStringValueCompanion[DatabaseId]
  case class DatabaseId(value: CIString) extends StringValue.CIStringValue
}
@CompanionGen
case class DatabaseConfig(
  id: DatabaseId,
  url: Uri,
  user: String,
  password: String,
  minIdle: Int = 1,
  maxPoolSize: Int = 50,
//  dialect: Dialect = Dialect.Default,
  autoCommit: Boolean = true,
)