package a8.shared

object zreplace {


  object Equal {
    def default[A]: Equal[A] = ???
    def make[A](f: (A, A) => Boolean): Equal[A] = ???

    given Equal[String] = default
    given Equal[BigDecimal] = default
    given Equal[Int] = default

  }

  trait Equal[-A] {
    def ===(a: A, b: A): Boolean = equal(a, b)
    def equal(a: A, b: A): Boolean
  }

  object Task

  trait Task[+A] {
    def map[B](f: A => B): Task[B]
    def flatMap[B](f: A => Task[B]): Task[B]
    def catchAll[B](f: Throwable => Task[B]): Task[B]
  }

  trait Scope
  trait ZIOAppArgs

  trait Resource[A] {
    def map[B](f: A => B): Resource[B]
    def flatMap[B](f: A => Task[B]): Resource[B]
    def catchAll[B](f: Throwable => Task[B]): Resource[B]
  }

  trait XStream[A]

  object Chunk {
    def fromArray[A](array: Array[A]): Chunk[A] = ???
  }

  trait Chunk[A] {
    def size: Int
    def iterator: Iterator[A]
    def apply(i: Int): A
    def drop(n: Int): Chunk[A]
    def map[B](f: A => B): Chunk[B]
    def mkString(sep: String): String
  }

}
