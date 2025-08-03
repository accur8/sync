package a8.shared.jdbcf

import a8.shared.SharedImports.*
import a8.shared.app.Ctx
import a8.shared.jdbcf.Conn.ConnInternal
import a8.shared.jdbcf.SqlString.CompiledSql

object Query {

  def create[A : RowReader](conn: ConnInternal, sql: SqlString): Query[A] = {

    val sql0 = conn.compile(sql)

    new Query[A] {

      override val sql: CompiledSql = sql0

      override val reader: RowReader[A] = implicitly[RowReader[A]]

      def stream: zio.XStream[A] = {
        withSqlCtx(conn.databaseId, sql) {
          conn.withInternalConn { jdbcConn =>
            // no cleanup since the result set will break out of the scope of this function
            val st = jdbcConn.createStatement()
            val rs = st.executeQuery(sql.value)
            resultSetToStream(rs)
              .map(reader.read)
          }
        }
      }

      override def select: Iterable[A] = {
        given Ctx = conn.ctx
        stream.runCollect()
      }

      override def fetchOpt: Option[A] = {
        given Ctx = conn.ctx
        select.headOption
      }

    }
  }

}


/**
 * A lazy representation of a database query that can be executed to retrieve results.
 * 
 * Query instances are created by calling `conn.query(sql)` and are lazy, meaning
 * the SQL is not executed until you call one of the fetch methods.
 * 
 * @tparam A The type of rows this query returns, determined by the implicit RowReader
 * 
 * @example {{{
 * // Create a query (lazy - no execution yet)
 * val userQuery = conn.query[User](
 *   sql"SELECT id, name, email FROM users WHERE active = true"
 * )
 * 
 * // Execute and fetch all results
 * val allUsers: Iterable[User] = userQuery.select
 * 
 * // Execute and fetch optional single result
 * val maybeUser: Option[User] = conn.query[User](
 *   sql"SELECT * FROM users WHERE id = $userId"
 * ).fetchOpt
 * 
 * // Execute and fetch required single result (throws if not found)
 * val user: User = conn.query[User](
 *   sql"SELECT * FROM users WHERE id = $userId"
 * ).fetch
 * }}}
 * 
 * @note All fetch methods will execute the query each time they are called
 */
trait Query[A] { query =>
  val sql: CompiledSql
  val reader: RowReader[A]
  
  /**
   * Execute the query and return all results as an Iterable.
   * 
   * @return All rows returned by the query
   */
  def select: Iterable[A]
  
  /**
   * Execute the query and return the first result, if any.
   * 
   * @return Some(firstRow) if the query returned at least one row, None otherwise
   */
  def fetchOpt: Option[A]
  
  /**
   * Execute the query and return exactly one result.
   * 
   * @return The single row returned by the query
   * @throws SQLException if the query returns no rows
   */
  def fetch: A =
    fetchOpt match {
      case None =>
        throw new java.sql.SQLException(s"query return 0 records expected 1 -- ${sql}")
      case Some(v) =>
        v
    }
}

