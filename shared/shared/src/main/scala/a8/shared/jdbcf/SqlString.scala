package a8.shared.jdbcf

import java.time.{LocalDate, LocalDateTime, LocalTime}
import a8.shared.jdbcf.TypeName

import scala.language.implicitConversions

object SqlString extends SqlStringLowPrio {

  val DoubleQuote = '"'.toString
  val Null: SqlString = keyword("null")
  val Comma = keyword(",")


  case object Empty extends SqlString
  case class RawSqlString(sqlSafeValue: String) extends SqlString
  case class EscapedSqlString(escapeMe: String) extends SqlString
  case class DialectQuotedIdentifier(value: String) extends SqlString
  case class Concat(l: SqlString, r: SqlString) extends SqlString
  case class Concat3(l: SqlString, m: SqlString, r: SqlString) extends SqlString
  case class SeparatedSqlString(iterable: Iterable[SqlString], separator: SqlString) extends SqlString
  case class CompositeSqlString(parts: Iterable[SqlString]) extends SqlString
  case class CompositeString(parts: Iterable[String]) extends SqlString

  case class Context(
    dialect: Dialect,
    jdbcConn: Option[java.sql.Connection] = None,
  )

  object unsafe {

    def rawSqlString(s: String) = RawSqlString(s)

    def resolve(sqlString: SqlString)(implicit dialect: Dialect): ResolvedSql = {
      implicit val ctx = Context(dialect)

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
            sb.append(dialect.sqlEscapeStringValue(s))
          case CompositeString(parts) =>
            parts.foreach(sb.append)
          case CompositeSqlString(parts) =>
            parts.foreach(run)
          case DialectQuotedIdentifier(v) =>
            sb.append(dialect.sqlQuotedIdentifier(v))
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

      ResolvedSql(sb.toString())

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

  case class ResolvedSql(value: String)


  trait HasSqlString extends SqlString {
    def asSqlFragment: SqlString
  }

  object SqlStringer {

    implicit val hasSqlString: SqlStringer[HasSqlString] =
      new SqlStringer[HasSqlString] {
        override def toSqlString(a: HasSqlString): SqlString = a.asSqlFragment
      }

    implicit def toSqlStringer[A](implicit fn: A=>SqlString): SqlStringer[A] =
      new SqlStringer[A] {
        override def toSqlString(a: A): SqlString = fn(a)
      }

    implicit val stringSqlStringer: SqlStringer[String] =
      new SqlStringer[String] {
        override def toSqlString(a: String): SqlString = a.escape
      }

    implicit val optionStringer: SqlStringer[Option[String]] =
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

  }

  trait SqlStringer[A] {
    def toSqlString(a: A): SqlString
  }

  implicit def localDate(ld: LocalDate): SqlString =
    java.sql.Date.valueOf(ld).toString.escape

  implicit def localDateTime(ldt: LocalDateTime): SqlString =
    java.sql.Timestamp.valueOf(ldt).toString.escape

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

}

sealed trait SqlString {
  override def toString = SqlString.unsafe.resolve(this)(Dialect.Default).value
}
