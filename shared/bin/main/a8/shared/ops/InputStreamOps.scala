package a8.shared.ops

import java.io.{InputStream, InputStreamReader}

class InputStreamOps(private val inputStream: InputStream) extends AnyVal {

  def readString: String =
    new ReaderOps(new InputStreamReader(inputStream)).readFully()
}
