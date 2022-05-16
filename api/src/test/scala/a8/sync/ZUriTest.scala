package a8.sync


import a8.shared.SharedImports._
import org.scalatest.funsuite.AnyFunSuite

class ZUriTest extends AnyFunSuite {

  test("Simple test") {
    val uri = zuri"foo"
    assert(uri.toString == "foo")
  }

  test("Multiple Argument test") {
    val uri = zuri"foo-${1}-${2}"
    assert(uri.toString == "foo-1-2")
  }

}
