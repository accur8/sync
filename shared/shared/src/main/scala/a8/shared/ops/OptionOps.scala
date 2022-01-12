package a8.shared.ops


class OptionOps[A](private val source: Option[A]) extends AnyVal {

  def getOrError(msg: String): A =
    source.getOrElse(sys.error(msg))

}
