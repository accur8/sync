package a8.shared.json


import a8.shared.{FileSystem, HoconOps, ZFileSystem}
import a8.shared.json.JsonReadOptions.UnusedFieldAction
import a8.shared.SharedImports._
import a8.shared.app.{LoggerF, Logging}
import a8.shared.app.LoggerF.Pos
import a8.shared.json.JsonReader.JsonSource.OverrideContextJsonSource
import a8.shared.json.JsonReader.{JsonReaderOptions, JsonSource, ReadResult}
import a8.shared.json.ReadError.ReadErrorException
import a8.shared.json.ast.JsDoc.JsDocRoot
import a8.shared.json.ast.{JsDoc, JsVal}
import wvlet.log.{LogLevel, LogSource, Logger}
import zio.{Trace, UIO, ZIO}

import scala.collection.mutable
import scala.io.Source
import scala.util.Try
import scala.language.implicitConversions

object JsonReader extends Logging { outer =>

  def apply[A: JsonCodec]: JsonReader[A] = new JsonReaderImpl[A]

  implicit def jsonReader[A : JsonCodec]: JsonReader[A] =
    JsonReader[A]


  object ReadResult {
    def allWarningsMessage(context: Option[String], warnings: Vector[String]): Option[String] = {
      warnings
        .toNonEmpty
        .map { warnings0 =>
          s"warnings marshalling json from source ${context.getOrElse("")}\n${warnings0.mkString("\n").indent("    ")}"
        }
    }

    case class Success[A](value: A, warnings: Vector[String], doc: JsDoc, resolvedContext: Option[String]) extends ReadResult[A] {
      override def valueOpt: Option[A] = Some(value)
    }

    case class Error[A](readError: ReadError, warnings: Vector[String], doc: Option[JsDoc]) extends ReadResult[A] {
      override def valueOpt: Option[A] = None
    }

  }

  sealed trait ReadResult[A] {
    def valueOpt: Option[A] = None
    val warnings: Vector[String]
    def allWarningsMessage(context: Option[String]): Option[String] =
      ReadResult.allWarningsMessage(context, warnings)
  }

  private case class JsonReaderImpl[A](overrideContext: Option[String] = None)(implicit jsonCodec: JsonCodec[A]) extends JsonReader[A] {

    override def withOverrideContext(overrideContext: String): JsonReader[A] =
      copy(overrideContext = Some(overrideContext))

    override def readResult(source: JsonSource)(implicit jsonReaderOptions: JsonReaderOptions): ReadResult[A] = {
      val resolvedContext = resolveContext(source.context)
      source.jsdoc match {
        case Left(re) =>
          ReadResult.Error(re.withContext(resolvedContext), Vector.empty, None)
        case Right(jsd) =>
          impl(jsd, resolvedContext)
      }
    }

    def resolveContext(sourceContext: Option[String]): Option[String] =
      overrideContext.orElse(sourceContext)

    /**
     * this will throw ReadErrorException's on error
     */
    override def read(source: JsonSource)(implicit jsonReaderOptions: JsonReaderOptions): A =
      readResult(source) match {
        case ReadResult.Success(a, _, _, _) =>
          a
        case ReadResult.Error(re, _, _) =>
          throw re.asException
      }

    def impl(doc: JsDoc, resolvedContext: Option[String])(implicit jsonReaderOptions: JsonReaderOptions): ReadResult[A] = {

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

      val readResult = jsonCodec.read(doc)(readOptions)

      if ( warnings.nonEmpty ) {
        jsonReaderOptions match {
          case JsonReaderOptions.NoLogWarnings =>
          // noop
          case lw: JsonReaderOptions.LogWarnings =>
            ReadResult
              .allWarningsMessage(resolvedContext, warnings.toVector)
              .foreach(lw.logMessage)
        }
      }

      readResult match {
        case Right(v) =>
          ReadResult.Success(v, warnings.toVector, doc, resolvedContext)
        case Left(re) =>
          ReadResult.Error(re.withContext(resolvedContext), warnings.toVector, doc.some)
      }

    }

  }

  object JsonSource {

    def apply(context: String, jsdoc: JsDoc): JsonSource = {
      val c0 = context.some
      val j0 = Right(jsdoc)
      new JsonSource {
        override def context: Option[String] = c0
        override def jsdoc: Either[ReadError, JsDoc] = j0
      }
    }

    implicit def hoconToSource(hoconConfig: com.typesafe.config.Config): JsonSource =
      new JsonSource {
        override def context = None
        override def jsdoc: Either[ReadError, JsDoc] =
          Right(JsDocRoot(HoconOps.impl.toJsVal(hoconConfig.root)))
      }

    implicit def fileToSource(file: FileSystem.File): JsonSource = {
      new JsonSource {
        override def context = file.absolutePath.some
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
        override def context = None
        override def jsdoc: Either[ReadError, JsDoc] =
          parse(jsonStr)
            .map(_.toRootDoc)
      }

    implicit def jsvalToSource(jsval: JsVal): JsonSource =
      new JsonSource {
        override def context = None
        override def jsdoc: Either[ReadError, JsDoc] =
          Right(jsval.toRootDoc)
      }

    implicit def jsdocToSource(jsdoc0: JsDoc): JsonSource =
      new JsonSource {
        override def context = None
        override def jsdoc: Either[ReadError, JsDoc] = Right(jsdoc0)
      }

    case class OverrideContextJsonSource(overrideContext: Option[String], jsonSource: JsonSource) extends JsonSource {
      def context: Option[String] = overrideContext.orElse(jsonSource.context)
      override def jsdoc: Either[ReadError, JsDoc] = jsonSource.jsdoc
    }

  }

  trait JsonSource {
    def withContext(context: Option[String]): JsonSource = OverrideContextJsonSource(context, this)
    def context: Option[String]
    def jsdoc: Either[ReadError, JsDoc]
  }

  object JsonReaderOptions {

    implicit def jsonReaderOptions(implicit logLevel: LogLevel = LogLevel.WARN, pos: Pos, logger: Logger = outer.logger): JsonReaderOptions =
      LogWarnings(logLevel, pos, logger)

    case class LogWarnings(logLevel: LogLevel = LogLevel.WARN, pos: Pos, logger: Logger) extends JsonReaderOptions {
      def logMessage(msg: String): Unit =
        logger.log(logLevel, pos.asLogSource, msg)
    }

    case object NoLogWarnings extends JsonReaderOptions

  }
  sealed trait JsonReaderOptions

}

trait JsonReader[A] {

  def withOverrideContext(overrideContext: String): JsonReader[A]

  /**
   * this will throw ReadErrorException's on error
   */
  def read(source: JsonSource)(implicit jsonReaderOptions: JsonReaderOptions): A
  def readResult(source: JsonSource)(implicit jsonReaderOptions: JsonReaderOptions): ReadResult[A]

}
