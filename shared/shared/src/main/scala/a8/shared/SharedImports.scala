package a8.shared


import a8.shared.CatsUtils.Memo
import a8.shared.SharedImports.Async
import a8.shared.json.JsonCodec
import a8.shared.ops.{AnyOps, AsyncOps, BooleanOps, ChunkByteOps, ChunkOps, ClassOps, FStreamOps, InputStreamOps, IntOps, IterableOps, IteratorOps, LocalDateTimeOps, OptionOps, PathOps, ReaderOps, StreamOps, ThrowableOps}
import cats.data.Chain
import fs2.Chunk
import sttp.model.{Uri, UriInterpolator}

import java.io.{PrintWriter, StringWriter}
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import a8.shared.json.JsonCodec.JsonCodecOps
import a8.shared.json.impl.JsonImports
import wvlet.log.Logger

import java.nio.charset.Charset
import scala.collection.{StringOps, mutable}
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

  lazy val Utf8Charset: Charset = java.nio.charset.Charset.forName("UTF-8")

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


  implicit def sharedImportsStringOps(s: String): ops.StringOps =
    new a8.shared.ops.StringOps(s)

  implicit def matchOpsAnyRef[A <: AnyRef](a: A): AnyOps[A] =
    new AnyOps[A](a)

  implicit def matchOpsAnyVal[A <: AnyVal](a: A): AnyOps[A] =
    new AnyOps[A](a)

  implicit def fStreamOps[F[_] : Async,A](strF: F[fs2.Stream[F,A]]): FStreamOps[F, A] =
    new FStreamOps[F,A](strF)

  implicit def streamOps[F[_] : Async,A](str: fs2.Stream[F,A]): StreamOps[F, A] =
    new StreamOps(str)

  implicit def sharedImportsIntOps(i: Int): IntOps =
    new IntOps(i)

  implicit def sharedImportsOptionOps[A](o: Option[A]): OptionOps[A] =
    new OptionOps(o)

  implicit def sharedImportsLocalDateTimeOps(localDateTime: LocalDateTime): LocalDateTimeOps =
    new LocalDateTimeOps(localDateTime)

  implicit def throwableOps(th: Throwable): ThrowableOps =
    new ThrowableOps(th)

  implicit def chunkOps[A](ch: Chunk[A]): ChunkOps[A] =
    new ChunkOps(ch)

  implicit def chunkBytesOps(ch: Chunk[Byte]): ChunkByteOps =
    new ChunkByteOps(ch)

  implicit def sharedImportsAsyncOps[F[_] : Async, A](fa: F[A]): AsyncOps[F, A] =
    new a8.shared.ops.AsyncOps[F,A](fa)

  implicit def iterableOps[A](iterable: Iterable[A]): IterableOps[A, Iterable] =
    new IterableOps(iterable)

  implicit def iteratorOps[A](iterator: Iterator[A]): IteratorOps[A] =
    new IteratorOps[A](iterator)

  implicit def readerOps(reader: java.io.Reader): ReaderOps =
    new ReaderOps(reader)

  implicit def inputStreamOps(is: java.io.InputStream): InputStreamOps =
    new InputStreamOps(is)

  implicit def booleanOps(b: Boolean): BooleanOps =
    new BooleanOps(b)

  implicit def pathOps(p: Path): PathOps =
    new PathOps(p)

  implicit def classOps[A](clazz: Class[A]): ClassOps[A] =
    new ClassOps(clazz)

  implicit def toRunnable(fn: () => Unit): Runnable =
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

  implicit def jsonCodecOps[A : JsonCodec](a: A): JsonCodecOps[A] =
    new JsonCodecOps(a)

  object json extends JsonImports

  implicit class StringContextOps(stringContext: StringContext) {

    def zuri(parms: ZString*): Uri =
      UriInterpolator
        .interpolate(stringContext, parms:_*)

    def zz(parms: ZString*): ZString = {

      val partsIter = stringContext.parts.iterator

      // we always have one part in every string context
      val head = partsIter.next()

      parms
        .iterator
        .zip(partsIter.map(ZString.str))
        .foldLeft(ZString.str(head)) { case (zstr, (l,r)) =>
          ZString.impl.Concat3(zstr, l, r)
        }

    }

    def z(parms: ZString*): String = {
      val zstr = zz(parms:_*)
      val sb = new StringBuilder
      ZString.impl.append(zstr, sb)
      sb.toString
    }

  }

  implicit def uriOps(uri: Uri): UriOps =
    new UriOps(uri)

}
