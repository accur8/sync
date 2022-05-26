package a8.shared.jdbcf

import a8.shared.jdbcf.JdbcMetadata.JdbcColumn
import a8.shared.jdbcf.SqlString.{CompositeSqlString, CompositeString, Concat3, DoubleQuote, HasSqlString, SeparatedSqlString}

import scala.language.implicitConversions

object SqlStringLowPrio {

  class SqlFragmentContext(private val stringContext: StringContext) extends AnyVal {
    def q(args: SqlString*): SqlString = sql(args:_*)
    def sql(args: SqlString*): SqlString = {
      val parts =
        stringContext
          .parts
          .iterator
          .zipWithIndex
          .flatMap { case (s, i) =>
            val prefix = SqlString.unsafe.rawSqlString(s)
            if ( i < args.size ) {
              List(prefix, args(i))
            } else {
              List(prefix)
            }
          }
          .to(Iterable)
      CompositeSqlString(parts)
    }
  }

  class StringOpsSqlString(private val v: String) extends AnyVal {
    def keyword = SqlString.unsafe.rawSqlString(v)
    def identifier = SqlString.DialectQuotedIdentifier(v)
    def escape = SqlString.EscapedSqlString(v)
    def unsafeUnEscaped = SqlString.unsafe.rawSqlString(v)
  }

  class IterableSqlString[A <: SqlString](private val iterable: Iterable[A]) extends AnyVal {

    def mkSqlString: SqlString =
      CompositeSqlString(iterable)

    def mkSqlString(separator: SqlString): SqlString =
      SeparatedSqlString(iterable, separator)

    def mkSqlString(prefix: SqlString, separator: SqlString, suffix: SqlString): SqlString =
      Concat3(
        prefix,
        SeparatedSqlString(iterable, separator),
        suffix,
      )

  }

  class IteratorSqlString[A <: SqlString](private val iterator: Iterator[A]) extends AnyVal {
    def mkSqlString(separator: SqlString): SqlString =
      iterator
        .to(Iterable)
        .mkSqlString(separator)
  }

}

trait SqlStringLowPrio {

  import SqlStringLowPrio._

  implicit def iteratorSqlString[A <: SqlString](iterator: Iterator[A]): IteratorSqlString[A] = new IteratorSqlString(iterator)

  implicit def iterableSqlString[A <: SqlString](iterable: Iterable[A]): IterableSqlString[A] = new IterableSqlString(iterable)

  implicit def stringOpsSqlString(s: String): StringOpsSqlString = new StringOpsSqlString(s)

  implicit def sqlStringContextImplicit(sc: StringContext): SqlFragmentContext =
    new SqlFragmentContext(sc)

  implicit def liftJdbcColumn(jdbcColumn: JdbcColumn): SqlString = {
    // this has come from the database so we can force quote it and know we are okay
    CompositeString(Iterable(DoubleQuote, jdbcColumn.columnName.asString, DoubleQuote))
  }

  implicit def liftAnyVal(a: AnyVal): SqlString =
    SqlString.unsafe.rawSqlString(a.toString)

  implicit def liftHasSqlString(hss: HasSqlString): SqlString =
    hss.asSqlFragment

  implicit def liftNumber(n: java.lang.Number): SqlString =
    SqlString.unsafe.rawSqlString(n.toString)

  implicit def liftOptionSqlString(o: Option[SqlString]): SqlString =
    o.getOrElse(SqlString.Empty)

}
