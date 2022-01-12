package a8.shared.json.impl

import a8.shared.Chord
import a8.shared.json.ast._

object JsValOps {

  object impl {
    implicit val implicitIndent = Chord.Indent(Chord.str("  "))
    val colonCh = Chord.str(":")
    val colonSpaceCh = Chord.str(": ")
    val leftBracketCh = Chord.str("[")
    val rightBracketCh = Chord.str("]")
    val leftCurlyCh = Chord.str("{")
    val rightCurlyCh = Chord.str("}")
    val trueCh = Chord.str("true")
    val falseCh = Chord.str("false")
    val nullCh = Chord.str("null")
    val nothingCh = Chord.str("")
    val newLineCh = Chord.str("\n")
    val doubleQuoteCh = Chord.str("\"")
    val commaNewLineCh = Chord.str(",\n")
  }
  import impl._

  /**
   * see here https://stackoverflow.com/questions/19176024/how-to-escape-special-characters-in-building-a-json-string
   */
  def toEscapedJsonChord(value: String): Chord = {
    val sb = new StringBuilder
    value
      .map {
        case '"' =>
          sb.append("\\\"")
        case '\\' =>
          sb.append("\\\\")
        case '\b' =>
          sb.append("\\b")
        case '\f' =>
          sb.append("\\f")
        case '\n' =>
          sb.append("\\n")
        case '\r' =>
          sb.append("\\r")
        case '\t' =>
          sb.append("\\t")
        case ch =>
          sb.append(ch)
      }
    doubleQuoteCh ~  Chord.str(sb.toString()) ~ doubleQuoteCh
  }

  def toPrettyJsonChord(JsVal: JsVal): Chord = {
    def impl(jv: JsVal): Chord =
      jv match {
        case jsd: JsDoc =>
          impl(jsd.value)
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
          (
            leftBracketCh
              ~ Chord.indent (
                newLineCh
                ~ Chord.impl.IteratorChordWithSeparator(
                    () =>
                      jarr
                        .values
                        .iterator
                        .filterNot(_.actualValue == JsNothing)
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
                      .filterNot(_._2.actualValue == JsNothing)
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
      case jsd: JsDoc =>
        toCompactJsonChord(jsd.value, sortKeys)
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
                .filterNot(_.actualValue == JsNothing)
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
                  .filterNot(_._2.actualValue == JsNothing)
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
                  .filterNot(_._2.actualValue == JsNothing)
                  .map(t => toEscapedJsonChord(t._1) ~ colonCh ~ toCompactJsonChord(t._2,sortKeys)),
              Chord.comma,
            ) ~
            rightCurlyCh
        }

    }

}
