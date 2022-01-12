package a8.shared.ops


import a8.shared.SharedImports._
import fs2.Chunk

import scala.reflect.ClassTag

object IteratorOps {

  def wrap[T](f: () => Option[T]): Iterator[T] = {
    Iterator
      .continually(f())
      .takeWhile(_.isDefined)
      .flatten
  }

}


class IteratorOps[A](private val iterator: Iterator[A]) {

  def toChain: Chain[A] =
    Chain.fromSeq(iterator.toSeq)

  def toChunk(implicit classTag: ClassTag[A]): Chunk[A] =
    Chunk.array(iterator.toArray)

}
