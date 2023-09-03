package a8.common.logging.println

import a8.common.logging.{Level, Logger, LoggerFactory}

object PrintlnLoggerFactory extends LoggerFactory {

  private val loggerMap = scala.collection.concurrent.TrieMap.empty[String,PrintlnLogger]

  override def logger(name: String): Logger = {
    loggerMap.getOrElseUpdate(name,new PrintlnLogger(name))
  }

  override def withContext[A](context: String)(fn: => A): A = {
    println(s"context start -- ${context}")
    try {
      fn
    } catch {
      case th: Throwable =>
        println(s"context error -- ${context}")
        th.printStackTrace()
        throw th
    } finally {
      println(s"context completed -- ${context}")
    }
  }

}
