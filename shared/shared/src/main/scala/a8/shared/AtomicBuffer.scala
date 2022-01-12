package a8.shared

object AtomicBuffer {
  def apply[A] = new AtomicBuffer[A]()
}


class AtomicBuffer[A] extends collection.mutable.Buffer[A] {

  private val ref = AtomicRef(Vector.empty[A])

  def snapshot(): Vector[A] = ref.get

  override def prepend(elem: A): AtomicBuffer.this.type = {
    ref.getAndTransform(elem +: _)
    this
  }

  override def insert(idx: Int, elem: A): Unit = {
    ref
      .getAndTransform { v =>
        val (l,r) = v.splitAt(idx)
        l ++ Vector(elem) ++ r
      }
  }

  override def insertAll(idx: Int, elems: IterableOnce[A]): Unit = {
    val reifiedElems = elems.iterator.to(Vector)
    ref
      .getAndTransform { v =>
        val (l,r) = v.splitAt(idx)
        l ++ reifiedElems ++ r
      }
  }

  override def remove(idx: Int): A = {
    val oldV =
      ref
        .getAndTransform { v =>
          val (l,r) = v.splitAt(idx)
          l ++ r.drop(1)
        }
    oldV(idx)
  }

  override def remove(idx: Int, count: Int): Unit =
    ref
      .getAndTransform { v =>
        val (l,r) = v.splitAt(idx)
        l ++ r.drop(count)
      }

  override def patchInPlace(from: Int, patch: IterableOnce[A], replaced: Int): AtomicBuffer.this.type = {
    val reifiedPatch = patch.iterator.take(replaced).to(Vector)
    ref
      .getAndTransform { v =>
        val (l,r) = v.splitAt(from)
        l ++ reifiedPatch ++ r.drop(replaced)
      }
    this
  }

  override def addOne(elem: A): AtomicBuffer.this.type = {
    ref.getAndTransform(_ :+ elem)
    this
  }

  override def update(idx: Int, elem: A): Unit =
    ref
      .getAndTransform { v =>
        if ( idx < 0 || idx >= v.length) {
          throw new IndexOutOfBoundsException(s"${idx} is out of bounds (min 0, max ${v.length - 1})")
        } else {
          val (l,r) = v.splitAt(idx)
          l.appended(elem) ++ r.drop(1)
        }
      }

  override def apply(i: Int): A =
    ref.get.apply(i)

  override def length: Int =
    ref.get.length

  override def iterator: Iterator[A] =
    ref.get.iterator

  override def clear(): Unit =
    ref.set(Vector.empty)

}
