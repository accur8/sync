package a8.shared

object Chord {

  implicit class ChordStringContexter(private val stringContext: StringContext) extends AnyVal {
    def ch(args: Chord*): Chord = {
      assert(args.length + 1 == stringContext.parts.length)
      val allButLast =
        stringContext
          .parts
          .iterator
          .zip(args.iterator)
          .foldLeft(Chord.empty) { case (ch, (str,arg)) =>
            ch ~ Chord.str(str) ~ arg
          }
      allButLast ~ Chord.str(stringContext.parts.last)
    }
  }

  object impl {
    case object Empty extends Chord
    case class Concat(l: Chord, r: Chord) extends Chord
    case class Concat3(l: Chord, m: Chord, r: Chord) extends Chord
    case class Str(v: String) extends Chord
    case class Character(c: Char) extends Chord
    case class IteratorChord(iter: ()=>Iterator[Chord]) extends Chord
    case class IteratorChordWithSeparator(iter: ()=>Iterator[Chord], separator: Chord) extends Chord
    case class IndentValue(value: Chord, indent: Indent) extends Chord
  }
  import impl._

  sealed trait Indent {
    def ~(i: Indent): Indent
  }
  object Indent {
    def apply(ch: Chord): Indent = By(ch)
    case object Empty extends Indent {
      def ~(i: Indent): Indent = i
    }
    case class By(value: Chord) extends Indent {
      def ~(i: Indent): Indent =
        i match {
          case Indent.Empty =>
            this
          case by: By =>
            By(value ~ by.value)
        }
    }
  }

  val empty: Chord = impl.Empty
  val doubleQuote: Chord = Chord.str('"'.toString)
  val singleQuote: Chord = Chord.str("'")
  val leftParen: Chord = Chord.str("(")
  val rightParen: Chord = Chord.str(")")
  val comma: Chord = Chord.str(",")
  val equal: Chord = Chord.str("=")

  implicit class ChordOps(private val ch: Chord) extends AnyVal {
    @inline def ~(r: Chord): Chord = Concat(ch, r)
    @inline def ~(r: String): Chord = Concat(ch, str(r))
    @inline def *(r: String): Chord = ch ~ " " ~ str(r)
    @inline def *(r: Chord): Chord = ch ~ " " ~ r
    @inline def ~*~(r: Chord): Chord = ch * r
    @inline def ~*~(r: String): Chord = ch * r
  }

  @inline def indent(ch: Chord)(implicit indent: Indent): Chord = IndentValue(ch, indent)
  @inline def str(s: String): Chord = Str(s)
  @inline def char(c: Char): Chord = Character(c)

  def mkString(chord: Chord): String = {

    val sb = new java.lang.StringBuilder

    def doIndent(value: Chord, indent: Indent): Unit = {

      impl(value)

      @inline
      def append(c: Char): Unit = {
        sb.append(c)
        if ( c == '\n' ) {
          indent match {
            case Indent.Empty =>
            case by: Indent.By =>
              impl(by.value)
          }
        }
      }

      def impl(ch: Chord): Unit = {
        ch match {
          case Empty =>
          case s: Str =>
            sb.ensureCapacity(sb.length() + s.v.length)
            s.v.foreach { c =>
              append(c)
            }
          case IndentValue(value, additionalIndent) =>
            doIndent(value, indent ~ additionalIndent)
          case c: Concat =>
            impl(c.l)
            impl(c.r)
          case c: Concat3 =>
            impl(c.l)
            impl(c.m)
            impl(c.r)
          case c: Character =>
            append(c.c)
          case IteratorChord(thunk) =>
            thunk().foreach(ch =>
              impl(ch)
            )
          case IteratorChordWithSeparator(thunk, sep) =>
            var first = true
            thunk().foreach { ch =>
              if (first) {
                first = false
              } else {
                impl(sep)
              }
              impl(ch)
            }
        }
      }
    }
    doIndent(chord, Indent.Empty)
    sb.toString()
  }

  implicit class IterableChord(iter: Iterable[Chord]) {
    def mkChord: Chord = IteratorChord(() => iter.iterator)
    def mkChord(separator: Chord): Chord = IteratorChordWithSeparator(() => iter.iterator, separator)
  }

  implicit class ArrayChord(array: Array[Chord]) {
    def mkChord: Chord = IteratorChord(() => array.iterator)
    def mkChord(separator: Chord): Chord = IteratorChordWithSeparator(() => array.iterator, separator)
  }

}

sealed trait Chord {
  override def toString = Chord.mkString(this)
}
