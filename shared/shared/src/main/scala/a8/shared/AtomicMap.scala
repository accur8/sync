package a8.shared


object AtomicMap {
  def apply[A,B] = new AtomicMap[A,B]
}


class AtomicMap[A,B] extends collection.mutable.Map[A,B] {

  private val ref = AtomicRef(Map.empty[A,B])

  def snapshot(): Map[A,B] = ref.get

  override def iterator: Iterator[(A, B)] =
    ref.get.iterator

  override def addOne(elem: (A, B)): AtomicMap.this.type = {
    ref.getAndTransform(_ + elem): @scala.annotation.nowarn
    this
  }

  override def get(key: A): Option[B] =
    ref.get.get(key)

  override def subtractOne(key: A): AtomicMap.this.type = {
    ref.getAndTransform(_ - key): @scala.annotation.nowarn
    this
  }

}
