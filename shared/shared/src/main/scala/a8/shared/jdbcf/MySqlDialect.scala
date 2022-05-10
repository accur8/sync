package a8.shared.jdbcf

import a8.shared.jdbcf.SqlString._
import cats.effect.{Resource, Sync}
import cats.effect.kernel.Async

import java.sql.Connection

object MySqlDialect extends Dialect {

  implicit def self: Dialect = this

  override def isPostgres: Boolean = false

  /**
   * mysql is case insensitive
   */
  override def isIdentifierDefaultCase(name: String): Boolean =
    true


}
