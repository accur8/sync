package a8.shared

import SharedImports._
import java.sql.ResultSet
import a8.shared.jdbcf.UnsafeResultSetOps._

package object jdbcf {

  def resultSetToVector(resultSet: ResultSet): Vector[Row] = {
    resultSet.runAsIterator(_.toVector)
  }

  def resultSetToStream[F[_] : Sync](resultSet: ResultSet, chunkSize: Int = 1000): fs2.Stream[F,Row] = {
    val F = Sync[F]
    fs2.Stream.bracket(F.unit)(_ => F.blocking(if ( resultSet.isClosed ) () else resultSet.close()))
      .flatMap { _ =>
        fs2.Stream.fromBlockingIterator(unsafe.resultSetToIterator(resultSet), chunkSize)
      }
  }

}
