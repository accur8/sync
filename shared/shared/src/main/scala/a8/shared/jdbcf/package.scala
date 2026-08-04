package a8.shared


import java.sql.{ResultSet, SQLException}
import a8.shared.jdbcf.UnsafeResultSetOps.*
import SharedImports.*
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import a8.shared.jdbcf.SqlString.CompiledSql
import a8.shared.zreplace.XStream

package object jdbcf extends Logging {

  def resultSetToVector(resultSet: ResultSet): Vector[Row] = {
    resultSet.runAsIterator(_.toVector)
  }

  // chunkSize is gone. It was a leftover from the zio-stream era — XStream.acquireRelease
  // takes no chunk size, so nothing consumed it, and StreamingQuery was passing its
  // batchSize into a void. Batching is still in effect there: it comes from the
  // st.setFetchSize(batchSize) on the statement, which is where JDBC actually does it.
  def resultSetToStream(resultSet: =>ResultSet, releaseListener: ()=>Unit): zio.XStream[Row] = {

    def acquire = (resultSet, unsafe.resultSetToIterator(resultSet))

    def release(rs: ResultSet): Unit = {
      tryLogDebug("") {
        if ( !rs.isClosed )
          rs.close()
      }
      tryLogDebug("") {
        releaseListener()
      }
    }

    XStream.acquireRelease(acquire)(release)

  }

  def withSqlCtx[A](databaseId: DatabaseId, sql: CompiledSql)(fn: =>A): A = {
    if logger.isTraceEnabled then
      logger.trace(s"running ${databaseId.value} sql -- ${sql.value}")
    try {
      val a = fn
      a
    } catch {
      case e: SQLException =>
        throw new SQLException(s"error running -- ${sql.value} -- ${e.getMessage}", e.getSQLState, e.getErrorCode, e)
    }
  }

}
