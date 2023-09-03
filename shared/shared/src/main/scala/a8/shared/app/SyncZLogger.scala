package a8.shared.app


import a8.common.logging.Pos
import a8.shared.{AtomicMap, AtomicRef}
import org.slf4j.MDC
import zio.{Cause, FiberId, FiberRef, LogLevel, LogSpan, Runtime, Trace, ZLayer, ZLogger}
import zio.logging.LogFormat
import a8.shared.SharedImports.*
import a8.shared.SharedImports.canEqual.given

import java.util

object SyncZLogger {

  case class Leveler(
    logLevel: LogLevel,
    isEnabled: org.slf4j.Logger => Boolean
  )

  val levelers: List[Leveler] =
    List(
      Leveler(LogLevel.Trace, _.isTraceEnabled()),
      Leveler(LogLevel.Debug, _.isDebugEnabled()),
      Leveler(LogLevel.Info, _.isInfoEnabled()),
      Leveler(LogLevel.Warning, _.isWarnEnabled()),
      Leveler(LogLevel.Error, _.isErrorEnabled()),
      Leveler(LogLevel.Fatal, _.isErrorEnabled()),
    )

  val levelersByLogLevel: Map[LogLevel,Leveler] =
    levelers
      .toMapTransform(_.logLevel)

  case class CachedLogger(
    zioTrace: String,
    loggerName: String,
    slf4jLogger: org.slf4j.Logger,
    a8Logger: a8.common.logging.Logger,
  ) {
    implicit val pos: Pos = {
      val (fileName, lineNo) =
        (zioTrace.indexOf("("), zioTrace.lastIndexOf(":")) match {
          case (-1, _) | (_, -1) =>
            "" -> -1
          case (i, j) =>
            zioTrace.substring(i + 1, j) -> zioTrace.substring(j + 1, zioTrace.length - 1).toInt
        }
      Pos(sourcecode.FileName(fileName), sourcecode.Line(lineNo))
    }
  }

  def slf4jLayer(minLevel: LogLevel): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(slf4jZLogger(minLevel))

  /**
   * lots of mutable code because we feel a strong need to be performant here
   */
  private def slf4jZLogger(minLevel: LogLevel): ZLogger[String, Unit] =

    new ZLogger[String, Unit] {

      val cachedLoggers = AtomicMap[Trace, CachedLogger]

      override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        textMessage: () => String,
        cause: Cause[Any],
        context: zio.FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Unit = {

        if (logLevel.ordinal >= minLevel.ordinal) {
          //        formatLogger(trace, fiberId, logLevel, message, cause, context, spans, annotations).foreach { message =>

          val cachedLogger: CachedLogger =
            cachedLoggers.get(trace) match {
              case Some(cl) =>
                cl
              case None =>
                val traceStr = trace.toString
                val loggerName =
                  traceStr
                    .splitList("\\(", limit = 2)
                    .headOption
                    .getOrElse(traceStr)
                    .intern()
                val cl = CachedLogger(traceStr, loggerName, org.slf4j.LoggerFactory.getLogger(loggerName), a8.common.logging.LoggerFactory.logger(loggerName))
                cachedLoggers += (trace -> cl): @scala.annotation.nowarn
                cl
            }

          //          var previous = none[util.Map[String, String]]
          def message: String = {

            //            if (annotations.nonEmpty) {
            //              previous = Some(Option(MDC.getCopyOfContextMap).getOrElse(java.util.Collections.emptyMap[String, String]()))
            //              MDC.setContextMap(annotations.asJava)
            //            }

            val sb = new StringBuilder()

            def append(s: String): Unit =
              sb.append(s): @scala.annotation.nowarn

            { // fiber id(s)
              fiberId match {
                case FiberId.None =>
                // noop
                case FiberId.Runtime(id, _, _) =>
                  append("f")
                  append(id.toString)
                  append(" ")
                case FiberId.Composite(l, r) =>
                  append("f")
                  List(l, r).foreach { fiberId =>
                    append("_")
                    append(fiberId.toString)
                  }
                  append(" ")
              }
            }

            { // job correlation id
              annotations.get("job") match {
                case Some(j) =>
                  append("j")
                  append(j)
                  append(" ")
                case _ =>
                // noop
              }
            }

            append("| ")
            append(textMessage())

            { // cause
              if (!cause.isEmpty) {
                val indent = "        "
                append("\n")
                append(cause.prettyPrint.indent(indent))
              }
            }

            sb.toString()

          }

          import cachedLogger.slf4jLogger
          import cachedLogger.a8Logger
          import cachedLogger.pos
          val wvletLogLevel = LoggerF.impl.fromZioLogLevel(logLevel)
          if (a8Logger.isLevelEnabled(wvletLogLevel)) {
            try logLevel match {
              case LogLevel.Trace =>
                a8Logger.trace(message)
              case LogLevel.Debug =>
                a8Logger.debug(message)
              case LogLevel.Info =>
                a8Logger.info(message)
              case LogLevel.Warning =>
                a8Logger.warn(message)
              case LogLevel.Error =>
                a8Logger.error(message)
              case LogLevel.Fatal =>
                a8Logger.error(message)
              case LogLevel.None =>
              case _ =>
            } finally {
              //            previous.foreach(MDC.setContextMap)
            }
          }

          //          try logLevel match {
          //            case LogLevel.Trace   =>
          //              if (slf4jLogger.isTraceEnabled)
          //                slf4jLogger.trace(message)
          //            case LogLevel.Debug   =>
          //              if (slf4jLogger.isDebugEnabled)
          //                slf4jLogger.debug(message)
          //            case LogLevel.Info    =>
          //              if (slf4jLogger.isInfoEnabled)
          //                slf4jLogger.info(message)
          //            case LogLevel.Warning =>
          //              if (slf4jLogger.isWarnEnabled)
          //                slf4jLogger.warn(message)
          //            case LogLevel.Error   =>
          //              if (slf4jLogger.isErrorEnabled)
          //                slf4jLogger.error(message)
          //            case LogLevel.Fatal   =>
          //              if (slf4jLogger.isErrorEnabled)
          //                slf4jLogger.error(message)
          //            case LogLevel.None    => ()
          //            case _                => ()
          //          } finally {
          ////            previous.foreach(MDC.setContextMap)
          //          }
          //        }
        }
      }
    }
}
