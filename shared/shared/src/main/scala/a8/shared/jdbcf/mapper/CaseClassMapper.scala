package a8.shared.jdbcf.mapper


import a8.shared.SharedImports._
import a8.shared.jdbcf.{Conn, Row, RowReader, SqlString, TableName}
import a8.shared.jdbcf.mapper.KeyedMapper.UpsertResult
import a8.shared.jdbcf.mapper.MapperBuilder.{Parm, PrimaryKey}
import SqlString._


case class CaseClassMapper[A, PK](
  fields: Vector[Parm[A]],
  constructorFn: Iterator[Any]=>A,
  primaryKey: PrimaryKey[A,PK],
  tableName: TableName,
) extends KeyedMapper[A, PK] {

  implicit val rowReaderA: RowReader[A] = this

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
        .map(_.columnName)
        .mkSqlString(SqlString.Comma)
    sql"select ${selectFields} from ${tableName}"
  }

  lazy val selectFromAndWhere = sql"${selectAndFrom} where "



//  override def select[F[_] : Async](whereClause: SqlString)(implicit conn: Conn[F]): F[List[A]] =
//    conn
//      .query[A](sql"${selectFromAndWhere}${whereClause}")
//      .select
//      .map(_.toList)
//
//  override def streamingSelect[F[_] : Async](whereClause: SqlString)(implicit conn: Conn[F]): fs2.Stream[F, A] =
//    conn
//      .streamingQuery[A](sql"${selectFromAndWhere}${whereClause}")
//      .run
//
//
//  override def upsert[F[_] : Async](record: A)(implicit conn: Conn[F]): F[UpsertResult] =
//    fetchOpt[F](primaryKey.key(record))
//    .flatMap {
//      case None =>
//        insert(record).as(UpsertResult.Insert)
//      case Some(_) =>
//        update(record).as(UpsertResult.Update)
//    }
//
//  override def insert[F[_] : Async](record: A)(implicit conn: Conn[F]): F[Unit] =
//    runUpdate(sql"insert into ${tableName} (${fields.map(_.columnName).mkSqlString(SqlString.Comma)}) VALUES(${fields.map(_.sqlString(record)).mkSqlString(SqlString.Comma)})")
//
//  override def update[F[_] : Async](record: A)(implicit conn: Conn[F]): F[Unit] =
//    runUpdate(sql"update ${tableName} set ${fields.map(f => sql"${f.columnName} = ${f.sqlString(record)}").mkSqlString(SqlString.Comma)} where ${where(primaryKey.key(record))}")
//
//  override def delete[F[_] : Async](record: A)(implicit conn: Conn[F]): F[Unit] =
//    runUpdate(sql"delete from ${tableName} where ${where(primaryKey.key(record))}")
//
//  def runUpdate[F[_] : Async](ss: SqlString)(implicit conn: Conn[F]): F[Unit] = {
//    val F = Async[F]
//    conn
//      .update(ss)
//      .flatMap {
//        case 0 =>
//          F.raiseError[Unit](new RuntimeException(s"expected to affect a single row and did not affect any -- ${ss}"))
//        case 1 =>
//          F.unit
//        case i =>
//          F.raiseError[Unit](new RuntimeException(s"expected to affect a single row and affected ${i} -- ${ss}"))
//      }
//      .void
//  }
//
//  override def fetch[F[_] : Async](key: PK)(implicit conn: Conn[F]): F[A] =
//    fetchOpt[F](key)
//      .flatMap {
//        case None =>
//          Async[F].raiseError(new RuntimeException(s"no record found for key ${key}"))
//        case Some(v) =>
//          Async[F].pure(v)
//      }
//
//  override def fetchOpt[F[_] : Async](key: PK)(implicit conn: Conn[F]): F[Option[A]] = {
//    val F = MonadCancel[F]
//    select(where(key))
//      .flatMap {
//        case List() =>
//          F.pure(None)
//        case List(a) =>
//          F.pure(Some(a))
//        case l =>
//          F.raiseError(new RuntimeException(s"too many rows returned ${l}"))
//      }
//  }

  override def updateSql(record: A): SqlString =
    sql"update ${tableName} set ${fields.map(f => sql"${f.columnName} = ${f.sqlString(record)}").mkSqlString(SqlString.Comma)} where ${fetchWhere(primaryKey.key(record))}"

  override def deleteSql(record: A): SqlString =
    sql"delete from ${tableName} where ${fetchWhere(primaryKey.key(record))}"

  override def fetchSql(key: PK): SqlString =
    selectSql(fetchWhere(key))

  override def key(row: A): PK =
    primaryKey.key(row)

  override def insertSql(record: A): SqlString =
    sql"insert into ${tableName} (${fields.map(_.columnName).mkSqlString(SqlString.Comma)}) VALUES(${fields.map(_.sqlString(record)).mkSqlString(SqlString.Comma)})"

  override def selectSql(whereClause: SqlString): SqlString =
    sql"${selectFromAndWhere}${whereClause}"

  override def fetchWhere(key: PK): SqlString = {
    primaryKey.whereClause(key)
  }

}
