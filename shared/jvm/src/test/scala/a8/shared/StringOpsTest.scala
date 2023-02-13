package a8.shared


import a8.shared.app.BootstrapConfig.TempDir
import zio.{Task, ULayer, ZIO, ZLayer}
import zio.stream.ZStream
import zio.test.{TestResult, ZIOSpecDefault, assertTrue}
import SharedImports._
import a8.sync.ReplayableByteStreamSpec.runInParallel
import zio.test.Spec

object StringOpsTest extends ZIOSpecDefault {

  case class Test(input: String, expected: String)

  def spec: Spec[Any,Throwable] =
    suite("string ops")(

      test("ltrim") {
        runTests(
          Seq(
            Test("  abc", "abc"),
            Test("  abc  ", "abc  "),
            Test("abc", "abc"),
            Test("abc   ", "abc   "),
          ),
          _.ltrim
        )
      },

      test("rtrim") {
        runTests(
          Seq(
            Test("   abc", "   abc"),
            Test("   abc  ", "   abc"),
            Test("abc", "abc"),
            Test("abc   ", "abc"),
          ),
          _.rtrim
        )
      },
    )


  def runTests(tests: Seq[Test], fn: String=>String): Task[TestResult] =
    zsucceed(
      tests
        .map { test =>
          val actual = fn(test.input)
          assertTrue(actual == test.expected)
        }
        .reduce(_ && _)
    )

}
