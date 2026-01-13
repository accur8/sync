package a8.shared.jdbcf

import org.slf4j.LoggerFactory

import java.sql.SQLException

object ConnRetryOps {

  private val logger = LoggerFactory.getLogger(getClass)

  extension (conn: Conn) {
    def withRetry[A](
      config: DatabaseConfig,
      operationName: String
    )(fn: => A): A = {
      val maxAttempts = config.maxRetryAttempts
      val initialDelayMillis = config.retryInitialDelayMillis
      val maxDelayMillis = config.retryMaxDelayMillis
      val backoffMultiplier = config.retryBackoffMultiplier

      retryWithBackoff(
        operation = fn,
        maxAttempts = maxAttempts,
        initialDelayMillis = initialDelayMillis,
        maxDelayMillis = maxDelayMillis,
        backoffMultiplier = backoffMultiplier,
        isTransient = JdbcErrorClassifier.isTransient,
        operationName = operationName
      )
    }
  }

  private def retryWithBackoff[A](
    operation: => A,
    maxAttempts: Int,
    initialDelayMillis: Long,
    maxDelayMillis: Long,
    backoffMultiplier: Double,
    isTransient: Throwable => Boolean,
    operationName: String
  ): A = {
    def attempt(attemptNum: Int): A = {
      try {
        operation
      } catch {
        case ex: Throwable if isTransient(ex) && attemptNum < maxAttempts =>
          // Calculate exponential backoff delay
          val delayMillis = math.min(
            initialDelayMillis * math.pow(backoffMultiplier, attemptNum - 1).toLong,
            maxDelayMillis
          )

          val sqlState = ex match {
            case sqlEx: SQLException => s" (SQLSTATE: ${sqlEx.getSQLState})"
            case _ => ""
          }

          logger.warn(
            s"Transient error in $operationName$sqlState - retry attempt $attemptNum/$maxAttempts after ${delayMillis}ms",
            ex
          )

          // Sleep and retry
          Thread.sleep(delayMillis)
          attempt(attemptNum + 1)

        case ex: Throwable =>
          // Non-transient error or out of retries - fail
          if (attemptNum >= maxAttempts && isTransient(ex)) {
            logger.error(s"$operationName failed after $maxAttempts attempts", ex)
          }
          throw ex
      }
    }

    attempt(1)
  }
}
