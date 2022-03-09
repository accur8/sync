package a8.shared.json


import a8.shared.CompanionGen
import a8.shared.SharedImports._
import a8.shared.json.JsonTest.{Group, Person}
import a8.shared.json.MxUnionTest.MxFoo1
import a8.shared.json.ReadError.ReadErrorException
import a8.shared.json.UnionTest.Foo
import org.scalatest.funsuite.AnyFunSuite

import org.scalatest._
import matchers.should.Matchers._

object UnionTest {

  object Foo {


    implicit lazy val jsonCodec =
      UnionCodecBuilder[Foo]
        .typeFieldName("kind")
        .addType[Foo1]("foo1")
        .addSingleton("foo2", Foo2)
        .build

  }

  sealed trait Foo

  object Foo1 extends MxFoo1
  @CompanionGen
  case class Foo1(
    f0: Int,
    f1: String,
  ) extends Foo

  case object Foo2 extends Foo

}

class UnionTest extends AnyFunSuite {

  import UnionTest._

  test("unions to json and from json") {

    def run(expectedJson: String, expectedFoo: Foo): Unit = {
      val actualFoo = json.unsafeRead[Foo](expectedJson)
      actualFoo should be (expectedFoo)
      val expectedJsv = json.unsafeParse(expectedJson)
      val actualJsv = actualFoo.toJsVal
      actualJsv should be (expectedJsv)
    }

    run("""{"kind":"foo2"}""", Foo2)
    run("""{"kind":"foo1", "f0":1, "f1":"a"}""", Foo1(f0 = 1, f1 = "a"))

  }

  test("union to json") {

  }

}
