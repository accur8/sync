package a8.shared.jdbcf


import a8.shared.SharedImports._
import a8.shared.jdbcf.Conn.impl
import sttp.model.Uri

import javax.sql.DataSource

/**
 * Factory companion object for creating database connection factories.
 * 
 * This object provides methods to create [[ConnFactory]] instances from various
 * configuration sources. It extends platform-specific implementations to support
 * different database systems.
 * 
 * @example
 * {{{
 * val config = DatabaseConfig(
 *   jdbcUrl = Uri.parse("jdbc:postgresql://localhost/mydb"),
 *   username = "user",
 *   password = "pass".asSecretValue
 * )
 * val connFactory = ConnFactory.create(config)
 * }}}
 */
object ConnFactory extends ConnFactoryPlatform with ConnFactoryCompanion {
}

/**
 * Factory for creating and managing database connections with connection pooling.
 * 
 * ConnFactory provides a high-level interface for obtaining database connections
 * through a connection pool (typically HikariCP). It manages the lifecycle of
 * connections and ensures proper resource cleanup.
 * 
 * == Connection Management ==
 * 
 * Connections are managed through ZIO's Resource type, ensuring automatic cleanup:
 * {{{
 * connFactory.connR.use { conn =>
 *   // Use the connection
 *   conn.query(sql"SELECT * FROM users")
 * }
 * }}}
 * 
 * == Thread Safety ==
 * 
 * ConnFactory implementations are thread-safe and can be shared across multiple
 * threads. The underlying connection pool handles concurrent access.
 * 
 * == Lifecycle ==
 * 
 * The factory should be closed when no longer needed to release all pooled connections:
 * {{{
 * try {
 *   // Use the factory
 * } finally {
 *   connFactory.safeClose()
 * }
 * }}}
 * 
 * @see [[DatabaseConfig]] for configuration options
 * @see [[Conn]] for connection operations
 */
trait ConnFactory {

  /**
   * The database configuration used by this factory.
   * 
   * This includes connection parameters, pool settings, and database-specific options.
   */
  val config: DatabaseConfig
  
  /**
   * Creates a managed database connection resource.
   * 
   * The returned Resource ensures the connection is properly returned to the pool
   * after use. Connections are automatically committed or rolled back based on
   * the success or failure of the operation.
   * 
   * @return A ZIO Resource that provides a [[Conn]] instance
   * 
   * @example
   * {{{
   * connFactory.connR.use { conn =>
   *   for {
   *     users <- conn.query(sql"SELECT * FROM users")
   *     _ <- conn.update(sql"UPDATE users SET last_login = NOW() WHERE id = \${userId}")
   *   } yield users
   * }
   * }}}
   */
  def connR: zio.Resource[Conn]
  
  /**
   * Safely closes this factory and releases all resources.
   * 
   * This method closes the underlying connection pool and ensures all connections
   * are properly returned. It is safe to call multiple times.
   * 
   * @note This should be called when the application shuts down or when the
   *       factory is no longer needed
   */
  def safeClose(): Unit

}
