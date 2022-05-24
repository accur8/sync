package a8.shared


object FromString {
  implicit val string: FromString[String] =
    new FromString[String] {
      override def fromString(value: String): Option[String] =
        Some(value)
    }
}

trait FromString[A] {
  def fromString(value: String): Option[A]
}
