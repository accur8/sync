package a8.shared

object Meta {

  case class CaseClassParm[A, B](
    name: String,
    lens: A => B,
    setter: (A, B) => A,
    default: Option[() => B],
    ordinal: Int,
  )

  object Constructors {
    def apply[A](expectedFieldCount: Int, iterRawConstruct: Iterator[Any]=>A): Constructors[A] = {
      val e0 = expectedFieldCount
      val irc = iterRawConstruct
      new Constructors[A] {
        override val expectedFieldCount: Int = e0
        override def iterRawConstruct(values: Iterator[Any]): A = irc(values)
      }
    }
  }

  trait Constructors[A] {
    val expectedFieldCount: Int
    def iterRawConstruct(values: Iterator[Any]): A
    def iterRawConstruct(values: Iterator[Any], validateEndOfIterator: Boolean = true): A = {
      val v = iterRawConstruct(values)
      if ( validateEndOfIterator && values.hasNext ) {
        sys.error(s"created ${v} but values iterator has elements left ${values.toList}")
      }
      v
    }
    def rawConstruct(values: Iterable[Any], validateExpectedFieldCount: Boolean = true): A = {
      if ( validateExpectedFieldCount && (values.size != expectedFieldCount) )
        sys.error(s"values has ${values.size} values and expected ${expectedFieldCount} values")
      else
        iterRawConstruct(values.iterator)
    }
  }

  case class Generator[A, B](
    constructors: Constructors[A],
    caseClassParameters: B,
  )

}
