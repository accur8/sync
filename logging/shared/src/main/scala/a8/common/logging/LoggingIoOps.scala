package a8.common.logging


import java.nio.charset.StandardCharsets._
import java.nio.file.attribute.{FileAttribute, FileTime}
import java.nio.file.{Files, Paths}
import java.time.{LocalDateTime, OffsetDateTime}

object LoggingIoOps {

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

}
