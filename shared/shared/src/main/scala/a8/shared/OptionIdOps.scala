package a8.shared

import cats.syntax.OptionOps


final class OptionIdOps[A](private val a: A) extends AnyVal {

  /**
   * Wrap a value in `Some`.
   *
   * `3.some` is equivalent to `Some(3)`, but the former will have an inferred
   * return type of `Option[Int]` while the latter will have `Some[Int]`.
   *
   * Example:
   * {{{
   * scala> import cats.implicits._
   * scala> 3.some
   * res0: Option[Int] = Some(3)
   * }}}
   */
  def some: Option[A] = Some(a)
}

