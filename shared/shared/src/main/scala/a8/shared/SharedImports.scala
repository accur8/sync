package a8.shared


import a8.shared.app.Logger
import a8.shared.json.JsonCodec
import a8.shared.ops.{AnyOps, BooleanOps, ClassOps, InputStreamOps, IntOps, IterableOps, IteratorOps, LocalDateTimeOps, OptionOps, PathOps, ReaderOps, ThrowableOps}
import cats.data.Chain
import sttp.model.{Uri, UriInterpolator}

import java.io.{PrintWriter, StringWriter}
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import a8.shared.json.JsonCodec.JsonCodecOps
import a8.shared.json.impl.JsonImports

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
  extends AsJavaExtensions
    with AsScalaExtensions
{

  type Resource[A] = zio.ZIO[zio.Scope,Throwable,A]

  type UStream[A] = zio.stream.UStream[A]

  type CIString = org.typelevel.ci.CIString
  val CIString = org.typelevel.ci.CIString

  val IsNonFatal = scala.util.control.NonFatal

  lazy val Utf8Charset: Charset = java.nio.charset.Charset.forName("UTF-8")

  def some[A](a: A): Option[A] =
    Some(a)

  def none[A]: Option[A] =
    None

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

  implicit def sharedImportsIntOps(i: Int) =
    new IntOps(i)

  implicit def sharedImportsOptionOps[A](o: Option[A]) =
    new OptionOps(o)

  implicit def sharedImportsLocalDateTimeOps(localDateTime: LocalDateTime) =
    new LocalDateTimeOps(localDateTime)

  implicit def throwableOps(th: Throwable) =
    new ThrowableOps(th)

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

  implicit class StringContextOps(stringContext: StringContext) {

    def zuri(parms: ZString*): Uri =
      UriInterpolator
        .interpolate(stringContext, parms)

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
