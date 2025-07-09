package a8.common.logging


import java.io.{PrintWriter, StringWriter}
import java.nio.charset.StandardCharsets.*
import java.nio.file.attribute.{FileAttribute, FileTime}
import java.nio.file.{Files, Paths}
import java.time.{LocalDateTime, OffsetDateTime}
import java.util.regex.Pattern

/**
 * much of this was copied from shared and made logging bootstrap friendly (as in doesn't do any logging)
 */
object LoggingOps {

  def readFile(file: java.io.File): String = {
    new String(Files.readAllBytes(Paths.get(file.getCanonicalPath)), UTF_8)
  }

  def fileExtension(file: java.io.File): String = {
    val name = file.getName
    name.lastIndexOf(".") match {
      case i if i >= 0 && i < name.length =>
        file.getName.substring(i+1)
      case _ =>
        ""
    }
  }

  implicit class PathOps(path: java.nio.file.Path) {

    def creationTime = {
      val instant = Files.getAttribute(path, "creationTime").asInstanceOf[FileTime].toInstant
      LocalDateTime.ofInstant(instant, OffsetDateTime.now().getOffset())
    }

    def size() = Files.size(path)

    def exists = Files.exists(path)

    def isFile = Files.isRegularFile(path)
    def isDirectory = Files.isDirectory(path)

    def delete() = Files.delete(path)

    def parentOpt = Option(path.getParent)

    def appendSuffixToName(suffix: String): java.nio.file.Path = {
      val newFilename = path.getFileName.toString + suffix
      path.parentOpt match {
        case Some(p) =>
          p.resolve(newFilename)
        case None =>
          Paths.get(newFilename)
      }
    }

  }

  implicit class LoggingThrowableOps(private val _value: Throwable) extends AnyVal {

    def rootCause: Throwable = allCauses.last

    def allCauses: Vector[Throwable] = {
      _value.getCause match {
        case null => Vector(_value)
        case c if _value eq c => Vector(_value)
        case c => Vector(_value) ++ new LoggingThrowableOps(c).allCauses
      }
    }

    def stackTraceAsString: String = {
      val sw = new StringWriter()
      val pw = new PrintWriter(sw)
      _value.printStackTrace(pw)
      pw.flush()
      pw.close()
      sw.toString
    }

  }

  object StringOps {
    val ltrimPattern: Pattern = Pattern.compile("^\\s+")
    val rtrimPattern: Pattern = Pattern.compile("\\s+$")
  }

  implicit class StringOps(private val source: String) extends AnyVal {

    def isNotBlank: Boolean = source.exists(!_.isWhitespace)

    def isBlankX: Boolean = source.trim.length == 0

    def =:=(right: String) = source.equalsIgnoreCase(right)

    def ltrim: String = StringOps.ltrimPattern.matcher(source).replaceAll("")

    def rtrim: String = StringOps.rtrimPattern.matcher(source).replaceAll("")

    def indent(indent: String): String =
      source
        .linesIterator
        .map(indent + _)
        .mkString("\n")
  }

  /**
   * Take the scala'cized classname and make it more human readable.
   * So xyz.pdq.Main2$foo$bar$ becomes xyz.pdq.Main2.foo.bar
   */
  def normalizeClassname(classname: String): String =
    classname.replace("$", ".").reverse.dropWhile(_ == '.').reverse

  def normalizeClassname(clazz: Class[?]): String =
    normalizeClassname(clazz.getName)

  object canEqual {
    given[A]: CanEqual[A, A] = CanEqual.canEqualAny
  }

}
