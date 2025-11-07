package a8.shared.jdbcf


import a8.shared.SharedImports._
import a8.shared.jdbcf.SqlString._

import java.sql.Connection

object MySqlDialect extends Dialect {


  override def buildEscaper(conn: Connection): Escaper = {
    val keywordSet = KeywordSet.fromMetadata(conn.getMetaData)
    val st = conn.createStatement()
    val noBackslashEscapes =
      try {
        val rs = st.executeQuery("SELECT @@SESSION.sql_mode")
        try {
          if ( rs.next() ) {
            rs.getString(1).contains("NO_BACKSLASH_ESCAPES")
          } else {
            false
          }
        } finally {
          rs.close()
        }
      } finally {
        st.close()
    }

    val identifierQuoteString = conn.getMetaData.getIdentifierQuoteString
    new DefaultEscaper(identifierQuoteString, keywordSet, defaultCaseFn = isIdentifierDefaultCase) {
      override def escapeStringValue(value: String): String = {
        val content =
          value.replace("\\", "\\\\") match {
            case s if noBackslashEscapes =>
              s
            case s =>
              value.replace("\\", "\\\\")
          }
        "'" + content + "'"
      }
    }
  }

  //  class DefaultEscaper(identifierQuoteStr: String, keywordSet: KeywordSet, defaultCaseFn: String=>Boolean) extends Escaper {
//
//    override def unsafeSqlEscapeStringValue(value: String): String =
//      "'" + value.replace("'","''") + "'"
//
//    def unsafeSqlQuotedIdentifier(identifier: String): String = {
//      val isDefaultCase = defaultCaseFn(identifier)
//      val isKeyword = keywordSet.isKeyword(identifier)
//      if ( isDefaultCase && !isKeyword ) {
//        identifier
//      } else {
//        s"${identifierQuoteStr}${identifier}${identifierQuoteStr}"
//      }
//    }
//
//  }


  override def resolveTableNameImpl(tableLocator: TableLocator, conn: Conn, foundTables: Vector[ResolvedTableName]): ResolvedTableName = {
    val currentCatalog = conn.jdbcUrl.path.last
    foundTables
      .find(_.catalog.exists(_.value.toString.equalsIgnoreCase(currentCatalog)))
      .getOrElse(foundTables.head)
  }

  implicit def self: Dialect = this

  override def isPostgres: Boolean = false

  /**
   * mysql is case insensitive
   */
  override def isIdentifierDefaultCase(name: String): Boolean =
    true


}
