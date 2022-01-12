package a8.shared.ops


import fs2.Chunk
import a8.shared.SharedImports._

class ChunkByteOps(private val bytes: Chunk[Byte]) {

  def toUtf8String = {
    val slice = bytes.toArraySlice
    new String(slice.values, slice.offset, slice.length, Utf8Charset)
  }

}