package a8.shared.ops

import java.nio.charset.StandardCharsets._
import java.nio.file.attribute.FileAttribute
import java.nio.file.{Files, Path, Paths}

object PathOps {

  def userHome = Paths.get(System.getProperty("user.home"))

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

}


class PathOps(private val path: java.nio.file.Path) extends AnyVal {

  def absNormalize(): Path =
    path
      .toAbsolutePath
      .normalize()

  def subdir(name: String): java.nio.file.Path =
    path.resolve(Paths.get(name))

  def exists(): Boolean = Files.exists(path)

  def isFile(): Boolean = Files.isRegularFile(path)
  def isDirectory(): Boolean = Files.isDirectory(path)

  def parentOpt(): Option[Path] = Option(path.getParent)

}
