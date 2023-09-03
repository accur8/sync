package a8.common.logging


object Pos {

  def fromJava(fileName: String, line: Int): Pos =
    Pos(
      sourcecode.FileName(fileName),
      sourcecode.Line(line),
    )

  implicit def implicitPos(
    implicit
      file: sourcecode.File,
      fileName: sourcecode.FileName,
      line: sourcecode.Line,
  ): Pos =
    Pos(fileName, line)

}

case class Pos(
  fileName: sourcecode.FileName,
  line: sourcecode.Line,
)