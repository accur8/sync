package a8.shared.jdbcf

import a8.shared

import java.time.{LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZoneOffset}
import a8.shared.jdbcf.TypeName

import scala.language.implicitConversions
import a8.shared.SharedImports._
import a8.shared.jdbcf.JdbcMetadata.ResolvedColumn
import a8.shared.jdbcf.SqlString.{CompiledSql, DefaultJdbcEscaper, Escaper}
import a8.shared.json.ast.JsDoc

/**
 * Companion object for [[SqlString]] providing factory methods and common SQL fragments.
 * 
 * SqlString is the foundation of the type-safe SQL DSL, allowing construction of
 * parameterized SQL queries with proper escaping and dialect support.
 */
object SqlString extends SqlStringLowPrio {

  /** Double quote character */
  val DoubleQuote: String = '"'.toString
  
  /** SQL NULL keyword */
  val Null: SqlString = keyword("null")
  
  /** Comma separator */
  val Comma: SqlString = keyword(",")
  
  /** Comma with space separator */
  val CommaSpace: SqlString = keyword(", ")
  
  /** Space character */
  val Space: SqlString = keyword(" ")
  
  /** Left parenthesis */
  val LeftParen: SqlString = keyword("(")
  
  /** Right parenthesis */
  val RightParen: SqlString = keyword(")")
  
  /** OR keyword with spaces */
  val OrWs: SqlString = keyword(" or ")
  
  /** Question mark placeholder */
  val QuestionMark: SqlString = keyword("?")

  /**
   * Wraps a SQL fragment in parentheses.
   * 
   * @param sql The SQL to wrap
   * @return SQL wrapped in parentheses
   */
  def parens(sql: SqlString): SqlString =
    CompositeSqlString(Iterable(LeftParen, sql, RightParen))

  case object Empty extends SqlString
  case class RawSqlString(sqlSafeValue: String) extends SqlString
  case class EscapedSqlString(escapeMe: String) extends SqlString
  case class DialectQuotedIdentifier(value: String) extends SqlString
  case class Concat(l: SqlString, r: SqlString) extends SqlString
  case class Concat3(l: SqlString, m: SqlString, r: SqlString) extends SqlString
  case class SeparatedSqlString(iterable: Iterable[SqlString], separator: SqlString) extends SqlString
  case class CompositeSqlString(parts: Iterable[SqlString]) extends SqlString
  case class CompositeString(parts: Iterable[String]) extends SqlString

//  case class Context(
//    dialect: Dialect,
//    jdbcConn: Option[java.sql.Connection] = None,
//  )

  object unsafe {

    def rawSqlString(s: String): RawSqlString = RawSqlString(s)

    def compile(sqlString: SqlString, escaper: Escaper): CompiledSql = {
//      implicit val ctx = Context(escaper)

      val sb = new StringBuilder
      def run(ss: SqlString): Unit = {
        ss match {
          case c: Concat =>
            run(c.l)
            run(c.r)
          case c: Concat3 =>
            run(c.l)
            run(c.m)
            run(c.r)
          case Empty =>

          case h: HasSqlString =>
            run(h.asSqlFragment)
          case RawSqlString(v) =>
            sb.append(v): @scala.annotation.nowarn
          case EscapedSqlString(s) =>
            sb.append(escaper.escapeStringValue(s)): @scala.annotation.nowarn
          case CompositeString(parts) =>
            parts.foreach(sb.append)
          case CompositeSqlString(parts) =>
            parts.foreach(run)
          case DialectQuotedIdentifier(v) =>
            sb.append(escaper.quoteSqlIdentifier(v)): @scala.annotation.nowarn
          case SeparatedSqlString(iter, sep) =>
            if ( iter.nonEmpty ) {
              var first = true
              iter.foreach { ss =>
                  if ( first ) {
                    first = false
                    run(ss)
                  } else {
                    run(sep)
                    run(ss)
                  }
                }
            }
        }
      }

      run(sqlString)

      CompiledSql(sb.toString())

    }
  }

  /*
   * so the following set of methods could in the future have some validation level stuff to make sure
   * what is passed through them is actually what the provider says it is
   */
  def option(o: Option[SqlString]): SqlString = o.getOrElse(Empty)
  def number(value: BigDecimal): SqlString = unsafe.rawSqlString(value.toString)
  def boolean(value: Boolean): SqlString = unsafe.rawSqlString(value.toString)
  def timestamp(value: java.sql.Timestamp): SqlString = EscapedSqlString(value.toString)
  def operator(str: String): SqlString = unsafe.rawSqlString(str)
  def identifier(str: String): SqlString = DialectQuotedIdentifier(str)
  def typeName(name: TypeName): SqlString = {
    name match {
      case TypeName(n, None, None) =>
        unsafe.rawSqlString(n)
      case TypeName(n, Some(l), None) =>
        CompositeString(Iterable(n, "(", l.toString, ")"))
      case TypeName(n, Some(l), Some(d)) =>
        CompositeString(Iterable(n, "(", l.toString, ",", d.toString, ")"))
      case _ =>
        sys.error(s"inavlid type name ${name}")
    }
  }

  def escapedString(value: String): SqlString = EscapedSqlString(value)

  def keyword(str: String): SqlString = unsafe.rawSqlString(str)

  case class CompiledSql(value: String)


  trait HasSqlString extends SqlString {
    def asSqlFragment: SqlString
  }

  object SqlStringer extends Logging {

    implicit val jsDocSqlStringer: SqlStringer[JsDoc] =
      JsDocSqlStringer(false, None)

    implicit val hasSqlString: SqlStringer[HasSqlString] =
      new SqlStringer[HasSqlString] {
        override def toSqlString(a: HasSqlString): SqlString = a.asSqlFragment
      }

    implicit def toSqlStringer[A](implicit fn: A=>SqlString): SqlStringer[A] =
      new SqlStringer[A] {
        override def toSqlString(a: A): SqlString = fn(a)
      }

    implicit val stringSqlStringer: SqlStringer[String] =
      StringSqlStringer(Integer.MAX_VALUE)

    case class StringSqlStringer(maxLength: Int, resolvedColumn: Option[ResolvedColumn] = None) extends SqlStringer[String] {

      override def materialize(conn: Conn, resolvedColumn: ResolvedColumn): SqlStringer[String] =
        copy(maxLength = resolvedColumn.jdbcColumn.columnSize)

      override def toSqlString(a: String): SqlString = {
        val trimmed =
          if ( a.length > maxLength ) {
            logger.warn(s"trimming string from length ${a.length} to ${maxLength} for ${resolvedColumn.map(_.qualifiedName)}")
            a.substring(0, maxLength)
          } else {
            a
          }
        trimmed.escape
      }

    }

    implicit val optionStringStringer: SqlStringer[Option[String]] =
      new SqlStringer[Option[String]] {
        override def toSqlString(a: Option[String]): SqlString = {
          a match {
            case None =>
              Null
            case Some(s) =>
              stringSqlStringer.toSqlString(s)
          }
        }
      }

    implicit def optionSqlString[A: SqlStringer]: SqlStringer[Option[A]] =
      OptionSqlStringer(SqlStringer[A])

    def apply[A : SqlStringer]: SqlStringer[A] = implicitly[SqlStringer[A]]

    case class OptionSqlStringer[A](delegate: SqlStringer[A]) extends SqlStringer[Option[A]] {

      override def materialize(conn: Conn, resolvedColumn: ResolvedColumn): SqlStringer[Option[A]] = {
        val m = delegate.materialize(conn, resolvedColumn)
        OptionSqlStringer(m)
      }

      override def toSqlString(opt: Option[A]): SqlString = {
        opt match {
          case None =>
            SqlString.Null
          case Some(a) =>
            delegate.toSqlString(a)
        }
      }

    }

  }

  /**
   * Type class for converting Scala values to SQL fragments.
   * 
   * SqlStringer provides the conversion logic for embedding Scala values
   * into SQL queries with proper escaping and type handling.
   * 
   * @tparam A The Scala type to convert
   * 
   * @example
   * {{{n   * implicit val uuidStringer: SqlStringer[UUID] = new SqlStringer[UUID] {
   *   def toSqlString(uuid: UUID): SqlString = uuid.toString.escape
   * }
   * }}}
   */
  trait SqlStringer[A] {
    /**
     * Optionally refines this stringer based on database column metadata.
     * 
     * @param conn The database connection
     * @param resolvedColumn Column metadata
     * @return A refined stringer (default returns this)
     */
    def materialize(conn: Conn, resolvedColumn: ResolvedColumn): SqlStringer[A] = this
    
    /**
     * Converts a value to a SQL fragment.
     * 
     * @param a The value to convert
     * @return SQL representation of the value
     */
    def toSqlString(a: A): SqlString
  }

  implicit def localDate(ld: LocalDate): SqlString =
    java.sql.Date.valueOf(ld).toString.escape

  implicit def localDateTime(ldt: LocalDateTime): SqlString =
    java.sql.Timestamp.valueOf(ldt).toString.escape

  // as suggested here https://stackoverflow.com/questions/43216737/how-to-convert-java-sql-timestamp-to-java-time-offsetdatetime
  implicit def offsetDateTime(odt: OffsetDateTime): SqlString =
    java.sql.Timestamp.valueOf(odt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime).toString.escape

  implicit def localTime(lt: LocalTime): SqlString =
    java.sql.Time.valueOf(lt).toString.escape

  implicit def optionSqlString(optionSqlString: Option[SqlString]): SqlString =
    optionSqlString
      .getOrElse(Empty)

  implicit def optionOps[A](optionA: Option[A])(implicit fn: A=>SqlString): SqlString =
    optionA match {
      case None =>
        Null
      case Some(v) =>
        fn(v)
    }

  implicit class OptionOps[A](optionA: Option[A])(implicit fn: A=>SqlString) {
    def orSqlNull: SqlString =
      optionA
        .map(fn)
        .getOrElse(Null)
  }

  /**
   * Handles SQL escaping and quoting for different database dialects.
   * 
   * Escaper provides dialect-specific logic for:
   * - Escaping string values (handling quotes)
   * - Quoting identifiers (table/column names)
   */
  trait Escaper {
    /**
     * Escapes a string value for safe inclusion in SQL.
     * 
     * @param value The string to escape
     * @return Escaped string with quotes
     */
    def escapeStringValue(value: String): String
    
    /**
     * Quotes a SQL identifier if necessary.
     * 
     * Identifiers are quoted if they:
     * - Are SQL keywords
     * - Contain special characters
     * - Don't match the default case convention
     * 
     * @param identifier The identifier to quote
     * @return Quoted identifier if necessary
     */
    def quoteSqlIdentifier(identifier: String): String
  }

  class DefaultEscaper(identifierQuoteStr: String, keywordSet: KeywordSet, defaultCaseFn: String=>Boolean) extends Escaper {

    override def escapeStringValue(value: String): String =
      "'" + value.replace("'","''") + "'"

    override def quoteSqlIdentifier(identifier: String): String = {
      val isDefaultCase = defaultCaseFn(identifier)
      val isKeyword = keywordSet.isKeyword(identifier)
      if ( isDefaultCase && !isKeyword ) {
        identifier
      } else {
        s"${identifierQuoteStr}${identifier}${identifierQuoteStr}"
      }
    }

  }

  object DefaultJdbcEscaper extends DefaultEscaper("\"", KeywordSet.default, _ => false)

  object NoopEscaper extends Escaper {
    override def escapeStringValue(value: String): String =
      "'" + value.replace("'","''") + "'"
    override def quoteSqlIdentifier(identifier: String): String =
      identifier
  }


  //  implicit class IterableSqlString(iter: Iterable[SqlString]) {
//    def mkSqlString: SqlString = CompositeSqlString(iter)
//    def mkSqlString(separator: SqlString): SqlString = SeparatedSqlString(iter, separator)
//  }

  /**
   * Extension methods for building SQL strings using operators.
   * 
   * @param left The left-hand SQL fragment
   */
  implicit class SqlStringOps(private val left: SqlString) extends AnyVal {
    /**
     * Concatenates two SQL fragments without spacing.
     * 
     * @example {{{nsql"SELECT" ~ sql"*" // SELECT*
     * }}}
     */
    @inline def ~(r: SqlString): SqlString = Concat(left, r)
    
    /**
     * Concatenates two SQL fragments with a space between.
     * 
     * @example {{{nsql"SELECT" * sql"*" // SELECT *
     * }}}
     */
    @inline def *(r: SqlString): SqlString = Concat3(left, Space, r)
    
    /**
     * Alias for * operator.
     */
    @inline def ~*~(r: SqlString): SqlString = left * r
  }

  given [A <: SqlString, B <: SqlString]: CanEqual[A,B] = CanEqual.derived

}

/**
 * Type-safe representation of SQL fragments.
 * 
 * SqlString is the core type of the SQL DSL, representing SQL fragments that can be
 * safely composed and executed. It handles:
 * - Parameter binding
 * - SQL injection prevention through proper escaping
 * - Dialect-specific SQL generation
 * 
 * == Creating SqlString ==
 * 
 * Use the sql string interpolator:
 * {{{n * val name = "John"
 * val age = 25
 * val query = sql"SELECT * FROM users WHERE name = \${name} AND age > \${age}"
 * }}}
 * 
 * == Composing SqlString ==
 * 
 * SqlStrings can be composed using operators:
 * {{{n * val select = sql"SELECT * FROM users"
 * val where = sql"WHERE active = true"
 * val query = select * where
 * }}}
 * 
 * == Execution ==
 * 
 * SqlString compiles to SQL with bound parameters:
 * {{{n * conn.query(sql"SELECT * FROM users WHERE id = \${userId}")
 * }}}
 * 
 * @see [[SqlStringOps]] for composition operators
 * @see [[SqlStringer]] for custom type conversions
 */
sealed trait SqlString {

  /**
   * Compiles this SQL fragment using the given escaper.
   * 
   * @param escaper The escaper for dialect-specific quoting
   * @return Compiled SQL ready for execution
   */
  def compile(using escaper: Escaper): CompiledSql =
    SqlString.unsafe.compile(this, escaper)

  /**
   * Returns a string representation using default JDBC escaping.
   * 
   * Note: This is for debugging only. Use compile() for execution.
   */
  override def toString: String =
    SqlString.unsafe.compile(this, DefaultJdbcEscaper).value

}
