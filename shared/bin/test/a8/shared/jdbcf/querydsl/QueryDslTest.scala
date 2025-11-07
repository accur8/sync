package a8.shared.jdbcf.querydsl


import a8.shared.CompanionGen
import a8.shared.jdbcf.SqlString.{DefaultJdbcEscaper, Escaper, NoopEscaper}
import a8.shared.jdbcf.mapper.{MapperBuilder, PK}
import a8.shared.jdbcf.{SqlString, querydsl}
import a8.shared.jdbcf.querydsl.MxQueryDslTest.{MxAddress, MxContainer, MxWidget}
import a8.shared.jdbcf.querydsl.QueryDsl.Join
import org.scalatest.funsuite.AnyFunSuite

import scala.language.implicitConversions
import org.scalatest.Assertion
import a8.shared.SharedImports.canEqual.given

object QueryDslTest {


  object Widget extends MxWidget {
  }
  @CompanionGen(jdbcMapper = true)
  @JoinTo(name = "container", expr = "from.containerId === to.id", to = "Container")
  case class Widget(@PK id: String, name: String, containerId: String)

  object Container extends MxContainer {
  }
  @CompanionGen(jdbcMapper = true)
  case class Container(@PK id: String, count: Long, name: String, address: Address)

  object Address extends MxAddress
  @CompanionGen(jdbcMapper = true)
  case class Address(
    line1: String,
    line2: String,
    city: String,
    state: String,
    zip: String,
  )

}


class QueryDslTest extends AnyFunSuite {

  implicit val defaultEscaper: Escaper = NoopEscaper

  implicit class SqlStringOps(sqlString: SqlString) {
    def compileToString: String =
      sqlString
        .compile
        .value
        .trim
  }

  import QueryDslTest._

  def assertEquals[A](expected: A, actual: A): Assertion =
    assertResult(expected)(actual)

  test("simpleJoinInWhere") {

    val query = Widget.query(aa => aa.id === "foo" and aa.container.id === "bar")

    val actual = query.sqlString.compileToString

    assertResult(
      "select aa.id, aa.name, aa.containerId from Widget as aa left join Container bb on aa.containerId = bb.id where aa.id = 'foo' and bb.id = 'bar'"
    )(
      actual
    )

  }

  test("or's and and's") {

    val query = Widget.query(aa => (aa.id === "foo" and aa.container.id === "bar") or (aa.id === "foo" and aa.container.id === "bar") or (aa.id === "foo" and aa.container.id === "bar" and aa.id === "foo" and aa.container.id === "bar"))

    val actual = query.sqlString.compileToString

//    println("== actual ==")
//    println(actual)

    assertResult(
      "select aa.id, aa.name, aa.containerId from Widget as aa left join Container bb on aa.containerId = bb.id where (aa.id = 'foo' and bb.id = 'bar') or (aa.id = 'foo' and bb.id = 'bar') or (aa.id = 'foo' and bb.id = 'bar' and aa.id = 'foo' and bb.id = 'bar')"
    )(
      actual
    )

  }

  test("simple or's and and's with no parens") {

    val query = Widget.query(aa => aa.id === "foo" and aa.container.id === "bar" or aa.id === "foo")

    val actual = query.sqlString.compileToString

//    println("== actual ==")
//    println(actual)

    assertResult(
      "select aa.id, aa.name, aa.containerId from Widget as aa left join Container bb on aa.containerId = bb.id where (aa.id = 'foo' and bb.id = 'bar') or aa.id = 'foo'"
    )(
      actual
    )

  }

  test("simple or's and and's with parens") {

    val query = Widget.query(aa => aa.id === "foo" and (aa.container.id === "bar" or aa.id === "foo"))

    val actual = query.sqlString.compileToString

//    println("== actual ==")
//    println(actual)

    assertResult(
      "select aa.id, aa.name, aa.containerId from Widget as aa left join Container bb on aa.containerId = bb.id where aa.id = 'foo' and (bb.id = 'bar' or aa.id = 'foo')"
    )(
      actual
    )

  }

  test("all and's") {

    val query = Widget.query(aa => aa.id === "foo" and aa.container.id === "bar" and aa.id === "foo" and aa.container.id === "bar" and aa.id === "foo")

    val actual = query.sqlString.compileToString

    println("== actual ==")
    println(actual)

    assertResult(
      "select aa.id, aa.name, aa.containerId from Widget as aa left join Container bb on aa.containerId = bb.id where aa.id = 'foo' and bb.id = 'bar' and aa.id = 'foo' and bb.id = 'bar' and aa.id = 'foo'"
    )(
      actual
    )

  }

  test("all or's") {

    val query = Widget.query(aa => aa.id === "foo" or aa.container.id === "bar" or aa.id === "foo" or aa.container.id === "bar" or aa.id === "foo")

    val actual = query.sqlString.compileToString

    println("== actual ==")
    println(actual)

    assertResult(
      "select aa.id, aa.name, aa.containerId from Widget as aa left join Container bb on aa.containerId = bb.id where aa.id = 'foo' or bb.id = 'bar' or aa.id = 'foo' or bb.id = 'bar' or aa.id = 'foo'"
    )(
      actual
    )

  }

  test("structural equality") {

    val address = Address("line1", "line2", "city", "state", "zip")

    val query = Container.query(aa => aa.address === address)

    val actual = query.sqlString.compileToString

    assertResult(
      "select aa.id, aa.count, aa.name, aa.addressline1, aa.addressline2, aa.addresscity, aa.addressstate, aa.addresszip from Container as aa where (aa.addressline1 = 'line1' and aa.addressline2 = 'line2' and aa.addresscity = 'city' and aa.addressstate = 'state' and aa.addresszip = 'zip')"
    )(
      actual
    )

  }

  test("structural in clause") {

    val address1 = Address("1line1", "1line2", "1city", "1state", "1zip")
    val address2 = Address("2line1", "2line2", "2city", "2state", "2zip")

    val query = Container.query(aa => aa.address in Iterable(address1,address2))

    val actual = query.sqlString.compileToString

    assertResult(
      "select aa.id, aa.count, aa.name, aa.addressline1, aa.addressline2, aa.addresscity, aa.addressstate, aa.addresszip from Container as aa where (aa.addressline1, aa.addressline2, aa.addresscity, aa.addressstate, aa.addresszip) in (('1line1', '1line2', '1city', '1state', '1zip'), ('2line1', '2line2', '2city', '2state', '2zip'))"
    )(
      actual
    )

  }
  test("structural in2 clause") {

    val address1 = Address("1line1", "1line2", "1city", "1state", "1zip")
    val address2 = Address("2line1", "2line2", "2city", "2state", "2zip")

    val query = Container.query(aa => aa.address in2 Iterable(address1,address2))

    val actual = query.sqlString.compileToString

    assertResult(
      "select aa.id, aa.count, aa.name, aa.addressline1, aa.addressline2, aa.addresscity, aa.addressstate, aa.addresszip from Container as aa where (aa.addressline1 = '1line1' and aa.addressline2 = '1line2' and aa.addresscity = '1city' and aa.addressstate = '1state' and aa.addresszip = '1zip') or (aa.addressline1 = '2line1' and aa.addressline2 = '2line2' and aa.addresscity = '2city' and aa.addressstate = '2state' and aa.addresszip = '2zip')"
    )(
      actual
    )

  }

  test("address line 1 equality") {

    val query = Container.query(aa => aa.address.line1 === "myline1")

    val actual = query.sqlString.compileToString

    assertResult(
      "select aa.id, aa.count, aa.name, aa.addressline1, aa.addressline2, aa.addresscity, aa.addressstate, aa.addresszip from Container as aa where aa.addressline1 = 'myline1'"
    )(
      actual
    )

  }

  test("simpleJoinInOrderBy") {

    val query = Widget.query(aa => aa.id === "foo" and aa.container.id === "bar").orderBys(aa=>List(aa.id, aa.container.name))

    val actual = query.sqlString.compileToString

    assertResult(
      "select aa.id, aa.name, aa.containerId from Widget as aa left join Container bb on aa.containerId = bb.id where aa.id = 'foo' and bb.id = 'bar' order by aa.id asc, bb.name asc"
    )(
      actual
    )

  }

  test("simple") {
    val query = Container.query(aa => aa.count >= 1L)
    val actual = query.sqlString.compileToString
    assertResult(
      """select aa.id, aa.count, aa.name, aa.addressline1, aa.addressline2, aa.addresscity, aa.addressstate, aa.addresszip from Container as aa where aa.count >= 1"""
    )(
      actual
    )
  }

  test("noneAsNull") {
    val query = Container.query(aa => aa.count === None)
    val actual = query.sqlString.compileToString
    assertResult(
      """select aa.id, aa.count, aa.name, aa.addressline1, aa.addressline2, aa.addresscity, aa.addressstate, aa.addresszip from Container as aa where aa.count is null"""
    )(
      actual
    )
  }

  test("someAsValue") {
    val query = Container.query(aa => aa.count === Some(1L))
    val actual = query.sqlString.compileToString
    assertResult(
      """select aa.id, aa.count, aa.name, aa.addressline1, aa.addressline2, aa.addresscity, aa.addressstate, aa.addresszip from Container as aa where aa.count = 1"""
    )(
      actual
    )
  }

//  test("updateQuery") {
//
//    val updateQuery = Widget.update(aa => aa.name := "foo").where(aa => aa.id === "xyz")
//
//    val actual = updateQuery.asSql.trim
//
//    assertEquals("update WIDGET as aa set name = 'foo' where aa.id = 'xyz'", actual)
//
//  }


  def runTests[TInput,TOutput](tests: List[(TInput,TOutput)])( code: TInput => TOutput ): Unit = {
    tests.foreach { case (input, expectedOutput) =>
      val actualOutput = code(input)
      assertEquals(expectedOutput, actualOutput)
    }
  }

}