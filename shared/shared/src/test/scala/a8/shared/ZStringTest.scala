package a8.shared

import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import SharedImports._
import sttp.client3.UriContext
import sttp.model.Uri

class ZStringTest extends AnyFunSuite {

  case class Bob(a0: String, a1: Int)

  def assertEquals[A](expected: A, actual: A): Assertion =
    assertResult(expected)(actual)

  test("simple test") {

    assertEquals(
      "a b c 123",
      z"a ${"b"} ${"c"} ${123}".toString,
    )

  }

  test("uri test") {

    val uri: Uri = zuri"https://google.com/${1234}"

    assertEquals(
      "https://google.com/1234",
      z"${uri}",
    )

  }

  test("empty string context") {

    assertEquals(
      "",
      z"".toString,
    )

  }

  test("just a variable with empty prefix and suffix") {

    assertEquals(
      "1234",
      z"${1234}".toString,
    )

  }

  test("this code should not compile uncomment to test") {

    // this should not compile uncomment to test
//    assertEquals(
//      "1234",
//      z"${Bob("", 1)}".toString,
//    )

  }

  test("zuri") {

    // this should not compile
    assertEquals(
      uri"https://google.com/123",
      zuri"https://google.com/${123}",
    )

  }
}
