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

/**
 * Configuration for database connections with connection pooling support.
 * 
 * This configuration is used to create a [[ConnFactory]] which manages database
 * connections through HikariCP connection pooling.
 * 
 * @param id Unique identifier for this database configuration
 * @param url JDBC connection URL (e.g., "jdbc:postgresql://localhost:5432/mydb")
 * @param user Database username for authentication
 * @param password Database password wrapped in a secure Password type
 * @param minIdle Minimum number of idle connections to maintain in the pool
 * @param maxPoolSize Maximum number of connections in the pool
 * @param maxLifeTimeInMillis Maximum lifetime of a connection in milliseconds (None = infinite)
 * @param autoCommit Whether connections should auto-commit (default: true)
 * 
 * @example {{{
 * val config = DatabaseConfig(
 *   id = DatabaseId("primary"),
 *   url = Uri.parse("jdbc:postgresql://localhost:5432/myapp"),
 *   user = "appuser",
 *   password = Password("secret"),
 *   maxPoolSize = 20
 * )
 * 
 * val connFactory = ConnFactory.constructor(config)
 * }}}
 * 
 * @since 1.0.0
 */
@CompanionGen
case class DatabaseConfig(
  id: DatabaseId,
  url: Uri,
  user: String,
  password: Password,
  minIdle: Int = 1,
  maxPoolSize: Int = 50,
  maxLifeTimeInMillis: Option[Long] = None,
  autoCommit: Boolean = true,
)
