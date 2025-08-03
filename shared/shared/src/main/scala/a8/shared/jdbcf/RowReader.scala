package a8.shared.jdbcf


import java.time.{Instant, LocalDateTime, LocalTime, OffsetDateTime, ZoneId}
import a8.shared.json.ast.{JsDoc, JsObj, JsVal}

import scala.reflect.ClassTag
import a8.shared.SharedImports.*
import a8.shared.jdbcf.JdbcMetadata.{ResolvedColumn, ResolvedJdbcTable}
import a8.shared.jdbcf.mapper.MapperBuilder

import java.io.BufferedReader
import a8.shared.jdbcf.RowReader.noneAnyRef
import a8.shared.SharedImports.canEqual.given

object RowReader extends MoreRowReaderCodecs with RowReaderTuples {

  val noneAnyRef: AnyRef = None

  trait TupleReader[A] extends RowReader[A] {
    class UnsafeReader(startIndex: Int, row: Row) {
      var offset = 0
      def next[B : RowReader]: B = {
        val t = RowReader[B].rawRead(row, startIndex+offset)
        offset += t._2
        t._1
      }
    }
  }

  def apply[A : RowReader]: RowReader[A] = implicitly[RowReader[A]]

  def singleColumnReader[A : ClassTag](fn: PartialFunction[AnyRef,A]): RowReader[A] =
    new RowReader[A] {
      override def rawRead(row: Row, index: Int): (A, Int) = {
        row.coerceByIndex[A](index)(fn) -> 1
      }
    }

  implicit lazy val intReader: RowReader[Int] = singleColumnReader[Int] {
    case i: java.lang.Number => i.intValue()
  }
  implicit lazy val stringReader: RowReader[String] = singleColumnReader[String] {
    case s: String =>
      s
    case pgo: org.postgresql.util.PGobject =>
      pgo.getValue
    case clob: java.sql.Clob =>
      clob.getCharacterStream.readFully()
  }

  implicit lazy val localDateTimeMapper: RowReader[LocalDateTime] =
    singleColumnReader[LocalDateTime] {
      case ts: java.sql.Timestamp =>
        ts.toLocalDateTime
    }

  implicit lazy val offsetDateTimeMapper: RowReader[OffsetDateTime] = {
    val utc = ZoneId.of("UTC")
    singleColumnReader[OffsetDateTime] {
      case ldt: LocalDateTime =>
        ldt.atOffset(java.time.ZoneOffset.UTC)
      case ts: java.sql.Timestamp =>
        // as suggested here https://stackoverflow.com/questions/43216737/how-to-convert-java-sql-timestamp-to-java-time-offsetdatetime
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(ts.getTime), utc)
    }
  }

  implicit lazy val localTimeMapper: RowReader[LocalTime] =
    singleColumnReader[LocalTime] {
      case ts: java.sql.Time =>
        ts.toLocalTime
    }

  implicit lazy val byteReader: RowReader[Byte] =
    singleColumnReader[Byte] {
      case i: java.lang.Number =>
        i.byteValue()
    }

  implicit lazy val shortReader: RowReader[Short] =
    singleColumnReader[Short] {
      case i: java.lang.Number =>
        i.shortValue()
    }

  implicit lazy val longReader: RowReader[Long] =
    singleColumnReader[Long] {
      case i: java.lang.Number =>
        i.longValue()
    }

  implicit lazy val floatReader: RowReader[Float] =
    singleColumnReader[Float] {
      case i: java.lang.Number =>
        i.floatValue()
    }

  implicit lazy val doubleReader: RowReader[Double] =
    singleColumnReader[Double] {
      case i: java.lang.Number =>
        i.doubleValue()
    }

  implicit lazy val boolean: RowReader[Boolean] =
    singleColumnReader[Boolean] {
      case i: java.lang.Integer =>
        i != 0
      case s: String =>
        s.toLowerCase match {
          case "y" | "true" | "yes" | "1" =>
            true
          case _ =>
            false
        }
      case b: java.lang.Boolean =>
        b

    }

  implicit def JsValReader: RowReader[JsVal] =
    singleColumnReader[JsVal] {
      case v =>
        unsafe.coerceToJsVal(v)
    }

  implicit def optionReader[A : RowReader]: RowReader[Option[A]] =
    new RowReader[Option[A]] {
      val rowReaderA = implicitly[RowReader[A]]
      override def rawRead(row: Row, index: Int): (Option[A], Int) = {
        row.rawValueByIndex(index) match {
          case v if v == noneAnyRef =>
            None -> 1
          case _ =>
            val t = rowReaderA.rawRead(row, index)
            Some(t._1) -> t._2
        }
      }
    }

  implicit val jsdocReader: RowReader[JsDoc] =
    implicitly[RowReader[Option[String]]]
      .map {
        case None =>
          JsDoc.empty
        case Some(jsonStr) =>
          json.unsafeParse(jsonStr).toRootDoc
      }
}

/**
 * Type class for reading values of type A from database result set rows.
 * 
 * RowReader instances define how to convert JDBC ResultSet data into Scala types.
 * Instances are provided for common types and tuples, and can be derived for case classes
 * using the provided macros.
 * 
 * @tparam A The Scala type that this reader can produce from database rows
 * 
 * @example {{{
 * // Implicit instances for basic types are provided
 * val nameReader: RowReader[String] = implicitly[RowReader[String]]
 * 
 * // Tuple readers are automatically available
 * val tupleReader: RowReader[(Long, String, Boolean)] = 
 *   implicitly[RowReader[(Long, String, Boolean)]]
 * 
 * // Case class readers can be derived
 * case class User(id: Long, name: String, active: Boolean)
 * implicit val userReader: RowReader[User] = RowReader.derived[User]
 * 
 * // Use with queries
 * val users = conn.query[User](sql"SELECT id, name, active FROM users").select
 * }}}
 */
trait RowReader[A] { outer =>

  def materialize(columnNamePrefix: ColumnName, conn: Conn, resolvedJdbcTable: ResolvedJdbcTable): RowReader[A] =
    this

  /**
   * Read a value from the row starting at column index 0.
   * 
   * @param row The database row to read from
   * @return The value of type A read from the row
   */
  def read(row: Row): A = read(row, 0)

  /**
   * Read a value from the row starting at the specified column index.
   * 
   * @param row The database row to read from
   * @param index The column index to start reading from (0-based)
   * @return The value of type A read from the row
   * @note index counts from 0 (even though jdbc result set values start from 1)
   */
  final def read(row: Row, index: Int): A = rawRead(row, index)._1

  /**
   * Read a value from the row and return both the value and number of columns consumed.
   * 
   * This is used internally to support reading composite types (like tuples and case classes)
   * where multiple columns need to be read sequentially.
   *
   * @param row The database row to read from
   * @param index The column index to start reading from (0-based)
   * @return A tuple of (value read, number of columns consumed)
   * @note index counts from 0 (even though jdbc result set values start from 1)
   */
  def rawRead(row: Row, index: Int): (A,Int)

  def readOpt(row: Row, index: Int): Option[A] =
    row.rawValueByIndex(index) match {
      case v if v == noneAnyRef =>
        None
      case null =>
        None
      case _ =>
        Some(read(row, index))
    }

  def map[B](fn: A=>B): RowReader[B] =
    new RowReader[B] {
      override def rawRead(row: Row, index: Int): (B, Int) = {
        val t = outer.rawRead(row, index)
        fn(t._1) -> t._2
      }
    }

}
