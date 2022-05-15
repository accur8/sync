package a8.shared


import SharedImports._
import java.sql.ResultSet
import a8.shared.jdbcf.UnsafeResultSetOps._
import zio._

package object jdbcf {

  def resultSetToVector(resultSet: ResultSet): Vector[Row] = {
    resultSet.runAsIterator(_.toVector)
  }

  def resultSetToStream(resultSet: ResultSet, chunkSize: Int = 1000): XStream[Row] = {
    ???
//    fs2.Stream.bracket(F.unit)(_ => F.blocking(if ( resultSet.isClosed ) () else resultSet.close()))
//      .flatMap { _ =>
//        fs2.Stream.fromBlockingIterator(unsafe.resultSetToIterator(resultSet), chunkSize)
//      }
  }

}
