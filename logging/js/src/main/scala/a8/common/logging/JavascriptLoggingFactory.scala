package a8.common.logging


import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("LoggingOps")
object JavascriptLoggingFactory extends LoggerFactory {

  var globalLevel: Level = Level.Debug

  @JSExport
  def enableTraceLogging(): Unit =
    globalLevel = Level.Trace

  val contextStack = js.Array[String]()
  val cache = mutable.Map[String, Logger]()

  override def logger(name: String): Logger =
    cache
      .getOrElseUpdate(
        name,
        JavascriptLogger(name, contextStack),
      )

  override def withContext[A](context: String)(fn: => A): A = {
    try {
      contextStack.push(context): @scala.annotation.nowarn
      fn
    } finally {
      contextStack.pop(): @scala.annotation.nowarn
    }
  }

}
