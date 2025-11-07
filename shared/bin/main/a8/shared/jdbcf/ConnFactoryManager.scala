package a8.shared.jdbcf

import a8.common.logging.Logging
import a8.shared.app.Ctx
import a8.shared.{CompanionGen, json}
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import scala.collection.mutable
import java.sql.Connection
import javax.sql.DataSource

trait DatabaseConfigProvider:
  def fetchConfig(databaseId: DatabaseId): Option[DatabaseConfig]

  /** noting that databases can be added / removed in realtime */
  def listCurrentDatabaseIds(): Iterable[DatabaseId]


object ConnFactoryManager:
  def load(filename: String)(using Ctx): ConnFactoryManager =
    import java.nio.file.{Files, Paths}

    val path = Paths.get(filename)
    if !Files.exists(path) then
      throw new IllegalArgumentException(s"Configuration file not found: $filename")

    try
      val content = Files.readString(path)
      val configs = json.unsafeRead[Iterable[DatabaseConfig]](content)
      fromConfigs(configs.toSeq)

    catch
      case e: Exception =>
        throw new IllegalArgumentException(s"Error loading configuration from $filename: ${e.getMessage}", e)

  def fromConfigs(configs: Seq[DatabaseConfig])(using Ctx): ConnFactoryManager =
    val configMap = configs.map(config => config.id -> config).toMap
    val configProvider = new DatabaseConfigProvider:
      def fetchConfig(databaseId: DatabaseId): Option[DatabaseConfig] =
        configMap.get(databaseId)

      def listCurrentDatabaseIds(): Iterable[DatabaseId] =
        configMap.keys

    new ConnFactoryManager(configProvider)

/**
 * Manages database connections using HikariCP connection pooling.
 * Creates and maintains connection pools for each database configuration
 * from the DatabaseConfigProvider.
 *
 * Connection pools are created lazily on first access and cached for reuse.
 * Pools can be closed individually or all at once via the close() method.
 */
class ConnFactoryManager(configProvider: DatabaseConfigProvider)(using Ctx)
  extends AutoCloseable
  with Logging:

  private val connFactories = mutable.Map[DatabaseId, ConnFactory]()
  private val lock = new Object()

  private def getOrCreateConnFactory(databaseId: DatabaseId): ConnFactory =
    lock.synchronized:
      connFactories.get(databaseId) match
        case Some(cf) =>
          cf
        case _ =>
          configProvider.fetchConfig(databaseId) match
            case Some(config) =>
              val ds = ConnFactory.constructor(config)
              connFactories.put(databaseId, ds)
              ds
            case None =>
              throw new RuntimeException(s"No configuration found for database: $databaseId")

  def getConfig(databaseId: DatabaseId): Option[DatabaseConfig] =
    configProvider.fetchConfig(databaseId)

  def listAvailableDatabases(): Iterable[DatabaseId] =
    configProvider.listCurrentDatabaseIds()

  def withConn[T](databaseId: DatabaseId)(f: Conn => T): T =
    val connFactory = getOrCreateConnFactory(databaseId)
    summon[Ctx].withSubCtx:
      val conn = connFactory.connR.unwrap
      f(conn)

  /**
   * Get the DataSource for a specific database.
   * This is useful when you need to work directly with the DataSource
   * or pass it to other libraries that expect a DataSource.
   */
  def getConnFactory(databaseId: DatabaseId): ConnFactory =
    getOrCreateConnFactory(databaseId)

  /**
   * Close a specific database connection pool.
   */
  def closeDatabase(databaseId: DatabaseId): Unit =
    lock.synchronized:
      connFactories.get(databaseId).foreach: cf =>
        if !cf.asInternal.getHikariDataSource.isClosed then
          cf.asInternal.getHikariDataSource.close()
        connFactories.remove(databaseId)

  /**
   * Close all database connection pools.
   * Should be called when the application shuts down.
   */
  def close(): Unit =
    lock.synchronized:
      connFactories.values.foreach: cf =>
        val ds = cf.asInternal.getHikariDataSource
        if !ds.isClosed then
          ds.close()
      connFactories.clear()

  /**
   * Get statistics about a specific connection pool.
   */
  def getPoolStats(databaseId: DatabaseId): Option[PoolStats] =
    lock.synchronized:
      connFactories.get(databaseId).map: cf =>
        val ds = cf.asInternal.getHikariDataSource
        PoolStats(
          totalConnections = ds.getHikariPoolMXBean.getTotalConnections,
          activeConnections = ds.getHikariPoolMXBean.getActiveConnections,
          idleConnections = ds.getHikariPoolMXBean.getIdleConnections,
          threadsAwaitingConnection = ds.getHikariPoolMXBean.getThreadsAwaitingConnection
        )

@CompanionGen()
case class PoolStats(
  totalConnections: Int,
  activeConnections: Int,
  idleConnections: Int,
  threadsAwaitingConnection: Int
)
