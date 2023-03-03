package a8.shared.json

import a8.shared.json.ast.*
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class JsonCodecsTest extends AnyFunSuite {

  test("Write Option Some") {
    val input = Some("test")
    val expected = JsStr(input.value)
    val actual = JsonCodec[Option[String]].write(input)
    assert(actual == expected)
  }

  test("Read Option Some") {
    val input = JsStr("test")
    val expected = Right(Some(input.value))
    val actual = input.as[Option[String]]
    assert(actual == expected)
  }

  test("Write Option None") {
    val input = None
    val expected = JsNothing
    val actual = JsonCodec[Option[String]].write(input)
    assert(actual == expected)
  }

  test("Read Option None") {
    val input = JsNothing
    val expected = Right(None)
    val actual = input.as[Option[String]]
    assert(actual == expected)
  }

  test("Write List") {
    val input = List("value1", "value2")
    val expected = JsArr(List(JsStr("value1"), JsStr("value2")))
    val actual = JsonCodec[List[String]].write(input)
    assert(actual == expected)
  }

  test("Read List") {
    val input = JsArr(List(JsStr("value1"), JsStr("value2")))
    val expected = Right(List("value1", "value2"))
    val actual = input.as[List[String]]
    assert(actual == expected)
  }

  test("Write Vector") {
    val input = Vector("value1", "value2")
    val expected = JsArr(List(JsStr("value1"), JsStr("value2")))
    val actual = JsonCodec[Vector[String]].write(input)
    assert(actual == expected)
  }

  test("Read Vector") {
    val input = JsArr(List(JsStr("value1"), JsStr("value2")))
    val expected = Right(Vector("value1", "value2"))
    val actual = input.as[Vector[String]]
    assert(actual == expected)
  }

  test("Write Iterable") {
    val input = Iterable("value1", "value2")
    val expected = JsArr(List(JsStr("value1"), JsStr("value2")))
    val actual = JsonCodec[Iterable[String]].write(input)
    assert(actual == expected)
  }

  test("Read Iterable") {
    val input = JsArr(List(JsStr("value1"), JsStr("value2")))
    val expected = Right(Iterable("value1", "value2"))
    val actual = input.as[Iterable[String]]
    assert(actual == expected)
  }

  test("Write Tuple") {
    val input = "key" -> "value"
    val expected = JsArr(List(JsStr("key"), JsStr("value")))
    val actual = JsonCodec[(String,String)].write(input)
    assert(actual == expected)
  }

  test("Read Tuple") {
    val input = JsArr(List(JsStr("key"), JsStr("value")))
    val expected = Right("key" -> "value")
    val actual = input.as[(String, String)]
    assert(actual == expected)
  }

  test("Write Map") {
    val input = Map("key1" -> "value1", "key2" -> "value2")
    val expected = JsObj(Map("key1" -> JsStr("value1"), "key2" -> JsStr("value2")))
    val actual = JsonCodec[Map[String,String]].write(input)
    assert(actual == expected)
  }

  test("Read Map") {
    val input = JsObj(Map("key1" -> JsStr("value1"), "key2" -> JsStr("value2")))
    val expected = Right(Map("key1" -> "value1", "key2" -> "value2"))
    val actual = input.as[Map[String,String]]
    assert(actual == expected)
  }

}
