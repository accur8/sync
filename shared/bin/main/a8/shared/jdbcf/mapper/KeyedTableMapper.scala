package a8.shared.jdbcf.mapper


import a8.shared.SharedImports._
import a8.shared.jdbcf.{Conn, SqlString}
import a8.shared.jdbcf.mapper.KeyedTableMapper.{Materialized, UpsertResult}
import zio._

/**
 * Companion object for [[KeyedTableMapper]] containing result types.
 */
object KeyedTableMapper {

  /**
   * Result of an upsert (insert or update) operation.
   * 
   * Used to indicate whether a row was inserted or updated during
   * an upsert operation.
   */
  sealed trait UpsertResult
  object UpsertResult {
    /** Indicates the row was updated (already existed) */
    case object Update extends UpsertResult
    /** Indicates the row was inserted (did not exist) */
    case object Insert extends UpsertResult
  }

  /**
   * A materialized keyed table mapper with cached metadata.
   * 
   * Materialization pre-computes and caches table metadata like column names,
   * types, and constraints to avoid repeated database queries.
   * 
   * @tparam A The row type
   * @tparam PK The primary key type
   * @param value The underlying keyed table mapper
   */
  case class Materialized[A,PK](value: KeyedTableMapper[A,PK])

}

/**
 * A table mapper for tables with primary keys, supporting key-based operations.
 * 
 * KeyedTableMapper extends [[TableMapper]] with operations that work with primary keys,
 * enabling efficient single-row operations like fetch, update, delete, and upsert.
 * 
 * == Overview ==
 * 
 * KeyedTableMapper adds key-based operations to the base TableMapper functionality:
 * - Fetch a single row by primary key
 * - Update a specific row
 * - Delete by primary key  
 * - Upsert (insert or update)
 * 
 * == Creating Keyed Mappers ==
 * 
 * Use [[MapperBuilder]] to create a keyed mapper:
 * {{{n * case class User(id: Long, name: String, email: String)
 * 
 * implicit val userMapper: KeyedTableMapper[User, Long] = 
 *   MapperBuilder[User]("users")
 *     .addField(_.id)
 *     .addField(_.name) 
 *     .addField(_.email)
 *     .buildKeyedTableMapper(_.id)
 * }}}
 * 
 * == Key Types ==
 * 
 * Primary keys can be:
 * - Single values (Long, String, UUID, etc.)
 * - Composite keys using tuples (Long, String)
 * - Custom case classes for complex keys
 * 
 * == Example Usage ==
 * 
 * {{{n * val mapper = implicitly[KeyedTableMapper[User, Long]]
 * 
 * connFactory.connR.use { implicit conn =>
 *   // Fetch by key
 *   val user = conn.fetch(mapper, userId)
 *   
 *   // Update 
 *   conn.update(mapper, user.copy(name = "New Name"))
 *   
 *   // Delete
 *   conn.delete(mapper, userId)
 *   
 *   // Upsert
 *   val result = conn.upsert(mapper, newUser)
 *   result match {
 *     case UpsertResult.Insert => println("Inserted")
 *     case UpsertResult.Update => println("Updated")
 *   }
 * }
 * }}}
 * 
 * @tparam A The row type
 * @tparam PK The primary key type
 * 
 * @see [[TableMapper]] for the base mapper trait
 * @see [[MapperBuilder]] for creating mappers
 */
trait KeyedTableMapper[A, PK] extends TableMapper[A] {

  /**
   * Generates a WHERE clause for the given primary key.
   * 
   * @param key The primary key value
   * @return SQL WHERE clause (without the WHERE keyword)
   * 
   * @example
   * {{{n   * keyToWhereClause(123)
   * // Returns: SqlString("id = ?", Seq(123))
   * }}}
   */
  def keyToWhereClause(key: PK): SqlString
  
  /**
   * Generates UPDATE SQL for the given row.
   * 
   * The UPDATE uses the primary key from the row to identify which row to update.
   * Audit fields (updatedDateTime, updatedBy) are automatically set if configured.
   * 
   * @param row The row with updated values
   * @param extraWhere Optional additional WHERE conditions
   * @return UPDATE SQL statement
   * 
   * @example
   * {{{n   * val user = User(123, "John", "john@example.com")
   * updateSql(user)
   * // UPDATE users SET name = ?, email = ? WHERE id = ?
   * }}}
   */
  def updateSql(row: A, extraWhere: Option[SqlString] = None): SqlString
  
  /**
   * Generates DELETE SQL for the given primary key.
   * 
   * @param key The primary key of the row to delete
   * @return DELETE SQL statement
   * 
   * @example
   * {{{n   * deleteSql(123)
   * // DELETE FROM users WHERE id = ?
   * }}}
   */
  def deleteSql(key: PK): SqlString
  
  /**
   * Generates SELECT SQL to fetch a single row by primary key.
   * 
   * @param key The primary key to fetch
   * @return SELECT SQL statement
   * 
   * @example
   * {{{n   * fetchSql(123)
   * // SELECT id, name, email FROM users WHERE id = ?
   * }}}
   */
  def fetchSql(key: PK): SqlString
  
  /**
   * Extracts the primary key from a row.
   * 
   * @param row The row to extract the key from
   * @return The primary key value
   * 
   * @example
   * {{{n   * val user = User(123, "John", "john@example.com")
   * key(user) // Returns: 123
   * }}}
   */
  def key(row: A): PK

  override def materializeTableMapper(using Conn): TableMapper[A] =
    materializeKeyedTableMapper
      .asInstanceOf[TableMapper[A]]

  /**
   * Materializes this mapper for a specific database connection.
   * 
   * Similar to [[TableMapper.materializeTableMapper]], but returns a
   * [[Materialized]] wrapper that preserves the keyed mapper type.
   * 
   * @param conn The database connection (implicit)
   * @return A materialized keyed mapper
   */
  def materializeKeyedTableMapper(using Conn): Materialized[A,PK]
//  def materializeKeyedTableMapper(implicit conn: Conn): Task[Materialized[A,PK]]

}
