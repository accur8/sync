package playground

import a8.shared.Chord
import a8.shared.Chord.Indent

object ChordDemo {


  def main(args: Array[String]): Unit = {

    implicit val indent: Indent = Indent(Chord.str("-->"))

    val ch = Chord.str("foo") ~ Chord.indent(Chord.str("123\n") ~ "abc\n" ~ "def")

    println(ch.toString)

  }

}
