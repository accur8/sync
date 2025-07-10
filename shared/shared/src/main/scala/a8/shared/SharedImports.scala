package a8.shared

import a8.shared.json.JsonCodec
import a8.shared.ops.{AnyOps, BooleanOps, ClassOps, FiniteDurationOps, InputStreamOps, IntOps, IterableOps, IteratorOps, LocalDateTimeOps, OptionOps, PathOps, ReaderOps, ThrowableOps}
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
import cats.syntax
import cats.instances
import a8.common.logging.Logger

import scala.quoted.{Expr, Quotes, Type}

object SharedImports extends SharedImports

trait SharedImports
  extends AsJavaExtensions
  with AsScalaExtensions
  with DefaultCanEquals
{

  type Logger = a8.common.logging.Logger
  type Logging = a8.common.logging.Logging

  type CIString = org.typelevel.ci.CIString
  val CIString = org.typelevel.ci.CIString

  val IsNonFatal = scala.util.control.NonFatal

  lazy val Utf8Charset: Charset = java.nio.charset.Charset.forName("UTF-8")

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

    val timeUnitsByName: Map[String,TimeUnit] = {
      val fromEnum: Map[String, TimeUnit] = TimeUnit.values().map(v => v.name().toLowerCase -> v).toMap

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

  implicit def sharedImportsIntOps(i: Int): IntOps =
    new IntOps(i)

  implicit def sharedImportsOptionOps[A](o: Option[A]): OptionOps[A] =
    new OptionOps(o)

  implicit def sharedImportsLocalDateTimeOps(localDateTime: LocalDateTime): LocalDateTimeOps =
    new LocalDateTimeOps(localDateTime)

  implicit def throwableOps(th: Throwable): ThrowableOps =
    new ThrowableOps(th)

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

  def trylog[A](context: =>String)(fn: =>A)(implicit logger: Logger): A = {
    try {
      fn
    } catch {
      case IsNonFatal(th) =>
        logger.warn(context, th)
        throw th
    }
  }

  def tryLogDebug[A](context: =>String)(fn: =>A)(using logger: Logger): A = {
    try {
      fn
    } catch {
      case IsNonFatal(th) =>
        logger.debug(context, th)
        throw th
    }
  }

  def trylogo[A](context: =>String)(fn: =>A)(implicit logger: Logger): Option[A] = {
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
        .interpolate(stringContext, parms*)

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
      val zstr = zz(parms*)
      val sb = new StringBuilder
      ZString.impl.append(zstr, sb)
      sb.toString
    }

  }

  implicit def uriOps(uri: Uri): UriOps =
    new UriOps(uri)


  def none[A]: Option[A] = None
  def some[A](a: A): Option[A] = Some(a)


  implicit final def optionIdOps[A](a: A): OptionIdOps[A] =
    new OptionIdOps(a)

  implicit final def finiteDurationOps(fd: FiniteDuration): FiniteDurationOps =
    new FiniteDurationOps(fd)

  object canEqual {
    given[A]: CanEqual[A, A] = CanEqual.canEqualAny
  }

  val zio = zreplace

  type Trace = a8.common.logging.Trace
  
}
