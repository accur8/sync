package a8.shared.ops

import java.io.{IOException, Reader, StringWriter}
import scala.util.Try

class ReaderOps(private val reader: Reader) extends AnyVal {
  def readFully(): String = {
    val writer = new StringWriter
    try {
      val buffer = new Array[Char](8192)
      var readCount = 0
      while (readCount >= 0 ) {
        readCount = reader.read(buffer)
        if (readCount >= 0)
          writer.write(buffer, 0, readCount)
      }
      writer.toString
    } finally {
      Try(writer.close())
      Try(reader.close())
    }
  }

}
