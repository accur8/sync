package a8.shared

import sttp.model.Uri
import sttp.model.Uri.{PathSegment, Segment}

import language.implicitConversions

object ZString {

  object impl {

    case object Empty extends ZString
    case class Concat(l: ZString, r: ZString) extends ZString
    case class Concat3(l: ZString, m: ZString, r: ZString) extends ZString
    case class Str(v: String) extends ZString
    case class Character(c: Char) extends ZString
    case class IteratorZStr(iter: ()=>Iterator[ZString]) extends ZString
    case class IteratorZstrWithSeparator(iter: ()=>Iterator[ZString], separator: ZString) extends ZString
    case class LazyZstr(thunk: ()=>ZString) extends ZString


    def append(zstr: ZString, sb: StringBuilder): Unit = {
      zstr match {
        case Str(s) =>
          sb.append(s): @scala.annotation.nowarn
        case Empty =>
        // noop
        case Concat(l, r) =>
          append(l, sb)
          append(r, sb)
        case Concat3(l, m, r) =>
          append(l, sb)
          append(m, sb)
          append(r, sb)
        case Character(ch) =>
          sb.append(ch): @scala.annotation.nowarn
        case IteratorZStr(iter) =>
          iter()
            .foreach(zstr => append(zstr, sb))
        case IteratorZstrWithSeparator(iter, sep) =>
          iter()
            .foreach { v =>
              append(v, sb)
              append(sep, sb)
            }
        case LazyZstr(thunk) =>
          append(thunk(), sb)
      }
    }

  }
  import impl._

  implicit def zstringFromZStringer[A: ZStringer](a: A): ZString =
    implicitly[ZStringer[A]].toZString(a)

  object ZStringer {

    def apply[A: ZStringer]: ZStringer[A] =
      implicitly[ZStringer[A]]

    implicit val uriZStringer: ZStringer[Uri] = toStringZStringer[Uri]

    implicit val stringZStringer: ZStringer[String] =
      new ZStringer[String] {
        override def toZString(a: String): ZString =
          impl.Str(a)
      }

    implicit val doubleZStringer: ZStringer[Double] = toStringZStringer[Double]
    implicit val floatZStringer: ZStringer[Float] = toStringZStringer[Float]
    implicit val longZStringer: ZStringer[Long] = toStringZStringer[Long]
    implicit val intZStringer: ZStringer[Int] = toStringZStringer[Int]
    implicit val shortZStringer: ZStringer[Short] = toStringZStringer[Short]
    implicit val byteZStringer: ZStringer[Byte] = toStringZStringer[Byte]
    implicit val booleanZStringer: ZStringer[Boolean] = toStringZStringer[Boolean]
    implicit val charZStringer: ZStringer[Char] = toStringZStringer[Char]

    implicit class ZStringIterableOps[A: ZStringer](iterable: Iterable[A]) {
      lazy val zstringer: ZStringer[A] = implicitly[ZStringer[A]]
      def mkZString: ZString =
        IteratorZStr(() => iterable.iterator.map(zstringer.toZString))
      def mkZString(separator: String): ZString =
        IteratorZstrWithSeparator(() => iterable.iterator.map(zstringer.toZString), Str(separator))
      def mkZString(separator: ZString): ZString =
        IteratorZstrWithSeparator(() => iterable.iterator.map(zstringer.toZString), separator)
    }

    implicit val zstringerHasZString: ZStringer[HasZString] =
      new ZStringer[HasZString] {
        override def toZString(hzs: HasZString): ZString =
          hzs.toZString
      }

  }
  trait ZStringer[A] {
    def toZString(a: A): ZString
  }

  def toStringZStringer[A]: ZStringer[A] =
    new ZStringer[A] {
      override def toZString(a: A): ZString =
        a.toString
    }

  implicit def hasZString(hzs: HasZString): ZString =
    hzs.toZString

  trait HasZString {
    def toZString: ZString
  }

  implicit class ZStringOps(private val zstring: ZString) extends AnyVal {
    import impl._
    @inline def ~(r: ZString): ZString = Concat(zstring, r)
    @inline def *(r: ZString): ZString = zstring ~ " " ~ r
  }

  sealed trait ZStringInternal {
    def append(sb: StringBuilder): Unit
  }

  def str(s: String): ZString =
    Str(s)

  implicit def zstringToPathSegment(zstr: ZString): Segment =
    PathSegment(zstr.toString)

  given [A <: ZString, B <: ZString]: CanEqual[A,B] = CanEqual.derived

}

sealed trait ZString {
  override def toString: String = {
    val sb = new StringBuilder
    ZString.impl.append(this, sb)
    sb.toString()
  }
}

