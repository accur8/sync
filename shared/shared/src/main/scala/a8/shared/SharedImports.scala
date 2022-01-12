package a8.shared


import a8.shared.CatsUtils.Memo
import a8.shared.SharedImports.Async
import a8.shared.json.JsonCodec
import a8.shared.ops.{AnyOps, AsyncOps, BooleanOps, ChunkByteOps, ChunkOps, ClassOps, FStreamOps, InputStreamOps, IntOps, IterableOps, IteratorOps, LocalDateTimeOps, OptionOps, PathOps, ReaderOps, StreamOps, ThrowableOps}
import cats.data.Chain
import fs2.Chunk
import sttp.model.Uri

import java.io.{PrintWriter, StringWriter}
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import a8.shared.json.JsonCodec.JsonCodecOps
import a8.shared.json.impl.JsonImports
import wvlet.log.Logger

import scala.collection.StringOps
import language.implicitConversions
import scala.collection.convert.{AsJavaExtensions, AsScalaExtensions}
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.reflect.ClassTag
import scala.util.Try

object SharedImports extends SharedImports

trait SharedImports
  extends CatsImportsTrait
    with AsJavaExtensions
    with AsScalaExtensions
{

  val IsNonFatal = scala.util.control.NonFatal

  lazy val Utf8Charset = java.nio.charset.Charset.forName("UTF-8")

  def some[A](a: A): Option[A] =
    Some(a)

  object ParseBigInt {
    def unapply(s : String) : Option[BigInt] = try {
      Some(BigInt(s))
    } catch {
      case _ : java.lang.NumberFormatException => None
    }
  }

  object ParseInt {
    def unapply(s : String) : Option[Int] = try {
      Some(s.toInt)
    } catch {
      case _ : java.lang.NumberFormatException => None
    }
  }

  object ParseLong {
    def unapply(s : String) : Option[Long] = try {
      Some(s.toLong)
    } catch {
      case _ : java.lang.NumberFormatException =>
        None
    }
  }

  object ParseTimeUnit {

    val timeUnitsByName = {
      val fromEnum = TimeUnit.values().map(v => v.name().toLowerCase -> v).toMap

      (
        fromEnum
          + ("ms" -> TimeUnit.MILLISECONDS)
          + ("millis" -> TimeUnit.MILLISECONDS)
          + ("millisecond" -> TimeUnit.MILLISECONDS)
          + ("s" -> TimeUnit.SECONDS)
          + ("second" -> TimeUnit.SECONDS)
          + ("m" -> TimeUnit.MINUTES)
          + ("minute" -> TimeUnit.MINUTES)
          + ("h" -> TimeUnit.HOURS)
          + ("hour" -> TimeUnit.HOURS)
      )

    }

    def unapply(s : String) : Option[TimeUnit] =
      timeUnitsByName.get(s.trim.toLowerCase)

  }


  implicit def sharedImportsStringOps(s: String) =
    new a8.shared.ops.StringOps(s)

  implicit def matchOpsAnyRef[A <: AnyRef](a: A) =
    new AnyOps[A](a)

  implicit def matchOpsAnyVal[A <: AnyVal](a: A) =
    new AnyOps[A](a)

  implicit def fStreamOps[F[_] : Async,A](strF: F[fs2.Stream[F,A]]) =
    new FStreamOps[F,A](strF)

  implicit def streamOps[F[_] : Async,A](str: fs2.Stream[F,A]) =
    new StreamOps(str)

  implicit def sharedImportsIntOps(i: Int) =
    new IntOps(i)

  implicit def sharedImportsOptionOps[A](o: Option[A]) =
    new OptionOps(o)

  implicit def sharedImportsLocalDateTimeOps(localDateTime: LocalDateTime) =
    new LocalDateTimeOps(localDateTime)

  implicit def throwableOps(th: Throwable) =
    new ThrowableOps(th)

  implicit def chunkOps[A](ch: Chunk[A]) =
    new ChunkOps(ch)

  implicit def chunkBytesOps(ch: Chunk[Byte]) =
    new ChunkByteOps(ch)

  implicit def sharedImportsAsyncOps[F[_] : Async, A](fa: F[A]) =
    new a8.shared.ops.AsyncOps[F,A](fa)

  implicit def iterableOps[A](iterable: Iterable[A]) =
    new IterableOps(iterable)

  implicit def iteratorOps[A](iterator: Iterator[A]) =
    new IteratorOps[A](iterator)

  implicit def readerOps(reader: java.io.Reader) =
    new ReaderOps(reader)

  implicit def inputStreamOps(is: java.io.InputStream) =
    new InputStreamOps(is)

  implicit def booleanOps(b: Boolean) =
    new BooleanOps(b)

  implicit def pathOps(p: Path) =
    new PathOps(p)

  implicit def classOps[A](clazz: Class[A]) =
    new ClassOps(clazz)

  implicit def toRunnable(fn: () => Unit) =
    new Runnable() {
      def run() = fn()
    }

  def unsafeParseUri(uriStr: String): Uri =
    Uri.unsafeParse(uriStr)

  def trylog[A](context: String)(fn: =>A)(implicit logger: Logger): A = {
    try {
      fn
    } catch {
      case IsNonFatal(th) =>
        logger.warn(context, th)
        throw th
    }
  }

  def trylogo[A](context: String)(fn: =>A)(implicit logger: Logger): Option[A] = {
    try {
      Some(fn)
    } catch {
      case IsNonFatal(th) =>
        logger.warn(context, th)
        None
    }
  }

  implicit def jsonCodecOps[A : JsonCodec](a: A) =
    new JsonCodecOps(a)

  object json extends JsonImports

}
