package a8.shared

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

class LockFree[A](initialValue: A) {

  case class TxnResult[B](before: A, after: A, result: B)

  private val _ref = new AtomicReference(initialValue)


  /**
   * returns the original value and the new value as a tuple.  This method can get called
   * many times as the txn can fail on commit (because the original map had changed between calculation and commit).
   */
  @tailrec
  final def txn[B](mutator: A=>(A,B)): TxnResult[B] = {
    val original = _ref.get
    val updated = mutator(original)
    if ( !_ref.compareAndSet(original, updated._1) ) txn(mutator)
    else TxnResult(original, updated._1, updated._2)
  }

  def txn1(mutator: A=>A): TxnResult[Unit] = {
    txn { v =>
      mutator(v) -> ()
    }
  }

  def snapshot = _ref.get

  def apply() =
    snapshot

}
