package a8.shared.jdbcf.mapper

import a8.shared.jdbcf.{ColumnName, SqlString}
import a8.shared.jdbcf.SqlString._
import a8.shared.jdbcf.mapper.CaseClassMapper.ColumnNameResolver
import a8.shared.jdbcf.querydsl.QueryDslTest
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.Assertion
import a8.shared.SharedImports.canEqual.given

class MapperTest extends AnyFunSuite {

  import QueryDslTest._

  implicit val defaultEscaper: Escaper = NoopEscaper

  implicit class SqlStringOps(sqlString: SqlString) {
    def compileToString: String =
      sqlString
        .compile
        .value
        .trim
  }

  def assertEquals[A](expected: A, actual: A): Assertion =
    assertResult(expected)(actual)

  test("fetchSql") {
    val actual = Widget.jdbcMapper.fetchSql("foo").compileToString
    assertResult(
      "select id, name, containerId from Widget where id = 'foo'"
    )(
      actual
    )
  }

  test("selectSql") {
    val actual = Widget.jdbcMapper.selectSql(sql"1 = 1").compileToString
    assertResult(
      "select id, name, containerId from Widget where 1 = 1"
    )(
      actual
    )
  }

  test("deleteSql") {
    val actual = Widget.jdbcMapper.deleteSql("foo").compileToString
    assertResult(
      "delete from Widget where id = 'foo'"
    )(
      actual
    )
  }

  test("updateSql") {
    val actual = Widget.jdbcMapper.updateSql(Widget("a", "b", "c")).compileToString
    assertResult(
      "update Widget set id = 'a', name = 'b', containerId = 'c' where id = 'a'"
    )(
      actual
    )
  }

  test("insertSql") {
    val actual = Widget.jdbcMapper.insertSql(Widget("a", "b", "c")).compileToString
    assertResult(
      "insert into Widget (id,name,containerId) values('a','b','c')"
    )(
      actual
    )
  }

  test("selectSql w/component") {
    val actual = Container.jdbcMapper.selectSql(sql"1 = 1").compileToString
    assertResult(
      "select id, count, name, addressline1, addressline2, addresscity, addressstate, addresszip from Container where 1 = 1"
    )(
      actual
    )
  }

  test("updateSql w/component") {
    val actual = Container.jdbcMapper.updateSql(Container("a", 2, "c", Address("d", "e", "f", "g", "h"))).compileToString
    assertResult(
      "update Container set id = 'a', count = 2, name = 'c', addressline1 = 'd', addressline2 = 'e', addresscity = 'f', addressstate = 'g', addresszip = 'h' where id = 'a'"
    )(
      actual
    )
  }

  test("insertSql w/component") {
    val actual = Container.jdbcMapper.insertSql(Container("a", 2, "c", Address("d", "e", "f", "g", "h"))).compileToString
    assertResult(
      "insert into Container (id,count,name,addressline1,addressline2,addresscity,addressstate,addresszip) values('a',2,'c','d','e','f','g','h')"
    )(
      actual
    )
  }


  object customColumnNameResolver extends ColumnNameResolver {
    val suffix: ColumnName = ColumnName("X")
    override def quote(columnName: ColumnName): DialectQuotedIdentifier =
      DialectQuotedIdentifier((columnName ~ suffix).asString)
  }
  lazy val customWidgetMapper: CaseClassMapper[Widget,String] = Widget.jdbcMapper.asInstanceOf[CaseClassMapper[Widget,String]].copy(columnNameResolver = customColumnNameResolver)
  lazy val customContainerMapper: CaseClassMapper[Container,String] = Container.jdbcMapper.asInstanceOf[CaseClassMapper[Container,String]].copy(columnNameResolver = customColumnNameResolver)

  test("fetchSql with custom columnNameResolver") {
    val actual = customWidgetMapper.fetchSql("foo").compileToString
    assertEquals(
      "select idX, nameX, containerIdX from Widget where idX = 'foo'",
      actual
    )
  }


  test("selectSql w/component w/customNameResolver") {
    val actual = customContainerMapper.selectSql(sql"1 = 1").compileToString
    assertEquals(
      "select idX, countX, nameX, addressline1X, addressline2X, addresscityX, addressstateX, addresszipX from Container where 1 = 1",
      actual
    )
  }
}
