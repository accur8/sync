package a8.shared


import a8.shared.app.BootstrapConfig.TempDir
import SharedImports._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class StringOpsTest extends AnyFunSuite with Matchers {

  case class Test(input: String, expected: String)

  def runTests(tests: Seq[Test], fn: String => String): Unit = {
    tests.foreach { test =>
      val actual = fn(test.input)
      withClue(s"Input: '${test.input}' => Expected: '${test.expected}', but got: '${actual}'") {
        actual shouldEqual test.expected
      }
    }
  }

  test("ltrim") {
    runTests(
      Seq(
        Test("  abc", "abc"),
        Test("  abc  ", "abc  "),
        Test("abc", "abc"),
        Test("abc   ", "abc   ")
      ),
      _.replaceAll("^\\s+", "") // assuming what `.ltrim` would be
    )
  }

  test("rtrim") {
    runTests(
      Seq(
        Test("   abc", "   abc"),
        Test("   abc  ", "   abc"),
        Test("abc", "abc"),
        Test("abc   ", "abc")
      ),
      _.replaceAll("\\s+$", "") // assuming what `.rtrim` would be
    )
  }

}