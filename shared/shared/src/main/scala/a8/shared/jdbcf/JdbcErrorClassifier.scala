package a8.shared.jdbcf

import a8.shared.SharedImports.canEqual.given

import java.sql.SQLException

object JdbcErrorClassifier {

  sealed trait ErrorCategory
  case object ConnectionError extends ErrorCategory      // 08xxx - retry
  case object StatementSizeError extends ErrorCategory   // SQL0101 - split batch
  case object AuthorizationError extends ErrorCategory   // SQL0551 - fail fast
  case object NotFoundError extends ErrorCategory        // SQL0204 - expected
  case object UnknownError extends ErrorCategory         // Other - fail

  def classify(ex: SQLException): ErrorCategory = {
    val sqlState = Option(ex.getSQLState).getOrElse("")
    val errorCode = ex.getErrorCode
    val message = Option(ex.getMessage).getOrElse("").toLowerCase

    // Connection errors (SQLSTATE 08xxx) - these are transient
    if (sqlState.startsWith("08")) {
      ConnectionError
    }
    // Transaction rollback errors (SQLSTATE 40xxx) - also transient
    else if (sqlState.startsWith("40")) {
      ConnectionError
    }
    // DB2 for i specific: Statement too large/complex
    else if (errorCode == -101 || message.contains("sql0101")) {
      StatementSizeError
    }
    // DB2 for i specific: Authorization error
    else if (errorCode == -551 || message.contains("sql0551")) {
      AuthorizationError
    }
    // DB2 for i specific: Object not found
    else if (errorCode == -204 || message.contains("sql0204")) {
      NotFoundError
    }
    // Check message for connection-related keywords
    else if (message.contains("connection") &&
             (message.contains("closed") || message.contains("broken") ||
              message.contains("does not exist") || message.contains("timeout"))) {
      ConnectionError
    }
    else {
      UnknownError
    }
  }

  def isTransient(ex: SQLException): Boolean = {
    classify(ex) == ConnectionError
  }

  def isTransient(ex: Throwable): Boolean = ex match {
    case sqlEx: SQLException => isTransient(sqlEx)
    case _ => false
  }
}
