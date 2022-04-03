package a8.shared.jdbcf.querydsl


import a8.shared.CompanionGen
import a8.shared.jdbcf.mapper.{MapperBuilder, PK}
import a8.shared.jdbcf.querydsl
import a8.shared.jdbcf.querydsl.MxQueryDslTest.{MxContainer, MxWidget}
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
  case class Container(@PK id: String, count: Long, name: String)


}


class QueryDslTest extends AnyFunSuite {

  import QueryDslTest._

  def assertEquals[A](expected: A, actual: A) =
    assertResult(expected)(actual)

  test("simpleJoinInWhere") {

    val query = Widget.query[IO](aa => aa.id === "foo" and aa.container.id === "bar")

    val actual = query.asSql.trim

    assertEquals("select aa.id, aa.name, aa.containerId from Widget as aa left join Container bb on aa.containerId = bb.id where aa.id = 'foo' and bb.id = 'bar'", actual)

  }

  test("simpleJoinInOrderBy") {

    val query = Widget.query[IO](aa => aa.id === "foo" and aa.container.id === "bar").orderBys(aa=>List(aa.id, aa.container.name))

    val actual = query.asSql.trim

    assertEquals("select aa.id, aa.name, aa.containerId from Widget as aa left join Container bb on aa.containerId = bb.id where aa.id = 'foo' and bb.id = 'bar' order by aa.id ASC, bb.name ASC", actual)

  }

  test("simple") {
    val query = Container.query[IO](aa => aa.count >= 1L)
    val actual = query.asSql.trim
    assertEquals("""select aa.id, aa.count, aa.name from Container as aa where aa.count >= 1""", actual)
  }

  test("noneAsNull") {
    val query = Container.query[IO](aa => aa.count === None)
    val actual = query.asSql.trim
    assertEquals("""select aa.id, aa.count, aa.name from Container as aa where aa.count is null""", actual)
  }

  test("someAsValue") {
    val query = Container.query[IO](aa => aa.count === Some(1L))
    val actual = query.asSql.trim
    assertEquals("""select aa.id, aa.count, aa.name from Container as aa where aa.count = 1""", actual)
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