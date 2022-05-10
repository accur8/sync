package a8.shared.jdbcf.mapper


import a8.shared.SharedImports._
import a8.shared.jdbcf.{ColumnName, Conn, JdbcMetadata, Row, RowReader, SqlString, TableLocator, TableName}
import a8.shared.jdbcf.mapper.KeyedTableMapper.UpsertResult
import a8.shared.jdbcf.mapper.MapperBuilder.{FromCaseClassParm, Parm, PrimaryKey}
import SqlString._
import a8.shared
import a8.shared.jdbcf.JdbcMetadata.ResolvedJdbcTable
import a8.shared.jdbcf.mapper.CaseClassMapper.ColumnNameResolver
import a8.shared.{Chord, SharedImports, jdbcf}
import a8.shared.jdbcf.querydsl.QueryDsl
import a8.shared.jdbcf.querydsl.QueryDsl.{BooleanOperation, LinkCompiler, StructuralProperty}

import java.sql.PreparedStatement


object CaseClassMapper {
//  val QuestionMark = SqlString.keyword("?")
  val And = SqlString.keyword(" and ")
  val RootColumnNamePrefix = ColumnName("")

  object ColumnNameResolver {
    object noop extends ColumnNameResolver {
      override def quote(columnName: ColumnName): DialectQuotedIdentifier =
        DialectQuotedIdentifier(columnName.asString)
    }
  }

  trait ColumnNameResolver {
    def quote(columnName: ColumnName): DialectQuotedIdentifier
  }

}

case class CaseClassMapper[A, PK](
  rawFields: Vector[Parm[A]],
  constructorFn: Iterator[Any]=>A,
  primaryKey: PrimaryKey[A,PK],
  tableName: TableName,
  columnNameResolver: ColumnNameResolver = ColumnNameResolver.noop,
) extends KeyedTableMapper[A, PK] { self =>

  import CaseClassMapper._

  implicit val rowReaderA: RowReader[A] = this

  lazy val fields = rawFields.sortBy(_.ordinal)



  // validate ordinals
  fields.zipWithIndex.find(t => t._1.ordinal != t._2) match {
    case Some(field) =>
      sys.error(s"ordinal mismatch at ${field}")
    case None =>
    // success
  }


  override def materializeComponentMapper[F[_]: Async](columnNamePrefix: ColumnName, conn: Conn[F], resolvedJdbcTable: JdbcMetadata.ResolvedJdbcTable): F[ComponentMapper[A]] =
    rawFields
      .map { parm =>
        parm.materialize(columnNamePrefix, conn, resolvedJdbcTable)
      }
      .sequence
      .map(materializedParms => copy(rawFields = materializedParms))


  lazy val columnCount = fields.map(_.columnCount).sum

  override def columnNames(columnNamePrefix: jdbcf.ColumnName): Iterable[jdbcf.ColumnName] =
    fields
      .flatMap(_.columnNames)
      .map(cn => ColumnName(columnNamePrefix.value.toString + cn.value.toString))

  override def structuralEquality(linker: QueryDsl.Linker, a: A)(implicit alias: LinkCompiler): QueryDsl.Condition =
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
      .map(columnNameResolver.quote)

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
    def columnNameResolver0(tableMeta: ResolvedJdbcTable) = {
      val mappedColumnNames: Map[ColumnName, DialectQuotedIdentifier] =
        tableMeta
          .columns
          .map { rc =>
            rc.name -> SqlString.DialectQuotedIdentifier(rc.name.value.toString)
          }
          .toMap

      val columnNameResolver =
        new ColumnNameResolver {
          override def quote(columnName: ColumnName): DialectQuotedIdentifier =
            mappedColumnNames.getOrElse(columnName, DialectQuotedIdentifier(columnName.asString))
        }

      columnNameResolver
    }
//        }

  val columnNamePrefix = ColumnName("")
    for {
      tableName <- conn.resolveTableName(TableLocator(tableName))
      resolvedJdbcTable <- conn.tableMetadata(tableName.asLocator)
      columnNameResolver = columnNameResolver0(resolvedJdbcTable)
      materializedRawFields <-
        rawFields
          .map { parm =>
            parm.materialize(columnNamePrefix, conn, resolvedJdbcTable)
          }
          .sequence
    } yield
      copy(
        rawFields = materializedRawFields,
        columnNameResolver = columnNameResolver,
      )
  }

}
