package a8.hermes.rpc

import a8.hermes.proto.process.wsmessages.{ErrorFrame, ErrorLocation, ErrorNode}
import com.google.protobuf.struct.Struct
import com.google.protobuf.timestamp.Timestamp

import java.time.Instant

/**
 * Error categories for RPC errors.
 * These map to the category field in ErrorFrame.
 */
object ErrorCategory {
  val RPC = "RPC"
  val AUTH = "AUTH"
  val CLIENT = "CLIENT"
  val RESOURCE = "RESOURCE"
  val DEPENDENCY = "DEPENDENCY"
  val INTERNAL = "INTERNAL"
}

/**
 * Error kinds by category.
 */
object ErrorKind {
  // RPC category
  val BAD_REQUEST = "BAD_REQUEST"
  val TIMEOUT = "TIMEOUT"
  val CANCELLED = "CANCELLED"

  // AUTH category
  val UNAUTHENTICATED = "UNAUTHENTICATED"
  val PERMISSION_DENIED = "PERMISSION_DENIED"

  // CLIENT category
  val INVALID_ARGUMENT = "INVALID_ARGUMENT"
  val NOT_FOUND = "NOT_FOUND"
  val CONFLICT = "CONFLICT"

  // RESOURCE category
  val RATE_LIMITED = "RATE_LIMITED"
  val QUOTA_EXCEEDED = "QUOTA_EXCEEDED"
  val OVERLOADED = "OVERLOADED"

  // DEPENDENCY category
  val DB_UNAVAILABLE = "DB_UNAVAILABLE"
  val NATS_UNAVAILABLE = "NATS_UNAVAILABLE"
  val UPSTREAM_UNAVAILABLE = "UPSTREAM_UNAVAILABLE"

  // INTERNAL category
  val UNEXPECTED = "UNEXPECTED"
  val INVARIANT_VIOLATION = "INVARIANT_VIOLATION"
}

/**
 * Utilities for creating and manipulating RPC errors using the ErrorNode structure.
 *
 * The error model is recursive:
 * - Each error has an ErrorFrame with category, kind, message, etc.
 * - Each error can have zero or more causes (which are themselves ErrorNodes)
 */
object RpcError {

  /**
   * Create a simple error with no causes.
   *
   * @param category Error category (use ErrorCategory constants)
   * @param kind Error kind (use ErrorKind constants)
   * @param message Human-readable error message
   * @param retryable Whether retry is reasonable without changing inputs
   * @param details Optional structured details (Map[String, String] will be converted to Struct)
   * @param component Logical component name (e.g., "auth-service")
   * @param location Optional source location for debugging
   * @return ErrorNode with no causes
   */
  def simple(
    category: String,
    kind: String,
    message: String,
    retryable: Boolean = false,
    details: Map[String, String] = Map.empty,
    component: String = "scala-client",
    location: Option[ErrorLocation] = None,
  ): ErrorNode = {
    ErrorNode(
      frame = Some(ErrorFrame(
        category = category,
        kind = kind,
        message = message,
        retryable = retryable,
        details = Some(mapToStruct(details)),
        component = component,
        location = location,
        timestamp = Some(Timestamp(Instant.now().getEpochSecond, Instant.now().getNano)),
      )),
      causes = Seq.empty,
    )
  }

  /**
   * Wrap an existing error with a new error frame.
   * This creates a causal chain: new error caused by existing error.
   *
   * @param category Error category for the wrapper
   * @param kind Error kind for the wrapper
   * @param message Human-readable message
   * @param cause The underlying cause (existing ErrorNode)
   * @param retryable Whether retry is reasonable
   * @param details Optional structured details
   * @param component Logical component name
   * @return ErrorNode with the cause attached
   */
  def wrap(
    category: String,
    kind: String,
    message: String,
    cause: ErrorNode,
    retryable: Boolean = false,
    details: Map[String, String] = Map.empty,
    component: String = "scala-client",
  ): ErrorNode = {
    ErrorNode(
      frame = Some(ErrorFrame(
        category = category,
        kind = kind,
        message = message,
        retryable = retryable,
        details = Some(mapToStruct(details)),
        component = component,
        location = None,
        timestamp = Some(Timestamp(Instant.now().getEpochSecond, Instant.now().getNano)),
      )),
      causes = Seq(cause),
    )
  }

  /**
   * Create an error from a Scala exception.
   * Uses INTERNAL/UNEXPECTED category/kind by default.
   *
   * @param exception The Scala exception
   * @param category Error category (defaults to INTERNAL)
   * @param kind Error kind (defaults to UNEXPECTED)
   * @param component Logical component name
   * @return ErrorNode representing the exception
   */
  def fromException(
    exception: Throwable,
    category: String = ErrorCategory.INTERNAL,
    kind: String = ErrorKind.UNEXPECTED,
    component: String = "scala-client",
  ): ErrorNode = {
    val location = exception.getStackTrace.headOption.map { frame =>
      ErrorLocation(
        file = frame.getFileName,
        line = frame.getLineNumber,
        function = s"${frame.getClassName}.${frame.getMethodName}",
      )
    }

    val details = Map(
      "exceptionClass" -> exception.getClass.getName,
    )

    val causeNode = Option(exception.getCause).map(cause =>
      fromException(cause, category, kind, component)
    )

    ErrorNode(
      frame = Some(ErrorFrame(
        category = category,
        kind = kind,
        message = Option(exception.getMessage).getOrElse(exception.getClass.getSimpleName),
        retryable = false,
        details = Some(mapToStruct(details)),
        component = component,
        location = location,
        timestamp = Some(Timestamp(Instant.now().getEpochSecond, Instant.now().getNano)),
      )),
      causes = causeNode.toSeq,
    )
  }

  /**
   * Convert a Map[String, String] to a protobuf Struct.
   * This is used for the details field in ErrorFrame.
   */
  private def mapToStruct(details: Map[String, String]): Struct = {
    import com.google.protobuf.struct.Value
    import com.google.protobuf.struct.Value.Kind

    val fields = details.map { case (k, v) =>
      k -> Value(kind = Kind.StringValue(v))
    }

    Struct(fields = fields)
  }

  /**
   * Extract a human-readable error message from an ErrorNode.
   * Walks the cause chain to build a complete message.
   *
   * @param error The error node
   * @param includeCategory Whether to include category/kind in output
   * @return Human-readable error message
   */
  def formatError(error: ErrorNode, includeCategory: Boolean = true): String = {
    error.frame match {
      case None => "Unknown error (missing frame)"
      case Some(frame) =>
        val prefix = if (includeCategory) {
          s"[${frame.category}/${frame.kind}] "
        } else {
          ""
        }

        val mainMessage = s"$prefix${frame.message}"

        if (error.causes.isEmpty) {
          mainMessage
        } else {
          val causeMessages = error.causes.map(formatError(_, includeCategory = false))
          s"$mainMessage\n  Caused by: ${causeMessages.mkString("\n  Caused by: ")}"
        }
    }
  }

}
