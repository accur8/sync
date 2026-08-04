package a8.shared

import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import a8.shared.SharedImports.canEqual.given

class AtomicBufferTest extends AnyFunSuite {

  def assertEquals[A](expected: A, actual: A): Assertion =
    assertResult(expected)(actual)

  test("addOne") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    val result = b.toList
    assertEquals(List("a"), result)
  }

  test("prepend") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.prepend("b")
    assertEquals(List("b", "a" ), b.toList)
  }

  test("insert - start") {
    val b = new AtomicBuffer[String]
    b.addOne("b")
    b.addOne("c")

    b.insert(0, "a")

    assertEquals(List("a", "b", "c"), b.toList)
  }

  test("insert - middle") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("c")

    b.insert(1, "b")

    assertEquals(List("a", "b", "c"), b.toList)
  }

  test("insert - end") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    b.insert(2, "c")

    assertEquals(List("a", "b", "c"), b.toList)
  }

  test("insert - beyond num of elements") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    b.insert(3, "c")

    assertEquals(List("a", "b", "c"), b.toList)
  }

  test("insert - negative index") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    b.insert(-1, "1")

    val result = b.toList

    assertEquals(List("1", "a", "b"), result)
  }

  test("insertAll - at start negative") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    b.insertAll(-1, Iterable("c","d"))

    val result = b.toList

    assertEquals(List("c", "d", "a", "b"), result)

  }

  test("insertAll - at start") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    b.insertAll(0, Iterable("c","d"))

    val result = b.toList

    assertEquals(List("c", "d", "a", "b"), result)

  }

  test("insertAll - at end") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    b.insertAll(2, Iterable("c","d"))

    val result = b.toList

    assertEquals(List("a", "b", "c", "d"), result)
  }

  test("insertAll - at end past existing index") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    b.insertAll(5, Iterable("c","d"))

    val result = b.toList

    assertEquals(List("a", "b", "c", "d"), result)
  }

  test("insertAll - in middle") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    b.insertAll(1, Iterable("c","d"))

    val result = b.toList

    assertEquals(List("a", "c", "d", "b"), result)
  }

  test("remove at index - inbounds") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    b.remove(0)

    val result = b.toList

    assertEquals(List("b"), result)
  }

  test("remove at index - out of bounds above max index") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.remove(2)
      b.toList
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals("2 is out of bounds (min 0, max 1)", result)
  }

  test("remove at index - out of bounds below min index") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.remove(-1)
      b.toList
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals("-1 is out of bounds (min 0, max 1)", result)
  }

  test("remove at index with count - inbounds") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    b.remove(0, 2)

    val result = b.toList

    assertEquals(List(), result)
  }

  test("remove at index with count - out of bounds below min index") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.remove(-1, 2)
      b.toList
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals(List(), result)
  }

  test("remove at index with count - out of bounds above max index") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.remove(2, 2)
      b.toList
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals(List("a", "b"), result)
  }

  test("patchInPlace - in middle") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.patchInPlace(1, Iterable("c", "d"),  2)
      b.toList
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals(List("a", "c", "d"), result)
  }

  test("patchInPlace - in middle partial") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.patchInPlace(1, Iterable("c", "d"),  1)
      b.toList
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals(List("a", "c"), result)
  }

  test("patchInPlace - below min index") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.patchInPlace(-1, Iterable("c", "d"),  2)
      b.toList
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals(List("c", "d"), result)
  }

  test("patchInPlace - above max index") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.patchInPlace(2, Iterable("c", "d"),  2)
      b.toList
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals(List("a", "b", "c", "d"), result)
  }

  test("update - existing head elem") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.update(0, "c")
      b.toList
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals(List("c", "b"), result)
  }

  test("update - existing elem") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.update(1, "c")
      b.toList
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals(List("a", "c"), result)
  }

  test("update - below min index") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.update(-1, "c")
      b.toList
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals("-1 is out of bounds (min 0, max 1)", result)
  }

  test("update - above max index") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.update(2, "c")
      b.toList
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals("2 is out of bounds (min 0, max 1)", result)
  }

  test("apply - head") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.apply(0)
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals("a", result)
  }

  test("apply - below min index") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.apply(-1)
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals("-1 is out of bounds (min 0, max 1)", result)
  }

  test("apply - above max index") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.apply(2)
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals("2 is out of bounds (min 0, max 1)", result)
  }

  test("length -- with values") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    val result = try {
      b.length
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals(2, result)
  }

  test("length -- empty") {
    val b = new AtomicBuffer[String]

    val result = try {
      b.length
    } catch {
      case e: Exception => e.getMessage
    }

    assertEquals(0, result)
  }

  test("iterator -- with values") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    assertResult(Iterator("a", "b").toList)(b.iterator.toList)
  }

  test("iterator -- empty") {
    val b = new AtomicBuffer[String]

    assertResult(Iterator().toList)(b.iterator.toList)
  }

  test("clear") {
    val b = new AtomicBuffer[String]
    b.addOne("a")
    b.addOne("b")

    b.clear()

    assertEquals(new AtomicBuffer[String], b)
  }

}
