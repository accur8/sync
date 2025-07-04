package a8.shared.jdbcf


import org.typelevel.ci.CIString

import scala.reflect.{ClassTag, classTag}
import a8.shared.SharedImports._
import a8.shared.json.ast.JsObj
import zio._

object Row {

  def apply(values0: Chunk[AnyRef], rowMetadata: Metadata): Row = {
    new Row {
      override protected val metadata: Metadata = rowMetadata
      override protected val values: Chunk[AnyRef] = values0
    }
  }

  object Metadata {
    def from(resultSetMetadata: java.sql.ResultSetMetaData): Metadata = {
      Row.Metadata(
        (1 to resultSetMetadata.getColumnCount).map(i => resultSetMetadata.getColumnName(i)).toVector
      )
    }
  }

  case class Metadata(columnNames: Vector[String]) {
    lazy val ciColumnNames: Vector[CIString] = columnNames.map(CIString.apply)
    lazy val columnIndexesByName: Map[CIString, Int] = ciColumnNames.iterator.zipWithIndex.map(t => t._1 -> t._2).toMap
    def columnIndex(name: String): Int =
      columnIndexesByName
        .get(CIString(name))
        .getOrElse(
          sys.error(s"cannot find column ${name}")
        )
  }

  implicit val rowReader: RowReader[Row] =
    new RowReader[Row] {
      override def rawRead(row: Row, index: Int): (Row, Int) = {
        val subRow = row.subRow(index)
        subRow -> subRow.size
      }
    }
}

trait Row { outer =>

  def columnIndex(name: String): Int = metadata.columnIndex(name)

  def size = values.size

  protected val metadata: Row.Metadata
  protected val values: Chunk[AnyRef]

  lazy val unsafeAsJsObj: JsObj = {
    JsObj(
      metadata
        .columnNames
        .iterator
        .zip(values.iterator.map(unsafe.coerceToJsVal))
        .toMap
    )
  }

  lazy val asMap: Map[CIString, AnyRef] =
    metadata
      .ciColumnNames
      .iterator
      .zip(values.iterator)
      .toMap

  /**
   * indexed from 0 like any sane api should
   */
  def coerceByIndex[A : ClassTag](i: Int)(pf: PartialFunction[AnyRef, A]): A = {
    val v = values(i)
    if ( pf.isDefinedAt(v) )
      pf.apply(v)
    else
      sys.error(s"unable to coerce ${v} ${v.getClass.getName} into ${classTag[A].runtimeClass.getName}")
  }

  def coerceByName[A : ClassTag](name: String)(pf: PartialFunction[AnyRef, A]): A =
    coerceByIndex(metadata.columnIndex(name))(pf)

  def rawValueByName(name: String): AnyRef = values(metadata.columnIndex(name))
  def rawValueByIndex(i: Int): AnyRef = values(i)
  def opt[T](i: Int)(implicit mapper: RowReader[T]): Option[T] = mapper.readOpt(this, i)
  def opt[T](name: String)(implicit mapper: RowReader[T]): Option[T] = mapper.readOpt(this, columnIndex(name))
  def get[T](i: Int)(implicit mapper: RowReader[T]): T = mapper.read(this, i)
  def get[T](name: String)(implicit mapper: RowReader[T]): T = mapper.read(this, columnIndex(name))

  def subRow(start: Int): Row =
    if ( start == 0 )
      this
    else
      new Row {
        override protected val metadata: Row.Metadata = Row.Metadata(outer.metadata.columnNames.drop(start))
        override protected val values: Chunk[AnyRef] = outer.values.drop(start)
      }

  override def toString: String = {
    try {
      s"Row(${values.map(_.toString).mkString(",")})"
    } catch {
      case e: Exception =>
        ""
    }
  }

}

