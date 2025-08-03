package a8.shared.jdbcf


import a8.shared.SharedImports.*
import a8.shared.app.Ctx
import a8.shared.jdbcf.mapper.{KeyedTableMapper, TableMapper}
import zio.*

import scala.collection.concurrent.TrieMap

/**
 * Companion object containing utilities for [[ConnFactory]] implementations.
 */
object ConnFactoryCompanion {

  /**
   * Provides mapper materialization strategies for database mappers.
   * 
   * Materialization pre-computes metadata about database tables and columns,
   * improving performance by avoiding repeated schema queries.
   */
  object MapperMaterializer {
    /**
     * A no-op materializer that returns mappers without caching or pre-computation.
     * 
     * Use this for testing or when schema queries are not a performance concern.
     */
    object noop extends MapperMaterializer {
      override def materialize[A, B](ktm: KeyedTableMapper[A, B]): KeyedTableMapper.Materialized[A, B] =
        KeyedTableMapper.Materialized(ktm)
      override def materialize[A](tm: TableMapper[A]): TableMapper[A] =
        tm
    }
  }

  /**
   * Abstract base class for mapper materialization strategies.
   * 
   * Materializers can cache table metadata to avoid repeated database queries
   * for column information, constraints, and other schema details.
   */
  abstract class MapperMaterializer {
    /**
     * Materializes a keyed table mapper, potentially caching metadata.
     * 
     * @tparam A The row type
     * @tparam B The key type
     * @param ktm The mapper to materialize
     * @return A materialized version of the mapper
     */
    def materialize[A,B](ktm: KeyedTableMapper[A,B]): KeyedTableMapper.Materialized[A,B]
    
    /**
     * Materializes a table mapper, potentially caching metadata.
     * 
     * @tparam A The row type
     * @param tm The mapper to materialize
     * @return A materialized version of the mapper
     */
    def materialize[A](tm: TableMapper[A]): TableMapper[A]
  }

  /**
   * Default implementation of MapperMaterializer that caches materialized mappers.
   * 
   * This implementation uses a thread-safe TrieMap to cache materialized mappers,
   * avoiding repeated database schema queries for the same table.
   * 
   * @param connFactory The connection factory to use for schema queries
   * @param ctx The execution context
   */
  class MapperMaterializerImpl(
    connFactory: ConnFactory,
  )(
    using ctx: Ctx
  ) extends MapperMaterializer {

    lazy val cache = TrieMap.empty[KeyedTableMapper[?,?],KeyedTableMapper.Materialized[?,?]]

    def createMaterializedMapper[A,B](ktm: KeyedTableMapper[A,B]): KeyedTableMapper.Materialized[A,B] =
      ctx.withSubCtx {
        given Conn = connFactory.connR.unwrap
        ktm.materializeKeyedTableMapper
      }

    def fetchOrCreate[A,B](ktm: KeyedTableMapper[A,B]): KeyedTableMapper.Materialized[A,B] =
      cache
        .getOrElseUpdate(ktm, createMaterializedMapper(ktm))
        .asInstanceOf[KeyedTableMapper.Materialized[A,B]]

    override def materialize[A, B](ktm: KeyedTableMapper[A, B]): KeyedTableMapper.Materialized[A, B] =
      fetchOrCreate(ktm)

    override def materialize[A](tm: TableMapper[A]): TableMapper[A] = {
      tm match {
        case ktm: KeyedTableMapper[A,_] =>
          fetchOrCreate(ktm).value
      }
    }

  }

}


/**
 * Companion trait for [[ConnFactory]] that provides factory methods.
 * 
 * This trait should be implemented by platform-specific ConnFactory companions
 * to provide database-specific connection factory implementations.
 */
trait ConnFactoryCompanion {

//  lazy val layer: ZLayer[DatabaseConfig & Scope, Throwable, ConnFactory] =
//    ZLayer(constructor)

  /**
   * Constructs a new ConnFactory instance from the given configuration.
   * 
   * This method is implemented by platform-specific companions to create
   * the appropriate connection factory for each database type.
   * 
   * @param databaseConfig The database configuration
   * @param ctx The execution context
   * @return A new ConnFactory instance
   */
  def constructor(databaseConfig: DatabaseConfig)(using Ctx): ConnFactory

  /**
   * Creates a managed resource for a ConnFactory.
   * 
   * The returned Resource ensures the ConnFactory is properly closed when
   * the resource scope ends, preventing connection leaks.
   * 
   * @param databaseConfig The database configuration
   * @return A ZIO Resource that manages the ConnFactory lifecycle
   * 
   * @example
   * {{{n   * ConnFactory.resource(config).use { connFactory =>
   *   // Use the connection factory
   *   connFactory.connR.use { conn =>
   *     conn.query(sql"SELECT * FROM users")
   *   }
   * }
   * }}}
   */
  def resource(databaseConfig: DatabaseConfig): Resource[ConnFactory] = {
    Resource
      .acquireRelease(
        constructor(databaseConfig)
      )(
        _.safeClose()
      )
  }

}
