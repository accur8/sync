package a8.shared.ops


import cats.data.Chain


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

//  zio
//  def toChunk(implicit classTag: ClassTag[A]): Chunk[A] =
//    Chunk.fromArray(iterator.toArray)

}
