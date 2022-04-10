package a8.shared.jdbcf.mapper

import a8.shared.jdbcf.SqlString.SqlStringer
import a8.shared.jdbcf.mapper.CaseClassMapper.ColumnNameResolver
import a8.shared.{Chord, jdbcf}
import a8.shared.jdbcf.querydsl.QueryDsl
import a8.shared.jdbcf.querydsl.QueryDsl.{ComponentJoin, Linker, StructuralProperty}
import a8.shared.jdbcf.{ColumnName, Row, RowReader, RowWriter, SqlString}

import java.sql.PreparedStatement

object Mapper {
  def apply[A : RowWriter : RowReader]: Mapper[A] = {
    val rowReader = RowReader[A]
    val rowWriter = RowWriter[A]
    new Mapper[A] {

      override def rawRead(row: Row, index: Int): (A, Int) =
        rowReader.rawRead(row, index)

//      override val parameterCount: Int =
//        rowWriter.parameterCount
//
//      override def columnNames(columnNamePrefix: jdbcf.ColumnName): Iterable[jdbcf.ColumnName] =
//        rowWriter.columnNames(columnNamePrefix)
//
//      override def applyParameters(ps: PreparedStatement, a: A, parameterIndex: Int): Unit =
//        rowWriter.applyParameters(ps, a, parameterIndex)
//
//      override def sqlString(a: A): Option[SqlString] =
//        rowWriter.sqlString(a)

    }
  }

  object FieldHandler {

    implicit def fromComponentMapper[A : ComponentMapper]: FieldHandler[A] =
      new ComponentFieldHandler[A]

    implicit def fromRowReaderAndSqlString[A : RowReader : SqlStringer]: FieldHandler[A] =
      new SingleFieldHandler[A]

    def apply[A: FieldHandler] =
      implicitly[FieldHandler[A]]

  }

  sealed trait FieldHandler[A] {
    val rowReader: RowReader[A]
    def booleanOp(linker: QueryDsl.Linker, name: String, a: A, columnNameResolver: ColumnNameResolver)(implicit alias: Linker => Chord): QueryDsl.Condition
    def columnNames(columnNamePrefix: ColumnName): Iterable[ColumnName]
    def pairs(columnNamePrefix: ColumnName, a: A): Iterable[(ColumnName, SqlString)]
    val columnCount: Int
  }

  class SingleFieldHandler[A](
    implicit
      val rowReader: RowReader[A],
      val sqlStringer: SqlStringer[A]
  ) extends FieldHandler[A] {
    def columnNames(columnNamePrefix: ColumnName) = Iterable(columnNamePrefix)
    val columnCount = 1
    override def booleanOp(linker: Linker, name: String, a: A, columnNameResolver: ColumnNameResolver)(implicit alias: Linker => Chord): QueryDsl.Condition = {
      import QueryDsl._
      val resolvedName = columnNameResolver(linker.columnName(ColumnName(name)))
      BooleanOperation(Field(resolvedName.toString, linker), ops.eq, Constant(a))
    }
    def pairs(columnNamePrefix: ColumnName, a: A) = Iterable(columnNamePrefix -> sqlStringer.toSqlString(a))
  }

  class ComponentFieldHandler[A](implicit componentMapper: ComponentMapper[A]) extends FieldHandler[A] {
    val rowReader = componentMapper
    def columnNames(columnNamePrefix: ColumnName) = componentMapper.columnNames(columnNamePrefix)
    val columnCount = componentMapper.columnCount

    override def booleanOp(linker: Linker, name: String, a: A, columnNameResolver: ColumnNameResolver)(implicit alias: Linker => Chord): QueryDsl.Condition = {
      val componentLinker = ComponentJoin(name, linker)
      componentMapper.structuralEquality(componentLinker, a)
    }

    def pairs(columnNamePrefix: ColumnName, a: A) =
      componentMapper.pairs(columnNamePrefix, a)

  }

}

trait Mapper[A] extends /*RowWriter[A] with*/ RowReader[A] {
}
