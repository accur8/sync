package a8.sync

import org.scalatest.funsuite.AnyFunSuite
import a8.shared.SharedImports._
import a8.shared.json.DynamicJson

class DynamicJsonTest extends AnyFunSuite {

  test("Simple test") {

    val jv = json.unsafeParse("""{"bob":{"bill":[{"ted": "hello"}]}}""")

    val dj = DynamicJson(jv)

    val hello = dj.bob.bill.apply(0).ted

    assert(hello.__.path == "bob.bill[0].ted")
  }

}
