package a8.sync

import a8.shared.json.{JsonCodec, ast}

import java.io.File
import scala.io.Source
import java.nio.file.Files
import a8.shared.SharedImports._

object FileUtil {

  def readFile(file: File): String = {
    val bufferedSource = Source.fromFile(file)
    val sb = new StringBuilder
    for (line <- bufferedSource.getLines()) {
      sb.append(line.trim)
    }
    bufferedSource.close()
    sb.toString()
  }

  def writeFile(file: File, contents: String): Unit = {
    file.getParentFile.mkdirs()
    Files.writeString(file.toPath, contents)
  }

  def resolveCaseClass[A](jsonStr: String)(implicit decoder: JsonCodec[A]): A =
    json.unsafeRead[A](jsonStr)

  def loadJsonFile[A](file: File)(implicit decoder: JsonCodec[A]): A =
    try {
      if ( file.exists() ) {
        val str = readFile(file)
        resolveCaseClass[A](str)
      } else {
        throw new RuntimeException(s"${file.getName} not found")
      }
    } catch {
      case e: Exception =>
        throw new RuntimeException(s"unable to load ${file}", e)
    }

  def saveJsonFile[A](file: File, a: A)(implicit encoder: JsonCodec[A]): Unit =
    try {
      val jsonStr = encoder.write(a).prettyJson
      writeFile(file, jsonStr)
    } catch {
      case e: Exception =>
        throw new RuntimeException(s"unable to save ${file}", e)
    }

}
