package a8.shared.jdbcf


import java.sql.ResultSet
import language.implicitConversions

object UnsafeResultSetOps {
  implicit def asImplicit(resultSet: ResultSet): UnsafeResultSetOps =
    new UnsafeResultSetOps(resultSet)
}

class UnsafeResultSetOps(resultSet: ResultSet) {

  def runAsIterator[A](fn: Iterator[Row]=>A): A = {
    val iter = unsafe.resultSetToIterator(resultSet)
    try {
      fn(iter)
    } finally {
      resultSet.close()
    }
  }
}
