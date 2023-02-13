package a8.shared.json


import a8.shared.{FileSystem, HoconOps, ZFileSystem}
import a8.shared.json.JsonReadOptions.UnusedFieldAction
import a8.shared.SharedImports._
import a8.shared.app.LoggerF
import a8.shared.app.LoggerF.Pos
import a8.shared.json.JsonReader.{JsonSource, ReadResult}
import a8.shared.json.ReadError.ReadErrorException
import a8.shared.json.ZJsonReader.ZJsonSource.OverrideContextJsonSource
import a8.shared.json.ZJsonReader.{ZJsonReaderOptions, ZJsonSource}
import a8.shared.json.ast.JsDoc.JsDocRoot
import a8.shared.json.ast.{JsDoc, JsVal}
import wvlet.log.{LogLevel, LogSource, Logger}
import zio.{Trace, UIO, ZIO}

import scala.collection.mutable
import scala.io.Source
import scala.util.Try
import scala.language.implicitConversions

object ZJsonReader {

  def apply[A: JsonCodec]: ZJsonReader[A] = new ZJsonReaderImpl[A]

  implicit def jsonReader[A : JsonCodec]: JsonReader[A] =
    JsonReader[A]

  private case class ZJsonReaderImpl[A](overrideContext: Option[String] = None)(implicit jsonCodec: JsonCodec[A]) extends ZJsonReader[A] {

    override def withOverrideContext(overrideContext: String): ZJsonReader[A] =
      copy(overrideContext = Some(overrideContext))


    override def read(zsource: ZJsonReader.ZJsonSource)(implicit jsonReaderOptions: ZJsonReaderOptions): ZIO[Any, ReadErrorException, A] = {
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

    override def readResult(zsource: ZJsonSource)(implicit jsonReaderZOptions: ZJsonReaderOptions): UIO[ReadResult[A]] = {
      def resolvedContext = resolveContext(zsource.context)
      zsource
        .jsdoc
        .either
        .map {
          case Left(re) =>
            ReadResult.Error(re.withContext(resolvedContext), Vector.empty[String], None)
          case Right(jsd) =>
            impl(jsd, resolvedContext)
        }
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

    def resolveContext(sourceContext: Option[String]): Option[String] =
      overrideContext.orElse(sourceContext)

  }

  object ZJsonSource {

    implicit def jsonSourceToZJsonSource(implicit source: JsonSource): ZJsonSource =
      new ZJsonSource {
        override def context: Option[String] = source.context

        override def jsdoc: ZIO[Any, ReadError, JsDoc] =
          ZIO
            .attemptBlocking(source.jsdoc)
            .either
            .flatMap {
              case Left(th) =>
                zfail(ReadError.UnexpectedException(th))
              case Right(Left(re)) =>
                zfail(re)
              case Right(Right(jsd)) =>
                zsucceed(jsd)
            }
      }

    implicit def stringToZJsonSource(jsonStr: String): ZJsonSource =
      jsonSourceToZJsonSource(jsonStr)

    implicit def jsvalToZJsonSource(jsval: JsVal): ZJsonSource =
      jsonSourceToZJsonSource(jsval)

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

    case class OverrideContextJsonSource(overrideContext: Option[String], jsonSource: ZJsonSource) extends ZJsonSource {
      def context: Option[String] = overrideContext.orElse(jsonSource.context)
      override def jsdoc: ZIO[Any, ReadError, JsDoc] = jsonSource.jsdoc
    }

  }

  trait ZJsonSource {
    def withContext(context: Option[String]): ZJsonSource = OverrideContextJsonSource(context, this)
    def context: Option[String]
    def jsdoc: ZIO[Any, ReadError, JsDoc]
  }

  object ZJsonReaderOptions {
    implicit def jsonReaderZOptions(implicit logLevel: LogLevel = LogLevel.WARN, trace: Trace, loggerF: LoggerF): ZJsonReaderOptions =
      LogWarnings(logLevel, trace, loggerF)
    case class LogWarnings(logLevel: LogLevel = LogLevel.WARN, trace: Trace, loggerF: LoggerF) extends ZJsonReaderOptions
    case object NoLogWarnings extends ZJsonReaderOptions
  }
  sealed trait ZJsonReaderOptions

}

trait ZJsonReader[A] {

  def withOverrideContext(overrideContext: String): ZJsonReader[A]

  def read(zsource: ZJsonSource)(implicit jsonReaderOptions: ZJsonReaderOptions): ZIO[Any,ReadErrorException,A]
  def readResult(zsource: ZJsonSource)(implicit jsonReaderZOptions: ZJsonReaderOptions): UIO[ReadResult[A]]

}
