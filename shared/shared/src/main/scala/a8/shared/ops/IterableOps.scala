package a8.shared.ops


import a8.shared.SharedImports._
import cats.data.Chain

class IterableOps[T, U[T] <: Iterable[T]](val _delegate: U[T]) {

  def toNonEmpty: Option[U[T]] = if (_delegate.isEmpty) None else Some(_delegate)

  def orElse(fn: => U[T]): U[T] =
    if (_delegate.isEmpty) fn else _delegate

  /**
    * A shorthand for turning an iterable into a Map via a keyFn to generate the key part
    * of the key value pairs.  The value part is the original element.
    * takes an iterable and a key generating function and turns the
    * iterable into a map keyed on the keyFn ala iterable.map(i=>keyFn(i)->i).toMap
    */
  def toMapTransform[V](keyFn: T => V): Map[V, T] =
    _delegate.iterator.map(i => keyFn(i) -> i).toMap


  def toCIMap(fn: T => String): Map[CIString, T] =
    _delegate
      .iterator
      .map(v => CIString(fn(v)) -> v)
      .toMap

  def keyMapOpt[B](keyFn: T => Option[B]): Map[B, T] =
    _delegate
      .iterator
      .flatMap(i => keyFn(i).map(_ -> i))
      .toMap

  def keyMap[B](keyFn: T => B): Map[B, T] =
    _delegate
      .iterator
      .map(i => keyFn(i) -> i)
      .toMap

  def toChain: Chain[T] = {
    val seq =
      _delegate match {
        case s: Seq[T] =>
          s
        case _ =>
          _delegate.toSeq
      }
    Chain.fromSeq(seq)
  }

}
