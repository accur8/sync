package a8.shared.jdbcf.mapper

import a8.shared.jdbcf.SqlString._
import a8.shared.jdbcf.querydsl.QueryDslTest
import org.scalatest.funsuite.AnyFunSuite

class MapperTest extends AnyFunSuite {

  import QueryDslTest._

  def assertEquals[A](expected: A, actual: A) =
    assertResult(expected)(actual)

  test("fetchSql") {
    val actual = Widget.jdbcMapper.fetchSql("foo").toString
    assertEquals(
      "select id, name, containerId from Widget where id = 'foo'",
      actual
    )
  }

  test("selectSql") {
    val actual = Widget.jdbcMapper.selectSql(sql"1 = 1").toString
    assertEquals(
      "select id, name, containerId from Widget where 1 = 1",
      actual
    )
  }

  test("deleteSql") {
    val actual = Widget.jdbcMapper.deleteSql("foo").toString
    assertEquals(
      "delete from Widget where id = 'foo'",
      actual
    )
  }

  test("updateSql") {
    val actual = Widget.jdbcMapper.updateSql(Widget("a", "b", "c")).toString
    assertEquals(
      "update Widget set id = 'a', name = 'b', containerId = 'c' where id = 'a'",
      actual
    )
  }

  test("insertSql") {
    val actual = Widget.jdbcMapper.insertSql(Widget("a", "b", "c")).toString
    assertEquals(
      "insert into Widget (id,name,containerId) values('a','b','c')",
      actual
    )
  }

  test("selectSql w/component") {
    val actual = Container.jdbcMapper.selectSql(sql"1 = 1").toString
    assertEquals(
      "select id, count, name, addressline1, addressline2, addresscity, addressstate, addresszip from Container where 1 = 1",
      actual
    )
  }

  test("updateSql w/component") {
    val actual = Container.jdbcMapper.updateSql(Container("a", 2, "c", Address("d", "e", "f", "g", "h"))).toString
    assertEquals(
      "update Container set id = 'a', count = 2, name = 'c', addressline1 = 'd', addressline2 = 'e', addresscity = 'f', addressstate = 'g', addresszip = 'h' where id = 'a'",
      actual
    )
  }

  test("insertSql w/component") {
    val actual = Container.jdbcMapper.insertSql(Container("a", 2, "c", Address("d", "e", "f", "g", "h"))).toString
    assertEquals(
      "insert into Container (id,count,name,addressline1,addressline2,addresscity,addressstate,addresszip) values('a',2,'c','d','e','f','g','h')",
      actual
    )
  }

}
