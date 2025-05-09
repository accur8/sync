package a8.shared.jdbcf.mapper

import a8.shared.jdbcf.JdbcMetadata.ResolvedJdbcTable
import a8.shared.jdbcf.SqlString.SqlStringer
import a8.shared.jdbcf.mapper.CaseClassMapper.ColumnNameResolver
import a8.shared.{Chord, jdbcf}
import a8.shared.jdbcf.querydsl.QueryDsl
import a8.shared.jdbcf.querydsl.QueryDsl.{ComponentJoin, Path, PathCompiler, StructuralProperty}
import a8.shared.jdbcf.{ColumnName, Conn, Row, RowReader, RowWriter, SqlString}
import a8.shared.SharedImports._
import cats.data.Chain
import zio._

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

    def apply[A: FieldHandler]: FieldHandler[A] =
      implicitly[FieldHandler[A]]

  }

  sealed trait FieldHandler[A] {
    def materialize(columnNamePrefix: ColumnName, conn: Conn, resolvedJdbcTable: ResolvedJdbcTable): Task[FieldHandler[A]]
    val rowReader: RowReader[A]
    def booleanOp(linker: QueryDsl.Path, name: String, a: A, columnNameResolver: ColumnNameResolver)(implicit alias: PathCompiler): QueryDsl.Condition
    def fieldExprs(linker: QueryDsl.Path, name: String, columnNameResolver: ColumnNameResolver): Vector[QueryDsl.FieldExpr[?]]
    def values(a: A): Vector[QueryDsl.Constant[?]]
    def columnNames(columnNamePrefix: ColumnName): Iterable[ColumnName]
    def pairs(columnNamePrefix: ColumnName, a: A): Iterable[(ColumnName, SqlString)]
    val columnCount: Int
  }

  class SingleFieldHandler[A](
    implicit
      val rowReader: RowReader[A],
      val sqlStringer: SqlStringer[A]
  ) extends FieldHandler[A] {

    override def fieldExprs(linker: Path, name: String, columnNameResolver: ColumnNameResolver): Vector[QueryDsl.Field[?]] = {
      import QueryDsl._
      val resolvedName = columnNameResolver.quote(linker.columnName(ColumnName(name)))
      Vector(Field(resolvedName.value, linker, true))
    }

    override def values(a: A): Vector[QueryDsl.Constant[?]] =
      Vector(QueryDsl.Constant(a))

    override def materialize(columnNamePrefix: ColumnName, conn: Conn, resolvedJdbcTable: ResolvedJdbcTable): Task[FieldHandler[A]] =
      for {
        materializedRowReader <- rowReader.materialize(columnNamePrefix, conn, resolvedJdbcTable)
        resolvedColumn <-
          resolvedJdbcTable.columnsByName.get(columnNamePrefix) match {
            case None =>
              ZIO.fail(new RuntimeException(s"no column named ${columnNamePrefix} found in ${resolvedJdbcTable.resolvedTableName}"))
            case Some(rc) =>
              ZIO.succeed(rc)
          }
        materializedSqlStringer <- sqlStringer.materialize(conn, resolvedColumn)
      } yield
        new SingleFieldHandler[A]()(materializedRowReader, materializedSqlStringer)

    def columnNames(columnNamePrefix: ColumnName): Iterable[ColumnName] = Iterable(columnNamePrefix)
    val columnCount = 1
    override def booleanOp(linker: Path, name: String, a: A, columnNameResolver: ColumnNameResolver)(implicit alias: PathCompiler): QueryDsl.Condition = {
      import QueryDsl._
      val resolvedName = columnNameResolver.quote(linker.columnName(ColumnName(name)))
      BooleanOperation(Field(resolvedName.value, linker, true), Ops.Equal, Constant(a))
    }
    def pairs(columnNamePrefix: ColumnName, a: A): Iterable[(ColumnName, SqlString)] = Iterable(columnNamePrefix -> sqlStringer.toSqlString(a))
  }

  class ComponentFieldHandler[A](implicit componentMapper: ComponentMapper[A]) extends FieldHandler[A] {

    override def fieldExprs(linker: Path, name: String, columnNameResolver: ColumnNameResolver): Vector[QueryDsl.FieldExpr[?]] = {
      val componentLinker = ComponentJoin(name, linker)
      componentMapper.fieldExprs(componentLinker)
    }

    override def values(a: A): Vector[QueryDsl.Constant[?]] =
      ???

    override def materialize(columnNamePrefix: ColumnName, conn: Conn, resolvedJdbcTable: ResolvedJdbcTable): Task[FieldHandler[A]] =
      componentMapper
        .materializeComponentMapper(columnNamePrefix, conn, resolvedJdbcTable)
        .map { materializedComponentMapper =>
          new ComponentFieldHandler[A]()(materializedComponentMapper)
        }

    val rowReader = componentMapper
    def columnNames(columnNamePrefix: ColumnName): Iterable[ColumnName] = componentMapper.columnNames(columnNamePrefix)
    val columnCount = componentMapper.columnCount

    override def booleanOp(linker: Path, name: String, a: A, columnNameResolver: ColumnNameResolver)(implicit alias: PathCompiler): QueryDsl.Condition = {
      val componentLinker = ComponentJoin(name, linker)
      componentMapper.structuralEquality(componentLinker, Iterable(a))
    }

    def pairs(columnNamePrefix: ColumnName, a: A): Iterable[(ColumnName, SqlString)] =
      componentMapper.pairs(columnNamePrefix, a)

  }

}

trait Mapper[A] extends /*RowWriter[A] with*/ RowReader[A] {
}
