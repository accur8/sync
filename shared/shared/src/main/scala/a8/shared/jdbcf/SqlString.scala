package a8.shared.jdbcf

import a8.shared

import java.time.{LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZoneOffset}
import a8.shared.jdbcf.TypeName

import scala.language.implicitConversions
import a8.shared.SharedImports._
import a8.shared.jdbcf.JdbcMetadata.ResolvedColumn
import a8.shared.jdbcf.SqlString.{CompiledSql, DefaultJdbcEscaper, Escaper}
import a8.shared.json.ast.JsDoc
import zio._

object SqlString extends SqlStringLowPrio {

  val DoubleQuote = '"'.toString
  val Null: SqlString = keyword("null")
  val Comma = keyword(",")
  val CommaSpace = keyword(", ")
  val Space = keyword(" ")

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

    def rawSqlString(s: String) = RawSqlString(s)

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
            sb.append(v)
          case EscapedSqlString(s) =>
            sb.append(escaper.unsafeSqlEscapeStringValue(s))
          case CompositeString(parts) =>
            parts.foreach(sb.append)
          case CompositeSqlString(parts) =>
            parts.foreach(run)
          case DialectQuotedIdentifier(v) =>
            sb.append(escaper.unsafeSqlQuotedIdentifier(v))
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

  object SqlStringer {

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

      override def materialize(conn: Conn, resolvedColumn: ResolvedColumn): Task[SqlStringer[String]] =
        ZIO.succeed(copy(maxLength = resolvedColumn.jdbcColumn.columnSize))

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

    def apply[A : SqlStringer] = implicitly[SqlStringer[A]]

    case class OptionSqlStringer[A](delegate: SqlStringer[A]) extends SqlStringer[Option[A]] {

      override def materialize(conn: Conn, resolvedColumn: ResolvedColumn): Task[SqlStringer[Option[A]]] = {
        delegate
          .materialize(conn, resolvedColumn)
          .map(OptionSqlStringer.apply)
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

  trait SqlStringer[A] {
    def materialize(conn: Conn, resolvedColumn: ResolvedColumn): Task[SqlStringer[A]] =
      ZIO.succeed(this)
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

  trait Escaper {
    def unsafeSqlEscapeStringValue(value: String): String
    def unsafeSqlQuotedIdentifier(identifier: String): String
  }

  class DefaultEscaper(identifierQuoteStr: String, keywordSet: KeywordSet, defaultCaseFn: String=>Boolean) extends Escaper {

    override def unsafeSqlEscapeStringValue(value: String): String =
      "'" + value.replace("'","''") + "'"

    def unsafeSqlQuotedIdentifier(identifier: String): String = {
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
    override def unsafeSqlEscapeStringValue(value: String): String =
      "'" + value.replace("'","''") + "'"
    override def unsafeSqlQuotedIdentifier(identifier: String): String =
      identifier
  }


  //  implicit class IterableSqlString(iter: Iterable[SqlString]) {
//    def mkSqlString: SqlString = CompositeSqlString(iter)
//    def mkSqlString(separator: SqlString): SqlString = SeparatedSqlString(iter, separator)
//  }

  implicit class SqlStringOps(private val left: SqlString) extends AnyVal {
    @inline def ~(r: SqlString): SqlString = Concat(left, r)
    @inline def *(r: SqlString): SqlString = Concat3(left, Space, r)
    @inline def ~*~(r: SqlString): SqlString = left * r
  }


}

sealed trait SqlString {

  def compile(implicit escaper: Escaper): CompiledSql =
    SqlString.unsafe.compile(this, escaper)

  override def toString =
    SqlString.unsafe.compile(this, DefaultJdbcEscaper).value

}
