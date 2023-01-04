package a8.shared.json


import a8.shared.{FileSystem, HoconOps, ZFileSystem}
import a8.shared.json.JsonReadOptions.UnusedFieldAction
import a8.shared.SharedImports._
import a8.shared.app.LoggerF
import a8.shared.json.JsonReader.{JsonSource, ReadResult, ZJsonSource}
import a8.shared.json.ReadError.ReadErrorException
import a8.shared.json.ast.JsDoc.JsDocRoot
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
    def valueOpt: Option[A] = None
    val warnings: Vector[String]
    def allWarningsMessage: Option[String] = {
      warnings
        .toNonEmpty
        .map { w =>
          s"warnings marshalling json from source\n${w.mkString("\n").indent("    ")}"
        }
    }

  }

  object ReadResult {

    case class Success[A](value: A, warnings: Vector[String], doc: JsDoc, resolvedContext: String) extends ReadResult[A] {
      override def valueOpt: Option[A] = Some(value)
    }

    case class Error[A](readError: ReadError, warnings: Vector[String], doc: Option[JsDoc]) extends ReadResult[A] {
      override def valueOpt: Option[A] = None
    }

  }

  private case class JsonReaderImpl[A](overrideContext: Option[String] = None)(implicit jsonCodec: JsonCodec[A]) extends JsonReader[A] {

    override def withOverrideContext(overrideContext: String): JsonReader[A] =
      copy(overrideContext = Some(overrideContext))

    override def readZ(zsource: ZJsonSource): ZIO[Any, ReadErrorException, A] = {
      val resolvedContext = resolveContext(zsource.context)
      zsource
        .jsdoc
        .either
        .flatMap {
          case Left(re) =>
            zfail(re.asException)
          case Right(jsd) =>
            impl(jsd, resolvedContext) match {
              case ReadResult.Success(a, _, _, _) =>
                zsucceed(a)
              case ReadResult.Error(re, _, _) =>
                zfail(re.asException)
            }
        }
    }

    override def readLogWarningsZ(zsource: ZJsonSource, logLevel: LogLevel = LogLevel.WARN)(implicit trace: Trace, loggerF: LoggerF): UIO[ReadResult[A]] = {
      readResultZ(zsource)
        .flatMap { readResult =>
          val logEffect =
            readResult
              .allWarningsMessage
              .map { msg =>
                val resolvedMessage = s"warnings from source ${zsource.context}\n${msg}"
                loggerF.log(logLevel, resolvedMessage, None)
              }
              .getOrElse(zunit)
          logEffect
            .as(readResult)
        }
    }

    override def readLogWarnings(source: JsonSource, logLevel: LogLevel = LogLevel.WARN)(implicit logger: Logger, pos: LoggerF.Pos): ReadResult[A] = {
      val readResult = this.readResult(source)
      readResult
        .allWarningsMessage
        .foreach { msg =>
          logger.log(logLevel, pos.asLogSource, msg)
        }
      readResult
    }

    override def readResult(source: JsonSource): ReadResult[A] = {
      val resolvedContext = resolveContext(source.context)
      source.jsdoc match {
        case Left(re) =>
          ReadResult.Error(re.withContext(resolvedContext), Vector.empty, None)
        case Right(jsd) =>
          impl(jsd, resolvedContext)
      }
    }

    def resolveContext(sourceContext: String): String =
      overrideContext.getOrElse(sourceContext)

    override def readResultZ(zsource: ZJsonSource): UIO[ReadResult[A]] = {
      def resolvedContext = resolveContext(zsource.context)
      zsource
        .jsdoc
        .either
        .map {
          case Left(re) =>
            ReadResult.Error(re.withContext(resolvedContext), Vector.empty, None)
          case Right(jsd) =>
            impl(jsd, resolvedContext)
        }
    }

    /**
     * this will throw ReadErrorException's on error
     */
    override def read(source: JsonSource): A =
      readResult(source) match {
        case ReadResult.Success(a, _, _, _) =>
          a
        case ReadResult.Error(re, _, _) =>
          throw re.asException
      }

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
          unusedFieldAction,
        )

      jsonCodec.read(doc)(readOptions) match {
        case Right(v) =>
          ReadResult.Success(v, warnings.toVector, doc, resolvedContext)
        case Left(re) =>
          ReadResult.Error(re.withContext(resolvedContext), warnings.toVector, doc.some)
      }

    }

  }

  object JsonSource {

    implicit def hoconToSource(hoconConfig: com.typesafe.config.Config): JsonSource =
      new JsonSource {
        override def context: String = "hocon"
        override def jsdoc: Either[ReadError, JsDoc] =
          Right(JsDocRoot(HoconOps.impl.toJsVal(hoconConfig.root)))
      }

    implicit def fileToSource(file: FileSystem.File): JsonSource = {
      new JsonSource {
        override def context: String = file.absolutePath
        override def jsdoc: Either[ReadError, JsDoc] = {
          file.readAsStringOpt() match {
            case None =>
              Left(ReadError.SourceNotFoundError(file.absolutePath))
            case Some(jsonStr) =>
              json.parse(jsonStr) match {
                case Right(jsv) =>
                  Right(jsv.toRootDoc)
                case Left(re) =>
                  Left(re)
              }
          }
        }
      }
    }

    implicit def stringToSource(jsonStr: String): JsonSource =
      new JsonSource {
        override def context: String = "in memory string"
        override def jsdoc: Either[ReadError, JsDoc] =
          parse(jsonStr)
            .map(_.toRootDoc)
      }

    implicit def jsvalToSource(jsval: JsVal): JsonSource =
      new JsonSource {
        override def context: String = "in memory json value"
        override def jsdoc: Either[ReadError, JsDoc] =
          Right(jsval.toRootDoc)
      }

    implicit def jsdocToSource(jsdoc0: JsDoc): JsonSource =
      new JsonSource {
        override def context: String = "in memory json document"
        override def jsdoc: Either[ReadError, JsDoc] = Right(jsdoc0)
      }

    implicit def zfileToZSource(file: ZFileSystem.File): ZJsonSource =
      new ZJsonSource {
        override def context: String = file.absolutePath

        override def jsdoc: ZIO[Any, ReadError, JsDoc] =
          file
            .readAsStringOpt
            .either
            .flatMap {
              case Left(e) =>
                zfail(ReadError.UnexpectedException(e))
              case Right(None) =>
                zfail(ReadError.SourceNotFoundError(context): ReadError)
              case Right(Some(jsonStr)) =>
                ZIO.fromEither(json.parse(jsonStr).map(_.toRootDoc))
            }
      }

  }

  trait JsonSource {
    def context: String
    def jsdoc: Either[ReadError, JsDoc]
  }

  trait ZJsonSource {
    def context: String
    def jsdoc: ZIO[Any, ReadError, JsDoc]
  }

  object JsonReaderOptions {
    case class LogAllWarningsAtEnd(logLevel: LogLevel, logger: Logger) extends JsonReaderOptions
  }

  sealed trait JsonReaderOptions

  object JsonReaderOptionsZ {
    case class LogAllWarningsAtEnd(logLevel: LogLevel)(implicit trace: Trace) extends JsonReaderOptions
  }

  sealed trait JsonReaderOptionsZ

}

trait JsonReader[A] {

  def withOverrideContext(overrideContext: String): JsonReader[A]

  def readZ(zsource: ZJsonSource): ZIO[Any,ReadErrorException,A]
  def readResultZ(zsource: ZJsonSource): UIO[ReadResult[A]]
  def readLogWarningsZ(zsource: ZJsonSource, logLevel: LogLevel = LogLevel.WARN)(implicit trace: Trace, loggerF: LoggerF): UIO[ReadResult[A]]

  /**
   * this will throw ReadErrorException's on error
   */
  def read(source: JsonSource): A
  def readResult(source: JsonSource): ReadResult[A]
  def readLogWarnings(source: JsonSource, logLevel: LogLevel = LogLevel.WARN)(implicit logger: Logger, pos: LoggerF.Pos): ReadResult[A]

}
