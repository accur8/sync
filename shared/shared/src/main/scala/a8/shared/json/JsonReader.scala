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
    def allWarningsMessage(context: Option[String]): Option[String] = {
      warnings
        .toNonEmpty
        .map { warnings0 =>
          s"warnings marshalling json from source ${context.getOrElse("")}\n${warnings0.mkString("\n").indent("    ")}"
        }
    }

  }

  object ReadResult {

    case class Success[A](value: A, warnings: Vector[String], doc: JsDoc, resolvedContext: Option[String]) extends ReadResult[A] {
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
              .allWarningsMessage(zsource.context)
              .map { msg =>
                loggerF.log(logLevel, msg, None)
              }
              .getOrElse(zunit)
          logEffect
            .as(readResult)
        }
    }

    override def readLogWarnings(source: JsonSource, logLevel: LogLevel = LogLevel.WARN)(implicit logger: Logger, pos: LoggerF.Pos): ReadResult[A] = {
      val readResult = this.readResult(source)
      readResult
        .allWarningsMessage(source.context)
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

    def resolveContext(sourceContext: Option[String]): Option[String] =
      overrideContext.orElse(sourceContext)

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

    def impl(doc: JsDoc, resolvedContext: Option[String]): ReadResult[A] = {

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

    implicit def zfileToZSource(file: ZFileSystem.File): ZJsonSource =
      new ZJsonSource {
        override def context = file.absolutePath.some

        override def jsdoc: ZIO[Any, ReadError, JsDoc] =
          file
            .readAsStringOpt
            .either
            .flatMap {
              case Left(e) =>
                zfail(ReadError.UnexpectedException(e))
              case Right(None) =>
                zfail(ReadError.SourceNotFoundError(file.absolutePath))
              case Right(Some(jsonStr)) =>
                ZIO.fromEither(json.parse(jsonStr).map(_.toRootDoc))
            }
      }

  }

  trait JsonSource {
    def context: Option[String]
    def jsdoc: Either[ReadError, JsDoc]
  }

  trait ZJsonSource {
    def context: Option[String]
    def jsdoc: ZIO[Any, ReadError, JsDoc]
  }

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
