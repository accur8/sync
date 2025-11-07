package a8.shared.ops

class BooleanOps(private val value : Boolean) extends AnyVal {

  def toOptionUnit: Option[Unit] =
    if ( value )
      Some(())
    else
      None

  def toOption[A](fn: => A): Option[A] =
    if ( value )
      Some(fn)
    else
      None

  def toOptionFlat[A](fn: => Option[A]): Option[A] =
    toOption(fn)
      .flatten


}
