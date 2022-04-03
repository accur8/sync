package a8.shared.jdbcf.mapper

import a8.shared.Chord
import a8.shared.jdbcf.querydsl.QueryDsl
import a8.shared.jdbcf.querydsl.QueryDsl.{Linker, StructuralProperty}
import a8.shared.jdbcf.{RowReader, RowWriter}

trait Mapper[A] extends RowWriter[A] with RowReader[A] {

  def structuralEquality(linker: QueryDsl.Linker, a: A)(implicit alias: Linker => Chord): QueryDsl.Condition

}
