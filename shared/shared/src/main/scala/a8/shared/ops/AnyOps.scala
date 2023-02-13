package a8.shared.ops

import scala.concurrent.Future
import scala.util.Try


class AnyOps[A](private val a: A) extends AnyVal {

  def matchopt[B](fn: PartialFunction[A, B]): Option[B] =
    if (fn.isDefinedAt(a)) Some(fn(a))
    else None

  def matchtry[B](fn: PartialFunction[A, B]): Try[B] =
    Try {
      if (fn.isDefinedAt(a)) fn(a)
      else throw new scala.MatchError(a)
    }

  def toSome: Option[A] = Some(a)

  def toFuture: Future[A] = Future.successful(a)

  def toLeft: Left[A,Nothing] = Left(a)

  def toRight: Right[Nothing,A] = Right(a)

}
