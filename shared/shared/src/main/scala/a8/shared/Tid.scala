package a8.shared


import java.util.UUID
import a8.shared.jdbcf.{RowReader, RowWriter, SqlString}
import a8.shared.jdbcf.SqlString.SqlStringer
import a8.shared.json.{JsonTypedCodec, ReadError, ast}
import a8.shared.json.ast.JsStr

import scala.util.Random
import language.implicitConversions
import SharedImports._

import java.sql.PreparedStatement

object Tid {

  def random[A](length: Int = 32) =
    Tid[A](value = Random.alphanumeric.take(length).mkString)

  trait DataSetOps[A] {
    def fetch(key: Tid[A]): A
    def fetchOpt(key: Tid[A]): Option[A]
  }

  implicit class DataOps[A](tid: Tid[A])(implicit dataSetOps: DataSetOps[A]) {
    def fetch: A = dataSetOps.fetch(tid)
    def fetchOpt: Option[A] = dataSetOps.fetchOpt(tid)
  }

  implicit def jsonTypedCodec[A]: JsonTypedCodec[Tid[A],JsStr] =
    JsonTypedCodec.string.dimap(
      s => Tid[A](s),
      t => t.value,
    )

  implicit def zioEq[A]: zio.prelude.Equal[Tid[A]] =
    zio.prelude.Equal.make((a, b) => a.value == b.value)

  implicit def catsEq[A]: cats.Eq[Tid[A]] = cats.Eq.fromUniversalEquals

  implicit def toSqlString[A](tid: Tid[A]): SqlString =
    SqlString.escapedString(tid.value)

  implicit def sqlStringer[A]: SqlStringer[Tid[A]] =
    new SqlStringer[Tid[A]] {
      override def toSqlString(a: Tid[A]): SqlString =
        SqlString.escapedString(a.value)
    }

  def isValidChar(ch: Char) =
    ch.isLetterOrDigit

  class Generator[A] {

    def random(length: Int = 32): Tid[A] = Tid.random(length)

    def unapply(value: String): Option[Tid[A]] =
      value
        .forall(isValidChar)
        .toOption(Tid[A](value))

  }

  implicit def rowWriter[A]: RowWriter[Tid[A]] =
    RowWriter.stringWriter.mapWriter[Tid[A]](_.value)

  implicit def rowReader[A]: RowReader[Tid[A]] =
    RowReader.stringReader.map(v => Tid[A](v))

}


case class Tid[A](value: String) extends StringValue {

}
