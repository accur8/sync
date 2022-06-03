package a8.shared


import a8.shared.app.{Logging, LoggingF}

import java.sql.{ResultSet, SQLException}
import a8.shared.jdbcf.UnsafeResultSetOps._
import zio._
import zio.stream.ZStream
import SharedImports._
import a8.shared.jdbcf.SqlString.CompiledSql

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

  def withSqlCtxT[R,A](sql: CompiledSql, effect: ZIO[R,Throwable,A]): ZIO[R,Throwable,A] =
    (
      loggerF.debug(s"running sql -- ${sql.value}")
        *> effect
      ) catchSome {
      case e: java.sql.SQLException =>
        throw new SQLException(s"error running -- ${sql.value} -- ${e.getMessage}", e.getSQLState, e.getErrorCode, e)
    }

  def withSqlCtx[A](sql: CompiledSql)(fn: =>A): Task[A] =
    withSqlCtxT(
      sql,
      ZIO.attemptBlocking(fn),
    )

}
