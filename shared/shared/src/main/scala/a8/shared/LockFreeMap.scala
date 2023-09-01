package a8.shared

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.immutable.Map as ImmutableMap
import scala.collection.mutable.Map as MutableMap

object LockFreeMap {

  def apply[A, B](entries: (A, B)*) = {
    val lfm = empty[A, B]
    entries.foldLeft(lfm)((acc,e) => lfm.addOne(e))
  }

  def empty[A,B] = new LockFreeMap[A,B]()
}

class LockFreeMap[A,B] extends MutableMap[A,B] {

  private val _ref = new AtomicReference(ImmutableMap[A,B]())

  override def empty: LockFreeMap[A, B] = LockFreeMap.empty[A, B]

  def get(key: A): Option[B] = {
    val k = transformKey(key)
    _ref
      .get
      .get(k)
      .orElse{
        val value = generate(k)
        value.foreach( v => += (k -> v) )
        value
      }
  }

  def snapshot = _ref.get

  def generate(key: A): Option[B] = None

  def transformKey(key: A): A = key

  def iterator: Iterator[(A, B)] = _ref.get.iterator

  override def addOne(elem: (A, B)): LockFreeMap.this.type = {
    val avoidWarning = put(elem._1, elem._2)
    this
  }

  override def subtractOne(elem: A): LockFreeMap.this.type = {
    val avoidWarning = remove(elem)
    this
  }

  /**
   * returns the original map and the new map as a tuple.  This method can get called
   * many times as the txn can fail on commit (because the original map had changed between calculation and commit).
   */
  def txn(mutator: ImmutableMap[A,B]=>ImmutableMap[A,B]): (ImmutableMap[A,B],ImmutableMap[A,B]) = {
    val t = txn_c { m0 => mutator(m0) -> () }
    t._1 -> t._2
  }

  /**
   * returns the original map and the new map as a tuple.  This method can get called
   * many times as the txn can fail on commit (because the original map had changed between calculation and commit).
   */
  @tailrec
  final def txn_c[C](mutator: ImmutableMap[A,B]=>(ImmutableMap[A,B],C)): (ImmutableMap[A,B],ImmutableMap[A,B],C) = {
    val original = _ref.get
    val updated = mutator(original)
    if ( !_ref.compareAndSet(original, updated._1) ) txn_c(mutator)
    else (original, updated._1, updated._2)
  }

  /**
   * overriding the put in MapLike since that wasn't atomic and this one is
   */
  override def put(key: A, value: B): Option[B] = {
    val pair = key -> value
    val (oldMap, newMap) = txn(_ + pair)
    oldMap.get(key)
  }

  override def remove(key: A): Option[B] = {
    val (oldMap, newMap) = txn(_ - key)
    oldMap.get(key)
  }

  override def getOrElseUpdate(key: A, op: => B): B = {
    get(key) match {
      case Some(v) => v
      case None =>
        txn_c { original =>
          val value = op
          original.get(key) match {
            case Some(v) => original -> v
            case None =>
              val pair = key -> value
              (original + pair) -> value
          }
        }._3
    }
  }

  /**
   * Performs a safe and atomic get and update. It will only update the map if nothing changes it during the call to op.
   *
   * If the map has changed during the call to op, it will try again. This means that op may be called multiple times,
   * so side-effect code should not be performed in it (including logging).
   *
   * Example:
   *
   * {{{
   * val m = LockFreeMap.empty[Int, Int]
   *
   * m.getAndUpdate(key, {
   *   case Some(v) => Some(v + 1) // Increments value by one
   *   case None => Some(1) // Key doesn't exist, so set the value to 1
   * })
   * }}}
   *
   * @param key the key to update
   * @param op  a function with the existing value for `key` as an input parameter and the value to be put in the map
   *            as the output parameter. If the input parameter is Some, a value exists for the `key`, otherwise if the
   *            input parameters is None, a value doesn't exist for the `key`. If a Some is returned, the value inside
   *            will be placed in the map. If None is returned, no value is put in the map for the `key`.
   * @return    a tuple where the first value was the existing value for the key, and the second value is the value
   *            that was put in the map or None if no value was put in the map
   */
  def getAndUpdate(key: A, op: Option[B] => Option[B]): (Option[B], Option[B]) = {
    txn_c { original =>
      val originalValue = original.get(key)
      op(originalValue) match {
        case Some(value) =>
          val pair = key -> value
          (original + pair) -> (originalValue, Some(value))
        case None => original -> (originalValue, None)
      }
    }._3
  }

}
