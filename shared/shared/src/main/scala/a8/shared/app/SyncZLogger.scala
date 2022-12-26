package a8.shared.app


import a8.shared.{AtomicMap, AtomicRef}
import org.slf4j.{LoggerFactory, MDC}
import zio.{Cause, FiberId, FiberRef, LogLevel, LogSpan, Runtime, Trace, ZLayer, ZLogger}
import zio.logging.LogFormat
import a8.shared.SharedImports._

import java.util

object SyncZLogger {

  case class Leveler(
    logLevel: LogLevel,
    isEnabled: org.slf4j.Logger => Boolean
  )

  val levelers =
    List(
      Leveler(LogLevel.Trace, _.isTraceEnabled()),
      Leveler(LogLevel.Debug, _.isDebugEnabled()),
      Leveler(LogLevel.Info, _.isInfoEnabled()),
      Leveler(LogLevel.Warning, _.isWarnEnabled()),
      Leveler(LogLevel.Error, _.isErrorEnabled()),
      Leveler(LogLevel.Fatal, _.isErrorEnabled()),
    )

  val levelersByLogLevel =
    levelers
      .toMapTransform(_.logLevel)

  case class CachedLogger(
    zioTrace: String,
    loggerName: String,
    slf4jLogger: org.slf4j.Logger,
    wvletLogger: wvlet.log.Logger,
  ) {
    val fileName =
      zioTrace.indexOf("(") match {
        case -1 =>
          ""
        case i  =>
          " - " + zioTrace.substring(i)
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
                val cl = CachedLogger(traceStr, loggerName, LoggerFactory.getLogger(loggerName), wvlet.log.Logger(loggerName))
                cachedLoggers += (trace -> cl)
                cl
            }

          //          var previous = none[util.Map[String, String]]
          def message: String = {

            //            if (annotations.nonEmpty) {
            //              previous = Some(Option(MDC.getCopyOfContextMap).getOrElse(java.util.Collections.emptyMap[String, String]()))
            //              MDC.setContextMap(annotations.asJava)
            //            }

            val sb = new StringBuilder()

            { // fiber id(s)
              fiberId match {
                case FiberId.None =>
                // noop
                case FiberId.Runtime(id, _, _) => Set(id)
                  sb.append("f")
                  sb.append(id)
                  sb.append(" ")
                case FiberId.Composite(l, r) =>
                  sb.append("f")
                  List(l, r).foreach { fiberId =>
                    sb.append("_")
                    sb.append(fiberId)
                  }
                  sb.append(" ")
              }
            }

            { // job correlation id
              annotations.get("job") match {
                case Some(j) =>
                  sb.append("j")
                  sb.append(j)
                  sb.append(" ")
                case _ =>
                // noop
              }
            }

            sb.append("| ")
            sb.append(textMessage())

            { // cause
              if (!cause.isEmpty) {
                val indent = "        "
                sb.append("\n")
                sb.append(cause.prettyPrint.indent(indent))
              }
            }

            sb.append(cachedLogger.fileName)

            sb.toString()

          }

          import cachedLogger.slf4jLogger
          import cachedLogger.wvletLogger
          val wvletLogLevel = LoggerF.impl.fromZioLogLevel(logLevel)
          if (wvletLogger.isEnabled(wvletLogLevel)) {
            try logLevel match {
              case LogLevel.Trace =>
                wvletLogger.trace(message)
              case LogLevel.Debug =>
                wvletLogger.debug(message)
              case LogLevel.Info =>
                wvletLogger.info(message)
              case LogLevel.Warning =>
                wvletLogger.warn(message)
              case LogLevel.Error =>
                wvletLogger.error(message)
              case LogLevel.Fatal =>
                wvletLogger.error(message)
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
