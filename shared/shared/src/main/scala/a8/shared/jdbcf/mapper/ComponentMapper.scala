package a8.shared.jdbcf.mapper

import a8.shared.Chord
import a8.shared.jdbcf.{ColumnName, SqlString}
import a8.shared.jdbcf.querydsl.QueryDsl
import a8.shared.jdbcf.querydsl.QueryDsl.Linker

import java.sql.PreparedStatement

trait ComponentMapper[A] extends Mapper[A] {

  def structuralEquality(linker: QueryDsl.Linker, a: A)(implicit alias: Linker => Chord): QueryDsl.Condition
  def columnNames(columnNamePrefix: ColumnName): Iterable[ColumnName]
  val columnCount: Int
  def pairs(columnNamePrefix: ColumnName, a: A): Iterable[(ColumnName,SqlString)]

}
