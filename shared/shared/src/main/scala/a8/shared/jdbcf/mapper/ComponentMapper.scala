package a8.shared.jdbcf.mapper

import a8.shared
import a8.shared.{Chord, SharedImports}
import a8.shared.jdbcf.{ColumnName, Conn, JdbcMetadata, RowReader, SqlString}
import a8.shared.jdbcf.querydsl.QueryDsl
import a8.shared.jdbcf.querydsl.QueryDsl.{PathCompiler, Path}

import java.sql.PreparedStatement
import SharedImports._

trait ComponentMapper[A] extends Mapper[A] {

  override def materialize[F[_] : Async](columnNamePrefix: ColumnName, conn: Conn[F], resolvedJdbcTable: JdbcMetadata.ResolvedJdbcTable): F[RowReader[A]] =
    materializeComponentMapper[F](columnNamePrefix, conn, resolvedJdbcTable)
      .map {
        case rr: RowReader[A] => rr
      }

  def materializeComponentMapper[F[_] : Async](columnNamePrefix: ColumnName, conn: Conn[F], resolvedJdbcTable: JdbcMetadata.ResolvedJdbcTable): F[ComponentMapper[A]]

  def structuralEquality(linker: QueryDsl.Path, a: A)(implicit alias: PathCompiler): QueryDsl.Condition
  def columnNames(columnNamePrefix: ColumnName): Iterable[ColumnName]
  val columnCount: Int
  def pairs(columnNamePrefix: ColumnName, a: A): Iterable[(ColumnName,SqlString)]

}
