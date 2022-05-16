package a8.shared


import a8.shared.app.{Logging, LoggingF}

import java.sql.ResultSet
import a8.shared.jdbcf.UnsafeResultSetOps._
import zio._
import zio.stream.ZStream
import SharedImports._

package object jdbcf extends LoggingF {

  def resultSetToVector(resultSet: ResultSet): Vector[Row] = {
    resultSet.runAsIterator(_.toVector)
  }

  def resultSetToStream(resultSet: ResultSet, chunkSize: Int = 1000): XStream[Row] = {

    val acquire = ZIO.succeed(resultSet)

    def release(rs: ResultSet): UIO[Unit] =
      ZIO
        .attemptBlocking {
          if ( resultSet.isClosed )
            ()
          else
            resultSet.close()
        }
        .catchAllAndLog

    ZStream.acquireReleaseWith(acquire)(release)
      .flatMap { _ =>
        logger.warn("we are blocking here but ZIO hasn't given us a blocking option")
        ZStream.blocking(
          ZStream.fromIterator(
            unsafe.resultSetToIterator(resultSet),
            chunkSize,
          )
        )
      }
  }

}
