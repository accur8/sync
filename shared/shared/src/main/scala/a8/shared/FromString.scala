package a8.shared


object FromString {

  implicit val string: FromString[String] =
    new FromString[String] {
      override def fromString(value: String): Option[String] =
        Some(value)
    }

  implicit val long: FromString[Long] =
    new FromString[Long] {
      override def fromString(value: String): Option[Long] =
        value.toLongOption
    }

  implicit val int: FromString[Int] =
    new FromString[Int] {
      override def fromString(value: String): Option[Int] =
        value.toIntOption
    }

  def apply[A: FromString]: FromString[A] =
    implicitly[FromString[A]]

}

trait FromString[A] {
  def fromString(value: String): Option[A]
}
