package a8.shared.jdbcf.mapper

import a8.shared.SharedImports._
import a8.shared.jdbcf.mapper.MapperBuilder.AuditProvider
import a8.shared.jdbcf.{Conn, RowReader, RowWriter, SqlString, TableName}

/**
 * Companion object for [[TableMapper]] providing convenient access to implicit instances.
 */
object TableMapper {
  /**
   * Summons an implicit TableMapper instance for type A.
   * 
   * @tparam A The row type
   * @return The implicit TableMapper instance
   * 
   * @example
   * {{{n   * case class User(id: Long, name: String)
   * implicit val userMapper: TableMapper[User] = ...
   * 
   * val mapper = TableMapper[User]
   * }}}
   */
  def apply[A: TableMapper]: TableMapper[A] = implicitly[TableMapper[A]]
}

/**
 * Maps Scala case classes to database tables for CRUD operations.
 * 
 * TableMapper provides a type-safe mapping between Scala types and database tables,
 * handling SQL generation, row reading/writing, and schema introspection.
 * 
 * == Overview ==
 * 
 * TableMapper is the foundation of the ORM-like functionality, providing:
 * - Automatic SQL generation for common operations
 * - Type-safe field access through the query DSL
 * - Support for audit fields (created/updated timestamps)
 * - Database-specific SQL dialect handling through materialization
 * 
 * == Creating Mappers ==
 * 
 * Mappers are typically created using the [[MapperBuilder]] DSL:
 * {{{n * case class User(id: Long, name: String, email: String)
 * 
 * implicit val userMapper: TableMapper[User] = 
 *   MapperBuilder[User]("users")
 *     .addField(_.id)
 *     .addField(_.name)
 *     .addField(_.email)
 *     .buildTableMapper()
 * }}}
 * 
 * == Database Operations ==
 * 
 * TableMapper generates SQL for common operations:
 * {{{n * val mapper = TableMapper[User]
 * 
 * // Insert a new row
 * val insertSql = mapper.insertSql(user)
 * 
 * // Select with WHERE clause
 * val selectSql = mapper.selectSql(sql"WHERE email = \${email}")
 * }}}
 * 
 * @tparam A The row type (typically a case class)
 * 
 * @see [[KeyedTableMapper]] for tables with primary keys
 * @see [[MapperBuilder]] for creating mappers
 * @see [[ComponentMapper]] for the base trait
 */
trait TableMapper[A] extends ComponentMapper[A] { self =>

  /**
   * The database table name this mapper operates on.
   */
  val tableName: TableName

  /**
   * Generates INSERT SQL for the given row.
   * 
   * The generated SQL includes all mapped fields and uses parameter placeholders
   * for values. Audit fields (if configured) are automatically populated.
   * 
   * @param row The row to insert
   * @return SQL string with bound parameters
   * 
   * @example
   * {{{n   * val user = User(0, "John", "john@example.com")
   * val sql = mapper.insertSql(user)
   * // INSERT INTO users (name, email) VALUES (?, ?)
   * }}}
   */
  def insertSql(row: A): SqlString
  
  /**
   * Generates a SELECT field list with the given table alias.
   * 
   * This is used internally to build SELECT queries with proper field aliasing
   * for joins and subqueries.
   * 
   * @param alias The table alias to use (e.g., "u" for "u.id, u.name")
   * @return SQL fragment for the SELECT field list
   */
  def selectFieldsSql(alias: String): SqlString
  
  /**
   * Generates a complete SELECT statement with the given WHERE clause.
   * 
   * @param whereClause The WHERE clause (including the WHERE keyword)
   * @return Complete SELECT SQL statement
   * 
   * @example
   * {{{n   * val sql = mapper.selectSql(sql"WHERE active = true ORDER BY name")
   * // SELECT id, name, email FROM users WHERE active = true ORDER BY name
   * }}}
   */
  def selectSql(whereClause: SqlString): SqlString

  /**
   * Materializes this mapper for a specific database connection.
   * 
   * Materialization allows the mapper to adapt to database-specific features:
   * - Keyword quoting (e.g., backticks for MySQL, double quotes for PostgreSQL)
   * - Data type mappings
   * - SQL dialect variations
   * 
   * The materialized mapper may also cache table metadata to improve performance.
   * 
   * @param conn The database connection to materialize for
   * @return A mapper optimized for the specific database
   * 
   * @example
   * {{{n   * connFactory.connR.use { implicit conn =>
   *   val materializedMapper = mapper.materializeTableMapper
   *   // Use the materialized mapper for operations
   * }
   * }}}
   */
  def materializeTableMapper(implicit conn: Conn): TableMapper[A]

  /**
   * Provides audit field handling for this mapper.
   * 
   * The AuditProvider manages automatic population of audit fields like:
   * - createdDateTime / createdBy (on insert)
   * - updatedDateTime / updatedBy (on update)
   * 
   * @return The audit provider for this mapper
   */
  def auditProvider: AuditProvider[A]

}
