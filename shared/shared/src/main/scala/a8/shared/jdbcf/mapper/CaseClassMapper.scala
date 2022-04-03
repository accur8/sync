package a8.shared.jdbcf.mapper


import a8.shared.SharedImports._
import a8.shared.jdbcf.{Conn, Row, RowReader, SqlString, TableName}
import a8.shared.jdbcf.mapper.KeyedTableMapper.UpsertResult
import a8.shared.jdbcf.mapper.MapperBuilder.{Parm, PrimaryKey}
import SqlString._
import a8.shared.jdbcf

import java.sql.PreparedStatement


object CaseClassMapper {
  val QuestionMark = SqlString.keyword("?")
  val And = SqlString.keyword(" and ")
}

case class CaseClassMapper[A, PK](
  fields: Vector[Parm[A]],
  constructorFn: Iterator[Any]=>A,
  primaryKey: PrimaryKey[A,PK],
  tableName: TableName,
) extends KeyedTableMapper[A, PK] {

  import CaseClassMapper._

  implicit val rowReaderA: RowReader[A] = this

  override def sqlString(a: A): Option[SqlString] =
    None

  override def rawRead(row: Row, index: Int): (A, Int) = {
    var offset = 0
    val valuesIterator =
      fields
        .iterator
        .map { field =>
          val next = field.rawRead(row, offset+index)
          offset += next._2
          next._1
        }
    constructorFn(valuesIterator) -> offset
  }

  lazy val selectAndFrom = {
    val selectFields =
      fields
        .flatMap(_.columnNames)
        .mkSqlString(SqlString.CommaSpace)
    sql"select ${selectFields} from ${tableName}"
  }


  override def selectFieldsSqlPs(alias: String): SqlString = {
    val aliasSqlStr = alias.keyword
    val selectFields =
      fields
        .flatMap(_.columnNames)
        .map(cn => sql"${aliasSqlStr}.${cn}")
        .mkSqlString(SqlString.CommaSpace)
    sql"select ${selectFields}"
  }

  lazy val selectFromAndWhere = sql"${selectAndFrom} where "


  override def applyParameters(ps: PreparedStatement, a: A, parameterIndex: Int): Unit =
    fields.foldLeft(parameterIndex) { case (index, parm) =>
      parm.applyParameters(ps, a, index)
      index + parm.columnCount
    }

  override def applyInsert(ps: PreparedStatement, a: A): Unit =
    applyParameters(ps, a, 1)

  override def applyUpdate(ps: PreparedStatement, a: A): Unit = {
    // apply set
    val offset =
      fields.foldLeft(0) { case (offset, parm) =>
        parm.applyParameters(ps, a, offset)
        offset + parm.columnCount
      }
    // apply where
    primaryKey.applyParameters(ps, key(a), offset)
  }


  override val parameterCount: Int = fields.size

  override def columnNames(columnNamePrefix: jdbcf.ColumnName): Iterable[jdbcf.ColumnName] =
    fields.flatMap(_.columnNames)

  override def applyWhere(ps: PreparedStatement, b: PK, parameterIndex: Int): Unit =
    primaryKey.applyParameters(ps, b, parameterIndex)

  override def key(row: A): PK =
    primaryKey.key(row)

  override def selectSql(whereClause: SqlString): SqlString =
    sql"${selectFromAndWhere}${whereClause}"

  override lazy val fetchSqlPs = selectSql(fetchSqlPs)

  override lazy val insertSqlPs =
    sql"insert into ${tableName} (${fields.flatMap(_.columnNames).mkSqlString(Comma)}) values(${fields.map(_ => QuestionMark).mkSqlString(SqlString.Comma)})"

  override lazy val deleteSqlPs: SqlString =
    sql"delete from ${tableName} where ${fetchWherePs}"

  override lazy val updateSqlPs: SqlString =
    sql"update ${tableName} set ${fields.map(_ => QuestionMark).mkSqlString(Comma)} where ${fetchWherePs}"

  override lazy val fetchWherePs: SqlString =
    primaryKey
      .whereClausePs

}
