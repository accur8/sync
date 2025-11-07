package a8.shared.json.impl

import a8.shared.Chord
import a8.shared.json.ast._

object JsValOps {

  object impl {
    implicit val implicitIndent: Chord.Indent = Chord.Indent(Chord.str("  "))
    val colonCh: Chord = Chord.str(":")
    val colonSpaceCh: Chord = Chord.str(": ")
    val leftBracketCh: Chord = Chord.str("[")
    val emptyArrayCh: Chord = Chord.str("[]")
    val rightBracketCh: Chord = Chord.str("]")
    val leftCurlyCh: Chord = Chord.str("{")
    val rightCurlyCh: Chord = Chord.str("}")
    val trueCh: Chord = Chord.str("true")
    val falseCh: Chord = Chord.str("false")
    val nullCh: Chord = Chord.str("null")
    val nothingCh: Chord = Chord.str("")
    val newLineCh: Chord = Chord.str("\n")
    val doubleQuoteCh: Chord = Chord.str("\"")
    val commaNewLineCh: Chord = Chord.str(",\n")
  }
  import impl._

  /**
   * see here https://stackoverflow.com/questions/19176024/how-to-escape-special-characters-in-building-a-json-string
   */
  def toEscapedJsonChord(value: String): Chord = {
    val sb = new StringBuilder
    def append(s: String): Unit =
      sb.append(s): @scala.annotation.nowarn
    value
      .foreach {
        case '"' =>
          append("\\\"")
        case '\\' =>
          append("\\\\")
        case '\b' =>
          append("\\b")
        case '\f' =>
          append("\\f")
        case '\n' =>
          append("\\n")
        case '\r' =>
          append("\\r")
        case '\t' =>
          append("\\t")
        case ch =>
          sb.append(ch): @scala.annotation.nowarn
      }
    doubleQuoteCh ~  Chord.str(sb.toString()) ~ doubleQuoteCh
  }

  def toPrettyJsonChord(JsVal: JsVal): Chord = {
    def impl(jv: JsVal): Chord =
      jv match {
        case jn: JsNum =>
          Chord.str(jn.value.toString())
        case js: JsStr =>
          toEscapedJsonChord(js.value)
        case JsTrue =>
          trueCh
        case JsFalse =>
          falseCh
        case JsNull =>
          nullCh
        case JsNothing =>
          nothingCh
        case jarr: JsArr if jarr.values.isEmpty =>
          emptyArrayCh
        case jarr: JsArr =>
          (
            leftBracketCh
              ~ Chord.indent (
                newLineCh
                ~ Chord.impl.IteratorChordWithSeparator(
                    () =>
                      jarr
                        .values
                        .iterator
                        .filterNot(_ == JsNothing)
                        .map(v => impl(v)),
                    commaNewLineCh,
                  )
              )
              ~ newLineCh
              ~ rightBracketCh
          )
        case jobj: JsObj =>
          (
            leftCurlyCh
              ~ Chord.indent (
                newLineCh
                ~ Chord.impl.IteratorChordWithSeparator(
                  () =>
                    jobj
                      .values
                      .iterator
                      .filterNot(_._2 == JsNothing)
                      .map(t => toEscapedJsonChord(t._1) ~ colonSpaceCh ~ impl(t._2)),
                  commaNewLineCh,
                )
              )
              ~ newLineCh
              ~ rightCurlyCh
          )

      }
    impl(JsVal)
  }

  def toCompactJsonChord(jv: JsVal, sortKeys: Boolean): Chord =
    jv match {
      case jn: JsNum =>
        Chord.str(jn.value.toString())
      case js: JsStr =>
        toEscapedJsonChord(js.value)
      case JsTrue =>
        trueCh
      case JsFalse =>
        falseCh
      case JsNull =>
        nullCh
      case JsNothing =>
        nothingCh
      case jarr: JsArr =>
        leftBracketCh ~
          Chord.impl.IteratorChordWithSeparator(
            () =>
              jarr
                .values
                .iterator
                .filterNot(_ == JsNothing)
                .map(v => toCompactJsonChord(v, sortKeys)),
            Chord.comma,
          ) ~
          rightBracketCh
      case jobj: JsObj =>
        // ??? TODO DRY this up
        if ( sortKeys ) {
          leftCurlyCh ~
            Chord.impl.IteratorChordWithSeparator(
              () =>
                jobj
                  .values
                  .toList
                  .sortBy(_._1)
                  .iterator
                  .filterNot(_._2 == JsNothing)
                  .map(t => toEscapedJsonChord(t._1) ~ colonCh ~ toCompactJsonChord(t._2, sortKeys)),
              Chord.comma,
            ) ~
            rightCurlyCh
        } else {
          leftCurlyCh ~
            Chord.impl.IteratorChordWithSeparator(
              () =>
                jobj
                  .values
                  .iterator
                  .filterNot(_._2 == JsNothing)
                  .map(t => toEscapedJsonChord(t._1) ~ colonCh ~ toCompactJsonChord(t._2,sortKeys)),
              Chord.comma,
            ) ~
            rightCurlyCh
        }

    }

}
