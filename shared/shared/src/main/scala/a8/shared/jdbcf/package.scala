package a8.shared


import java.sql.{ResultSet, SQLException}
import a8.shared.jdbcf.UnsafeResultSetOps.*
import SharedImports.*
import a8.shared.jdbcf.SqlString.CompiledSql
import a8.shared.zreplace.XStream

package object jdbcf extends Logging {

  def resultSetToVector(resultSet: ResultSet): Vector[Row] = {
    resultSet.runAsIterator(_.toVector)
  }

  def resultSetToStream(resultSet: =>ResultSet, chunkSize: Int = 1000): zio.XStream[Row] = {

    def acquire = (resultSet, unsafe.resultSetToIterator(resultSet))

    def release(rs: ResultSet): Unit =
      tryLogDebug("") {
        if ( !resultSet.isClosed )
          resultSet.close()
      }

    XStream.acquireRelease(acquire)(release)

  }

  def withSqlCtx[A](sql: CompiledSql)(fn: =>A): A = {
    logger.debug(s"running sql -- ${sql.value}")
    try {
      val a = fn
      a
    } catch {
      case e: SQLException =>
        throw new SQLException(s"error running -- ${sql.value} -- ${e.getMessage}", e.getSQLState, e.getErrorCode, e)
    }
  }

}
