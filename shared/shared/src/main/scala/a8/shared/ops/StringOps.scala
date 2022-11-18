package a8.shared.ops

import a8.shared.SharedImports.CIString
import sttp.model.Uri

import java.util.regex.Pattern
import a8.shared.SharedImports._
import zio.stream.{UStream, ZStream}


object StringOps {
  val ltrimPattern = Pattern.compile("^\\s+")
  val rtrimPattern = Pattern.compile("\\s+$")
}

class StringOps(private val source: String) extends AnyVal {

  def isNotBlank = source.exists(!_.isWhitespace)
  def isBlankX = source.trim.length == 0
  def toCi = CIString(source)
  def =:=(right: String) = source.equalsIgnoreCase(right)
  def ltrim = StringOps.ltrimPattern.matcher(source).replaceAll("")
  def rtrim = StringOps.rtrimPattern.matcher(source).replaceAll("")
  def toUri = Uri.unsafeParse(source)

  def toChunk: zio.Chunk[Byte] =
    zio.Chunk.fromArray(
      Utf8Charset.encode(source).array()
    )

  def toChunkyStream[F[_]]: UStream[Byte] = {
    ZStream
      .fromChunk(toChunk)
  }

  def indent(indent: String) =
    source
      .linesIterator.map(indent + _)
      .mkString("\n")

  def stripQuotes: String = {
    if ( source.length >= 2 && source.charAt(0) == '"' && source.charAt(source.length-1) == '"' ) {
      source.substring(1, source.length-1)
    } else {
      source
    }
  }

  def splitList(regex: String, limit: Int = Integer.MAX_VALUE, trim: Boolean = true, dropEmpty: Boolean = true): List[String] = {

    val pattern = Pattern.compile(regex)

    def trimmer(s: String): String = {
      if ( trim ) s.trim
      else s
    }

    def trimDrop(s: String): Option[String] = {
      val t0 = trimmer(s)
      if ( !dropEmpty || t0.length > 0 ) Some(t0)
      else None
    }

    def splitter(input: String, limit: Int): List[String] = {
      if ( limit == 0 ) Nil
      else {
        val splitLimit = math.min(limit,2)
        pattern.split(input, splitLimit) match {
          case Array() => Nil
          case Array(p0) => trimDrop(p0).toList
          case Array(p0, p1) => {
            trimDrop(p0) match {
              case None => splitter(p1, limit)
              case Some(i)  => i :: splitter(p1, limit - 1)
            }
          }
        }
      }
    }

    splitter(source, limit)

  }


  def padLeftTo(len: Int, elem: Char): String = {
    val sourceLen = source.length
    if (sourceLen >= len) {
      source
    } else {
      (elem.toString * (len - sourceLen)) + source
    }
  }

}
