package a8.shared.jdbcf

import java.sql.PreparedStatement


object RowWriter {

  def create[A](fn: java.sql.PreparedStatement=>((Int, A)=>Unit)): RowWriter[A] =
    new RowWriter[A] {
      val parameterCount = 1
      override def write(ps: PreparedStatement, parameterIndex: Int, a: A): Unit = {
        fn(ps)(parameterIndex, a)
      }
    }

  def createx[A](fn: (java.sql.PreparedStatement, Int, A)=>Unit, parameterCount0: Int = 0): RowWriter[A] =
    new RowWriter[A] {
      val parameterCount = parameterCount0
      override def write(ps: PreparedStatement, parameterIndex: Int, a: A): Unit = {
        fn(ps, parameterIndex, a)
      }
    }

  implicit val intWriter = create[Int](ps => ps.setInt(_, _))
  implicit val stringWriter = create[String](ps => ps.setString(_, _))

}

trait RowWriter[A] { outer =>
  val parameterCount: Int
  def write(ps: java.sql.PreparedStatement, parameterIndex: Int, a: A): Unit

  def map[B](fn: B=>A): RowWriter[B] =
    new RowWriter[B] {
      val parameterCount = outer.parameterCount
      override def write(ps: PreparedStatement, parameterIndex: Int, b: B): Unit =
        outer.write(ps, parameterIndex, fn(b))
    }

}
