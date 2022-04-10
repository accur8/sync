package a8.shared.jdbcf.mapper


import a8.shared.SharedImports._
import a8.shared.jdbcf.{ColumnName, Conn, Row, RowReader, SqlString, TableLocator, TableName}
import a8.shared.jdbcf.mapper.KeyedTableMapper.UpsertResult
import a8.shared.jdbcf.mapper.MapperBuilder.{Parm, PrimaryKey}
import SqlString._
import a8.shared
import a8.shared.jdbcf.mapper.CaseClassMapper.ColumnNameResolver
import a8.shared.{Chord, SharedImports, jdbcf}
import a8.shared.jdbcf.querydsl.QueryDsl
import a8.shared.jdbcf.querydsl.QueryDsl.{BooleanOperation, StructuralProperty}

import java.sql.PreparedStatement


object CaseClassMapper {
//  val QuestionMark = SqlString.keyword("?")
  val And = SqlString.keyword(" and ")
  val RootColumnNamePrefix = ColumnName("")

  object ColumnNameResolver {
    object noop extends ColumnNameResolver {
      override def apply(columnName: ColumnName): ColumnName =
        columnName
    }
  }

  trait ColumnNameResolver {
    def apply(columnName: ColumnName): ColumnName
  }

}

case class CaseClassMapper[A, PK](
  fields: Vector[Parm[A]],
  constructorFn: Iterator[Any]=>A,
  primaryKey: PrimaryKey[A,PK],
  tableName: TableName,
  columnNameResolver: ColumnNameResolver = ColumnNameResolver.noop,
) extends KeyedTableMapper[A, PK] { self =>

  import CaseClassMapper._

  implicit val rowReaderA: RowReader[A] = this

  lazy val columnCount = fields.map(_.columnCount).sum

  override def columnNames(columnNamePrefix: jdbcf.ColumnName): Iterable[jdbcf.ColumnName] =
    fields
      .flatMap(_.columnNames)
      .map(cn => ColumnName(columnNamePrefix.value.toString + cn.value.toString))

  override def structuralEquality(linker: QueryDsl.Linker, a: A)(implicit alias: QueryDsl.Linker => Chord): QueryDsl.Condition =
    fields
      .map(_.booleanOp(linker, a, columnNameResolver))
      .reduceLeft((l,r) => QueryDsl.And(l,r))

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

  lazy val resolvedColumnNames =
    fields
      .flatMap(_.columnNames)
      .map(columnNameResolver.apply)

  lazy val selectAndFrom = {
    val selectFields =
      resolvedColumnNames
        .mkSqlString(SqlString.CommaSpace)
    sql"select ${selectFields} from ${tableName}"
  }


  override def selectFieldsSql(alias: String): SqlString = {
    val aliasSqlStr = alias.keyword
    val selectFields =
      resolvedColumnNames
        .map(cn => sql"${aliasSqlStr}.${cn}")
        .mkSqlString(SqlString.CommaSpace)
    sql"select ${selectFields}"
  }

  lazy val selectFromAndWhere = sql"${selectAndFrom} where "

  override def key(row: A): PK =
    primaryKey.key(row)

  override def selectSql(whereClause: SqlString): SqlString =
    sql"${selectFromAndWhere}${whereClause}"

  def keyToWhereClause(key: PK): SqlString =
    primaryKey.whereClause(key, columnNameResolver)

  override def updateSql(row: A): SqlString = {
    val valuePairs = pairs(RootColumnNamePrefix, row)
    sql"update ${tableName} set ${valuePairs.map(p => sql"${p._1} = ${p._2}").mkSqlString(CommaSpace)} where ${keyToWhereClause(key(row))}"
  }

  override def deleteSql(key: PK): SqlString =
    sql"delete from ${tableName} where ${keyToWhereClause(key)}"

  override def fetchSql(key: PK): SqlString =
    selectSql(keyToWhereClause(key))

  override def pairs(columnNamePrefix: ColumnName, row: A): Iterable[(ColumnName, SqlString)] =
    fields.flatMap(_.pairs(columnNamePrefix, row))

  override def insertSql(row: A): SqlString = {
    val valuePairs = pairs(RootColumnNamePrefix, row)
    sql"insert into ${tableName} (${valuePairs.map(_._1).mkSqlString(Comma)}) values(${valuePairs.map(_._2).mkSqlString(SqlString.Comma)})"
  }

  override def materializeKeyedTableMapper[F[_] : SharedImports.Async](implicit conn: Conn[F]): F[KeyedTableMapper[A, PK]] = {
    conn
      .resolveTableName(TableLocator(tableName))
      .flatMap(rtm => conn.tableMetadata(rtm.asLocator))
      .flatMap { tableMeta =>
        val noPrefix = ColumnName("")
        conn
          .asInternal
          .withStatement { st =>
            Async[F].blocking {
              val mappedColumnNames =
                tableMeta
                  .columns
                  .map { rc =>
                    rc.name -> ColumnName(st.enquoteIdentifier(rc.name.value.toString, false))
                  }
                  .toMap

              val columnNameResolver =
                new ColumnNameResolver {
                  override def apply(columnName: ColumnName): ColumnName =
                    mappedColumnNames.getOrElse(columnName, columnName)
                }

              copy(columnNameResolver = columnNameResolver)
            }
          }
      }
  }

}
