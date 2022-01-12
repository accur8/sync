package a8.shared.ops

import fs2.Chunk



class ChunkOps[A](value: Chunk[A]) {
  def mkString(sep: String) = value.iterator.mkString(sep)
}
