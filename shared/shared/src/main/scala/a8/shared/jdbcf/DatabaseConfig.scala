package a8.shared.jdbcf

import a8.shared.jdbcf.DatabaseConfig.{DatabaseId, Password}
import a8.shared.{CompanionGen, SecretValue, StringValue}
import a8.shared.jdbcf.MxDatabaseConfig.MxDatabaseConfig
import org.typelevel.ci.CIString
import sttp.model.Uri


object DatabaseConfig extends MxDatabaseConfig {
  object DatabaseId extends StringValue.CIStringValueCompanion[DatabaseId]
  case class DatabaseId(value: CIString) extends StringValue.CIStringValue

  object Password extends SecretValue.Companion[Password]
  case class Password(value: String) extends SecretValue
}

@CompanionGen
case class DatabaseConfig(
  id: DatabaseId,
  url: Uri,
  user: String,
  password: Password,
  minIdle: Int = 1,
  maxPoolSize: Int = 50,
  maxLifeTimeInMillis: Option[Int] = None,
  autoCommit: Boolean = true,
)
