package a8.shared.jdbcf.querydsl


import a8.shared.CompanionGen
import a8.shared.jdbcf.SqlString.{DefaultJdbcEscaper, Escaper, NoopEscaper}
import a8.shared.jdbcf.mapper.{MapperBuilder, PK}
import a8.shared.jdbcf.{SqlString, querydsl}
import a8.shared.jdbcf.querydsl.MxQueryDslTest.{MxAddress, MxContainer, MxWidget}
import a8.shared.jdbcf.querydsl.QueryDsl.Join
import cats.effect.{Async, IO}
import org.scalatest.funsuite.AnyFunSuite

import scala.language.implicitConversions

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

  def assertEquals[A](expected: A, actual: A) =
    assertResult(expected)(actual)

  test("simpleJoinInWhere") {

    val query = Widget.query[IO](aa => aa.id === "foo" and aa.container.id === "bar")

    val actual = query.sqlString.compileToString

    assertResult(
      "select aa.id, aa.name, aa.containerId from Widget as aa left join Container bb on aa.containerId = bb.id where aa.id = 'foo' and bb.id = 'bar'"
    )(
      actual
    )

  }

  test("structural equality") {

    val address = Address("line1", "line2", "city", "state", "zip")

    val query = Container.query[IO](aa => aa.address === address)

    val actual = query.sqlString.compileToString

    assertResult(
      "select aa.id, aa.count, aa.name, aa.addressline1, aa.addressline2, aa.addresscity, aa.addressstate, aa.addresszip from Container as aa where aa.addressline1 = 'line1' and aa.addressline2 = 'line2' and aa.addresscity = 'city' and aa.addressstate = 'state' and aa.addresszip = 'zip'"
    )(
      actual
    )

  }

  test("address line 1 equality") {

    val query = Container.query[IO](aa => aa.address.line1 === "myline1")

    val actual = query.sqlString.compileToString

    assertResult(
      "select aa.id, aa.count, aa.name, aa.addressline1, aa.addressline2, aa.addresscity, aa.addressstate, aa.addresszip from Container as aa where aa.addressline1 = 'myline1'"
    )(
      actual
    )

  }

  test("simpleJoinInOrderBy") {

    val query = Widget.query[IO](aa => aa.id === "foo" and aa.container.id === "bar").orderBys(aa=>List(aa.id, aa.container.name))

    val actual = query.sqlString.compileToString

    assertResult(
      "select aa.id, aa.name, aa.containerId from Widget as aa left join Container bb on aa.containerId = bb.id where aa.id = 'foo' and bb.id = 'bar' order by aa.id asc, bb.name asc"
    )(
      actual
    )

  }

  test("simple") {
    val query = Container.query[IO](aa => aa.count >= 1L)
    val actual = query.sqlString.compileToString
    assertResult(
      """select aa.id, aa.count, aa.name, aa.addressline1, aa.addressline2, aa.addresscity, aa.addressstate, aa.addresszip from Container as aa where aa.count >= 1"""
    )(
      actual
    )
  }

  test("noneAsNull") {
    val query = Container.query[IO](aa => aa.count === None)
    val actual = query.sqlString.compileToString
    assertResult(
      """select aa.id, aa.count, aa.name, aa.addressline1, aa.addressline2, aa.addresscity, aa.addressstate, aa.addresszip from Container as aa where aa.count is null"""
    )(
      actual
    )
  }

  test("someAsValue") {
    val query = Container.query[IO](aa => aa.count === Some(1L))
    val actual = query.sqlString.compileToString
    assertResult(
      """select aa.id, aa.count, aa.name, aa.addressline1, aa.addressline2, aa.addresscity, aa.addressstate, aa.addresszip from Container as aa where aa.count = 1"""
    )(
      actual
    )
  }

//  test("updateQuery") {
//
//    val updateQuery = Widget.update[IO](aa => aa.name := "foo").where(aa => aa.id === "xyz")
//
//    val actual = updateQuery.asSql.trim
//
//    assertEquals("update WIDGET as aa set name = 'foo' where aa.id = 'xyz'", actual)
//
//  }


  def runTests[TInput,TOutput](tests: List[(TInput,TOutput)])( code: TInput => TOutput ) = {
    tests.foreach { case (input, expectedOutput) =>
      val actualOutput = code(input)
      assertEquals(expectedOutput, actualOutput)
    }
  }

}