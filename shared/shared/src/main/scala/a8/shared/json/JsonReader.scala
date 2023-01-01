package a8.shared.json


import a8.shared.{FileSystem, HoconOps, ZFileSystem}
import a8.shared.json.JsonReadOptions.UnusedFieldAction
import a8.shared.SharedImports._
import a8.shared.app.LoggerF
import a8.shared.json.JsonReader.{JsonSource, ReadResult, ZJsonSource}
import a8.shared.json.ast.{JsDoc, JsVal}
import wvlet.log.{LogLevel, LogSource, Logger}
import zio.{Trace, UIO, ZIO}

import scala.collection.mutable
import scala.io.Source
import scala.util.Try
import scala.language.implicitConversions

object JsonReader {

  def apply[A: JsonCodec]: JsonReader[A] = new JsonReaderImpl[A]

  implicit def jsonReader[A : JsonCodec]: JsonReader[A] =
    JsonReader[A]

  sealed trait ReadResult[A] {
    def resolvedContext: String
    def valueOpt: Option[A] = None
    val warnings: Vector[String]
    def warningsMessage: Option[String] = {
      warnings
        .toNonEmpty
        .map { w =>
          s"warnings marshalling json from source ${resolvedContext}\n${w.mkString("\n").indent("    ")}"
        }
    }

  }

  object ReadResult {

    case class Success[A](value: A, warnings: Vector[String], resolvedContext: String) extends ReadResult[A] {
      override def valueOpt: Option[A] = Some(value)
    }

    case class Error[A](readError: ReadError, warnings: Vector[String], resolvedContext: String) extends ReadResult[A] {
      override def valueOpt: Option[A] = None
    }

  }

  private class JsonReaderImpl[A](implicit jsonCodec: JsonCodec[A]) extends JsonReader[A] {

    override def readZ(zsource: ZJsonSource, overrideContext: String = ""): UIO[ReadResult[A]] = {
      val resolvedContext = resolveContext(zsource.context, overrideContext)
      zsource
        .jsval
        .either
        .map {
          case Left(re) =>
            ReadResult.Error(re, Vector.empty, resolvedContext)
          case Right(jsv) =>
            impl(jsv.toDoc, resolvedContext)
        }
    }


    override def readZLogWarnings(zsource: ZJsonSource, overrideContext: String, level: LogLevel = LogLevel.WARN)(implicit loggerF: LoggerF, trace: Trace): UIO[ReadResult[A]] = {
      readZ(zsource)
        .flatMap { readResult =>
          val logEffect =
            readResult
              .warningsMessage
              .map { msg =>
                loggerF.log(level, msg, None)
              }
              .getOrElse(zunit)
          logEffect
            .as(readResult)
        }
    }


    override def readLogWarnings(source: JsonSource, overrideContext: String, logLevel: LogLevel)(implicit logger: Logger, pos: LoggerF.Pos): ReadResult[A] = {
      val readResult = read(source)
      readResult
        .warningsMessage
        .foreach { msg =>
          logger.log(logLevel, pos.asLogSource, msg)
        }
      readResult
    }

    override def read(source: JsonSource, overrideContext: String = ""): ReadResult[A] = {
      val resolvedContext = resolveContext(source.context, overrideContext)
      source.jsval match {
        case Left(re) =>
          ReadResult.Error(re, Vector.empty, resolvedContext)
        case Right(jsv) =>
          impl(jsv.toDoc, resolvedContext)
      }
    }


    def resolveContext(sourceContext: String, overrideContext: String): String =
      if ( overrideContext.isEmpty ) sourceContext else overrideContext

    def impl(doc: JsDoc, resolvedContext: String): ReadResult[A] = {

      val warnings = mutable.Buffer[String]()

      val unusedFieldAction =
        new UnusedFieldAction {
          override def apply[A](unusedFieldsInfo: JsonReadOptions.UnusedFieldsInfo[A])(implicit readOptions: JsonReadOptions): Either[ReadError, A] = {
            warnings.append(unusedFieldsInfo.messageFn())
            unusedFieldsInfo.successFn()
          }
        }

      implicit val readOptions =
        JsonReadOptions(
          resolvedContext.some,
          unusedFieldAction,
        )

      jsonCodec.read(doc)(readOptions) match {
        case Right(v) =>
          ReadResult.Success(v, warnings.toVector, resolvedContext)
        case Left(re) =>
          ReadResult.Error(re, warnings.toVector, resolvedContext)
      }

    }

  }

  object JsonSource {

    implicit def hoconToSource(hoconConfig: com.typesafe.config.Config): JsonSource =
      new JsonSource {
        override def context: String = "hocon config"
        override def jsval: Either[ReadError, JsVal] =
          Right(HoconOps.impl.toJsVal(hoconConfig.root))
      }

    implicit def fileToSource(file: FileSystem.File): JsonSource =
      new JsonSource {
        override def context: String = file.absolutePath
        override def jsval: Either[ReadError, JsVal] = {
          file.readAsStringOpt() match {
            case None =>
              Left(ReadError.SourceNotFoundError(context))
            case Some(jsonStr) =>
              json.parse(jsonStr)
          }
        }
      }

    implicit def stringToSource(jsonStr: String): JsonSource =
      new JsonSource {
        override def context: String = "in memory string"
        override def jsval: Either[ReadError, JsVal] =
          parse(jsonStr)
      }

    implicit def jsvalToSource(jsval0: JsVal): JsonSource =
      new JsonSource {
        override def context: String = "in memory json value"
        override def jsval: Either[ReadError, JsVal] =
          Right(jsval0)
      }

    implicit def jsdocToSource(jsdoc: JsDoc): JsonSource =
      new JsonSource {
        override def context: String = "in memory json value"
        override def jsval: Either[ReadError, JsVal] =
          Right(jsdoc.value)
      }

    implicit def zfileToZSource(file: ZFileSystem.File): ZJsonSource =
      new ZJsonSource {
        override def context: String = file.absolutePath

        override def jsval: ZIO[Any, ReadError, JsVal] =
          file
            .readAsStringOpt
            .either
            .flatMap {
              case Left(e) =>
                zfail(ReadError.UnexpectedException(e))
              case Right(None) =>
                zfail(ReadError.SourceNotFoundError(context): ReadError)
              case Right(Some(jsonStr)) =>
                ZIO.fromEither(json.parse(jsonStr))
            }
      }

  }

  trait JsonSource {
    def context: String
    def jsval: Either[ReadError, JsVal]
  }

  trait ZJsonSource {
    def context: String
    def jsval: ZIO[Any, ReadError, JsVal]
  }

}

trait JsonReader[A] {

  def readZ(zsource: ZJsonSource, overrideContext: String = ""): UIO[ReadResult[A]]
  def readZLogWarnings(zsource: ZJsonSource, overrideContext: String = "", level: LogLevel = LogLevel.WARN)(implicit loggerF: LoggerF, trace: Trace): UIO[ReadResult[A]]

  def read(source: JsonSource, overrideContext: String = ""): ReadResult[A]
  def readLogWarnings(source: JsonSource, overrideContext: String = "", level: LogLevel = LogLevel.WARN)(implicit logger: Logger, pos: LoggerF.Pos): ReadResult[A]

}
