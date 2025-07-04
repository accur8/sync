package a8.common.logging

object Trace {
  val empty: Trace = new Trace {}
//  implicit def apply(): Trace = empty
  given trace: Trace = empty
}

trait Trace {

}
