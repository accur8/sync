package a8.shared.jdbcf


import a8.shared.jdbcf.{ResolvedTableName, SchemaName, TableLocator, TableName}
import cats.effect.Sync
import SqlString._

object PostgresDialect extends Dialect {

  implicit def self: Dialect = this

  override def isPostgres: Boolean = true

  /**
   * will do a case insensitive lookup
   */
  override def resolveTableName[F[_] : Sync](tableLocator: TableLocator, conn: Conn[F]): F[ResolvedTableName] = {
    import tableLocator._
    val schemaPart = tableLocator.schemaName.map(s=>q" and schemaname = ${s.asString.escape}").getOrElse(q"")
    val sql = q"""
SELECT
    tablename,
    schemaname,
    false
  FROM
    pg_catalog.pg_tables
  WHERE
    lower(tablename) = ${tableName.asLowerCaseStringValue}${schemaPart}
UNION
  SELECT
     viewname,
     schemaname,
     true
   FROM
     pg_catalog.pg_views
   WHERE
     lower(viewname) = ${tableName.asLowerCaseStringValue}${schemaPart}
UNION
 SELECT
     matviewname,
     schemaname,
     true
   FROM
     pg_catalog.pg_matviews
   WHERE
     lower(matviewname) = ${tableName.asLowerCaseStringValue}${schemaPart}
"""

    conn
      .query[ResolvedTableName](sql)
      .unique

  }


}
