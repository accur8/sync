package a8.common.logging


import LoggingOps._

/**
 * Factory methods for creating Logger instances.
 */
object Logging {

  def logger(implicit trace: Trace): Logger =
    LoggerFactory.logger(trace.loggerName)

  def logger(clazz: Class[?]): Logger =
    LoggerFactory.logger(normalizeClassname(clazz))

}

/**
 * Mix-in trait that provides logging capabilities to any class.
 * 
 * This trait automatically creates a logger instance based on the class name.
 * The logger is marked as protected, making it available to the class and its subclasses.
 * 
 * @example {{{
 * class MyService extends Logging {
 *   def processData(data: String): Unit = {
 *     logger.info(s"Processing data: $data")
 *     
 *     try {
 *       // Process data
 *       logger.debug("Data processed successfully")
 *     } catch {
 *       case e: Exception =>
 *         logger.error("Failed to process data", e)
 *     }
 *   }
 * }
 * }}}
 * 
 * @note The logger is lazy and transient, meaning it's created on first use and not serialized
 */
trait Logging {

  @transient implicit protected lazy val logger: Logger = Logging.logger(getClass)

}

/**
 * Alternative to [[Logging]] trait that exposes the logger as public.
 * 
 * Use this when you need the logger to be accessible from outside the class,
 * though generally it's better to keep logging internal to the class.
 */
trait PublicLogging {

  @transient implicit lazy val logger: Logger = Logging.logger(getClass)

}

