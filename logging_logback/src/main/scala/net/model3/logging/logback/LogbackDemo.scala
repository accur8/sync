package net.model3.logging.logback

import a8.common.logging.{LoggingBootstrapConfig, Level, LoggerFactory}
import ch.qos.logback.classic.Logger
import ch.qos.logback.core.status.InfoStatus
import org.fusesource.jansi.AnsiConsole

import java.io.{FileDescriptor, FileOutputStream, PrintStream}

object LogbackDemo {

  /*

  override def defaultBootstrapConfig: LoggingBootstrapConfig =
    super.defaultBootstrapConfig.copy(
      fileLogging = true,
      consoleLogging = true,
    )

  def main(args: Array[String]): Unit = {

    val intellij =
      try {
        getClass.getClassLoader().loadClass("com.intellij.rt.execution.application.AppMainV2")
        true
      } catch {
        case  _: Throwable =>
          false
      }

    val props = System.getProperty("java.library.path")
    val allProps = System.getProperties
    val classpath = System.getProperty("java.class.path")

    logger.trace("Hello world trace.")
    logger.debug("Hello world debug.")
    logger.info("Hello world info.")
    logger.warn("Hello world warn.")

    val thread =
      new Thread {
        start()
        override def run(): Unit =
          sys.error("exception to test uncaught exception handler")
      }

    thread.join()

    System.err.println("output to stderr")
    System.out.println("output to stdout")

//    while ( true ) {
//      logger.info("askjdlhasdlkfhjsadhfjklsfhdkjlafhdkjlfhaklsfhkljasdhlkasjfhsalkjdfhaskljdhaskjd")
//    }

//    logger.info(s"again logging status messsages\n${LoggerFactory.configurationStatusString().getOrElse("").linesIterator.map("        " + _).mkString("\n")}")

    LogbackLoggerFactory.loggerContext.getStatusManager.add(new InfoStatus("test status message", this))

    logger.info("hello bob")

//    while ( true ) {
//      logger.trace("trace")
//      logger.debug("debug")
//      logger.info("info")
//      logger.warn("warn")
//      logger.error("error")
//      logger.fatal("fatal")
//      Thread.sleep(15000)
//    }

    LoggerFactory.withContext("billy_bob") {
      logger.debug("this should print under the billy_bob context")
    }

    logger.debug(s"system console is ${System.console()}")

    logger.warn("another boom", new Throwable())

    {
      val out = new PrintStream(new FileOutputStream(FileDescriptor.out))
      import org.fusesource.jansi.Ansi._
      import Color._
      out.println(ansi().eraseScreen().fg(RED).a("Hello").fg(GREEN).a(" World").reset())

      logger.debug(s"intellij ${intellij}")

    }

    val julLoggerName = "java_util_logger"

    val julLogger = java.util.logging.Logger.getLogger(julLoggerName)
    julLogger.info("this is a java util logger")
    julLogger.fine("logging debug via julLogger")

    val julLoggerAsLogger = LoggerFactory.logger(julLoggerName)
    julLoggerAsLogger.info("same logger through a8 common Logger")
    julLoggerAsLogger.debug("logging debug via julLoggerAsLogger")

    julLogger.fine("logging debug via julLogger")
    julLoggerAsLogger.debug("logging info via julLoggerAsLogger ")

    julLoggerAsLogger.info("2 debug messages logged we should see them")

    julLoggerAsLogger.setLevel(Level.Info)
    julLoggerAsLogger.debug("logging debug again nobody should see this")
    julLogger.fine("logging debug nobody again should see this")

    julLoggerAsLogger.info("2 debug messages logged do we see them ??  we shouldn't")


    LogbackLoggerFactory.loggerContext.getStatusManager.add(new InfoStatus("test status message", this))

  }
 */
}
