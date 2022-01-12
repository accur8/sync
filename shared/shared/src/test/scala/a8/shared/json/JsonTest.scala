package a8.shared.json


import a8.shared.{AtomicBuffer, CompanionGen}
import a8.shared.json.JsonTest.{Group, Person}
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import a8.shared.SharedImports._
import a8.shared.json.MxJsonTest._
import a8.shared.json.ReadError.ReadErrorException

object JsonTest {

  object Person extends MxPerson
  @CompanionGen
  case class Person(
    first: String,
    last: String,
  )

  object Group extends MxGroup
  @CompanionGen
  case class Group(
    members: Iterable[Person],
    name: String,
  )

}

class JsonTest extends AnyFunSuite {

  test("all fields used") {
    val actual =
      json.unsafeParse(
      """
{
  "members": [
    {"first": "billy", "last": "bo"},
    {"first": "billy", "last": "bing"}
  ],
  "name": "billy bob's friends"
}
      """).unsafeAs[Group]

    val expected =
      Group(
        Iterable(Person("billy", "bo"), Person("billy", "bing")),
        "billy bob's friends",
      )

    assert(actual == expected)

  }

  test("unused field") {
    try {
      val actual =
        json.unsafeParse(
          """
  {
    "boof": 1,
    "members": [
      {"first": "billy", "last": "bo"},
      {"first": "billy", "last": "bing"}
    ],
    "name": "billy bob's friends"
  }
        """).unsafeAs[Group]
      assert(true)
    } catch {
      case ree: ReadErrorException =>
        assert(true)
    }

  }

}
