package a8.common.logging


import java.lang.Thread.UncaughtExceptionHandler
import LoggingOps._

object LoggingSetupOps {

  def doOneTimeSetup(loggerFactory: LoggerFactory): Unit = {

    def logger = loggerFactory.logger(normalizeClassname(getClass))

    val bootstrapConfig = LoggingBootstrapConfig.globalBootstrapConfig

//    scala.Predef.println("boom")

    lazy val loggerStderr = loggerFactory.logger("stderr")
    lazy val loggerStdout = loggerFactory.logger("stdout")

    if ( bootstrapConfig.setDefaultUncaughtExceptionHandler ) {
      // add uncaught exception handler
//      logger.info(s"setting Thread.setDefaultUncaughtExceptionHandler to delegate to the UncaughtExceptionHandler provided by the ApplicationInjector at the time of the uncaught exception")
      Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler {
        override def uncaughtException(t: Thread, e: Throwable): Unit = {
          val logger = LoggerFactory.logger("UncaughtExceptionHandler")
          logger.fatal(s"uncaughtException on thread ${t.getName} - ${t.getId}", e)
        }
      })
    }

    if ( bootstrapConfig.overrideSystemErr ) {
      // redirect stderr output stream to log at the WARN level
//      logger.info(s"Overriding System.err PrintStream's to goto a WARN logger")
      // this has to happen AFTER logging is configured to avoid recursive System.OUT and System.ERR access.
      // make sure everything sent to System.err goes through logging
      System.setErr(new java.io.PrintStream(new LoggedOutputStream(Level.Warn, loggerStderr)))
    }

    if ( bootstrapConfig.overrideSystemOut ) {
      // redirect stdout output stream to log at the WARN level
//      logger.info(s"Overriding System.out PrintStream's to goto an INFO logger")
      // this has to happen AFTER logging is configured to avoid recursive System.OUT and System.ERR access.
      // make sure everything sent to System.out goes through logging
      System.setOut(new java.io.PrintStream(new LoggedOutputStream(Level.Info, loggerStdout)))
    }

  }

}
