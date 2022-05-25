package a8.shared.jdbcf

import a8.shared.jdbcf.SqlString.SqlStringer


import java.sql.PreparedStatement
import a8.shared.SharedImports._

object RowWriter {

  def create[A](fn: java.sql.PreparedStatement=>((Int, A)=>Unit))(implicit sqlStringer: SqlStringer[A]): RowWriter[A] = {
    val sqlStringer0 = sqlStringer.some
    new RowWriter[A] {
      val parameterCount = 1
      override def columnNames(columnName: ColumnName): Iterable[ColumnName] = Iterable(columnName)
      override def applyParameters(ps: PreparedStatement, a: A, parameterIndex: Int): Unit = {
        fn(ps)(parameterIndex, a)
      }
      override def sqlString(a: A): Option[SqlString] =
        sqlStringer.toSqlString(a).some
    }
  }

  def createx[A](fn: (java.sql.PreparedStatement, Int, A)=>Unit, parameterCount0: Int = 0): RowWriter[A] =
    new RowWriter[A] {
      val parameterCount = parameterCount0
      override def columnNames(columnName: ColumnName): Iterable[ColumnName] = Iterable(columnName)
      override def applyParameters(ps: PreparedStatement, a: A, parameterIndex: Int): Unit = {
        fn(ps, parameterIndex, a)
      }
      override def sqlString(a: A): Option[SqlString] =
        None
    }

  implicit val byteWriter: RowWriter[Byte] = create[Byte](ps => ps.setByte(_, _))
  implicit val shortWriter: RowWriter[Short] = create[Short](ps => ps.setShort(_, _))
  implicit val integerWriter: RowWriter[Integer] = create[Integer](ps => ps.setInt(_, _))
  implicit val intWriter: RowWriter[Int] = create[Int](ps => ps.setInt(_, _))
  implicit val longWriter: RowWriter[Long] = create[Long](ps => ps.setLong(_, _))
  implicit val floatWriter: RowWriter[Float] = create[Float](ps => ps.setFloat(_, _))
  implicit val doubleWriter: RowWriter[Double] = create[Double](ps => ps.setDouble(_, _))
  implicit val javaMathBigDecimal: RowWriter[java.math.BigDecimal] = create[java.math.BigDecimal](ps => ps.setBigDecimal(_, _))
  implicit val scalaBigDecimal: RowWriter[BigDecimal] = create[BigDecimal](ps => { (pi, bd) => ps.setBigDecimal(pi, bd.bigDecimal) })

  implicit val stringWriter: RowWriter[String] = create[String](ps => ps.setString(_, _))

  def apply[A : RowWriter]: RowWriter[A] = implicitly[RowWriter[A]]

  def mapWriter[A,B](outer: RowWriter[A])(fn: B=>A): RowWriter[B] =
    new RowWriter[B] {
      val parameterCount = outer.parameterCount
      override def columnNames(columnNamePrefix: ColumnName): Iterable[ColumnName] = outer.columnNames(columnNamePrefix)
      override def applyParameters(ps: PreparedStatement, b: B, parameterIndex: Int): Unit =
        outer.applyParameters(ps, fn(b), parameterIndex)

      override def sqlString(b: B): Option[SqlString] =
        outer.sqlString(fn(b))
    }

  implicit def optionRowWriter[A](implicit rowWriterA: RowWriter[A]): RowWriter[Option[A]] =
    new RowWriter[Option[A]] {
      val nullSqlStr = SqlString.keyword("null")
      override val parameterCount: Int = rowWriterA.parameterCount
      override def columnNames(columnNamePrefix: ColumnName): Iterable[ColumnName] = rowWriterA.columnNames(columnNamePrefix)
      override def applyParameters(ps: PreparedStatement, option: Option[A], parameterIndex: Int): Unit = {
        option match {
          case None =>
            ps.setNull(parameterIndex, java.sql.Types.NULL)
          case Some(a) =>
            rowWriterA.applyParameters(ps, a, parameterIndex)
        }
      }
      override def sqlString(opt: Option[A]): Option[SqlString] =
        opt match {
          case None =>
            Some(nullSqlStr)
          case Some(a) =>
            rowWriterA.sqlString(a)
        }

//        : Option[SqlStringer[A]] =
//        outer.sqlStringer.map { outer =>
//          new SqlStringer[B] {
//            override def toSqlString(b: B): SqlString =
//              outer.toSqlString(fn(b))
//          }
//        }
    }

}

trait RowWriter[A] { outer =>
  val parameterCount: Int
  def columnNames(columnNamePrefix: ColumnName): Iterable[ColumnName]
  def applyParameters(ps: java.sql.PreparedStatement, a: A, parameterIndex: Int): Unit
  def mapWriter[B](fn: B=>A): RowWriter[B] = RowWriter.mapWriter(this)(fn)
  def sqlString(a: A): Option[SqlString]
}
