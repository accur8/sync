package a8.shared.jdbcf

import a8.shared.SharedImports._
import a8.shared.jdbcf.SqlString.SqlStringer
import a8.shared.json.JsonCodec
import a8.shared.json.JsonReader.JsonReaderOptions

object JsonCodecMapper {
  def apply[A: JsonCodec](implicit jsonReaderOptions: JsonReaderOptions): JsonCodecMapper[A] =
    new JsonCodecMapper[A]
}

class JsonCodecMapper[A : JsonCodec](implicit jsonReaderOptions: JsonReaderOptions) extends SqlStringer[A] with RowReader[A] {

  override def materialize(conn: Conn, resolvedColumn: JdbcMetadata.ResolvedColumn): zio.Task[SqlStringer[A]] = {
    for {
      delegate <- SqlStringer.jsDocSqlStringer.materialize(conn, resolvedColumn)
    } yield
      new SqlStringer[A] {
        override def toSqlString(a: A): SqlString =
          delegate.toSqlString(a.toJsRootDoc)
      }
  }

  override def toSqlString(a: A): SqlString =
    SqlStringer.jsDocSqlStringer.toSqlString(a.toJsRootDoc)

  /**
   * returns the value and the number of values read
   *
   * index counts from 0 (even though jdbc result set values start from 1)
   *
   */
  override def rawRead(row: Row, index: Int): (A, Int) = {
    val t = RowReader.jsdocReader.rawRead(row, index)
    t._1.unsafeAs[A] -> t._2
  }

}
