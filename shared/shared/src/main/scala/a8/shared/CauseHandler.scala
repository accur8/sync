//package a8.shared

// ???
//import zio.Cause
//
//object CauseHandler {
//  implicit object throwableHandler extends CauseHandler[Throwable] {
//    override def prepareForLogs(cause: Cause[Throwable]): (Option[String], Option[Throwable]) =
//      cause.
//  }
//}
//
//
//
//trait CauseHandler[A] {
//
//  def prepare(a: A): OptionString
//
//  def prepareForLogs(cause: Cause[A]): (Option[String],Option[Throwable])
//}
