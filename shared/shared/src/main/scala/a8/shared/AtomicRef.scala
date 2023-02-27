package a8.shared

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

object AtomicRef {

  def apply[A](initialValue: A): AtomicRef[A] =
    new AtomicRef[A](
      new AtomicReference[A](initialValue)
    )

}


class AtomicRef[A] private (private val instance: AtomicReference[A]) {

  def get: A =
    instance.get()

  def set(update: A): Unit =
    instance.set(update)

  def compareAndSet(expect: A, update: A): Boolean =
    instance.compareAndSet(expect, update)

  @tailrec
  final def transformAndGet(cb: A => A): A = {
    val oldValue = get
    val newValue = cb(oldValue)

    if (!compareAndSet(oldValue, newValue))
	  // tail-recursive call
      transformAndGet(cb)
    else
      newValue
  }

  /**
   * returns the NEW value
   * @param cb
   * @return
   */
  @tailrec
  final def transformAndGetO(cb: A => Option[A]): (A, Option[A]) = {
    val oldValue = get
    cb(oldValue) match {
      case None =>
        oldValue -> None
      case Some(newValue) =>
        if (!compareAndSet(oldValue, newValue))
        // tail-recursive call
          transformAndGetO(cb)
        else {
          oldValue -> Some(newValue)
        }
    }
  }

  /**
   * returns the OLD value
   * @param cb
   * @return
   */
  @tailrec
  final def getAndTransform(cb: A => A): A = {
    val oldValue = get
    val update = cb(oldValue)

    if (!compareAndSet(oldValue, update))
    // tail-recursive call
      getAndTransform(cb)
    else
      oldValue
  }

  private final def getAndTransform0(cb: A => A): Unit = {
    getAndTransform(cb): @scala.annotation.nowarn
  }

  final def transform(cb: A => A): Unit =
    getAndTransform(cb): @scala.annotation.nowarn

//  def incrementAndGet(implicit num : Numeric[A]) =
//      transformAndGet(x => num.plus(x, num.one))

}
