package a8.shared

import java.sql.ResultSet
import a8.shared.StringValue.{CIStringValue, CIStringValueCompanion}
import a8.shared.jdbcf.SqlString.HasSqlString
import a8.shared.jdbcf.UnsafeResultSetOps.asImplicit
import cats.effect.Sync
import org.typelevel.ci.CIString

package object jdbcf {

  object ColumnName extends CIStringValueCompanion[ColumnName] {
  }
  case class ColumnName(value: CIString) extends SqlIdentifierValue

  object TableLocator {

    def apply(tableName: TableName): TableLocator =
      new TableLocator(tableName = tableName)

    def apply(schemaName: SchemaName, tableName: TableName): TableLocator =
      new TableLocator(tableName = tableName, schemaName = Some(schemaName))

    def apply(schemaName: Option[SchemaName], tableName: TableName): TableLocator =
      new TableLocator(tableName = tableName, schemaName = schemaName)

  }
  case class TableLocator(catalogName: Option[CatalogName] = None, schemaName: Option[SchemaName] = None, tableName: TableName) {
    /**
     * catalog appropriate for use in Connection.getMetadata calls (noting this value could be null
     * because that is how jdbc wants it
     */
    def metadataCatalog = catalogName.map(_.asString).orNull
    def metadataSchema = schemaName.map(_.asString).orNull
    def metadataTable = tableName.asString
  }

  trait SqlIdentifierValue extends CIStringValue with HasSqlString {
    override val asSqlFragment = SqlString.identifier(asString)
  }

  object TableName extends CIStringValueCompanion[TableName] {
  }
  case class TableName(value: CIString) extends SqlIdentifierValue {
    def asLowerCaseStringValue = SqlString.EscapedSqlString(asString.toLowerCase)
  }

  object SchemaName extends CIStringValueCompanion[SchemaName] {
  }
  case class SchemaName(value: CIString) extends SqlIdentifierValue {
    def asLowerCaseStringValue = SqlString.EscapedSqlString(asString.toLowerCase)
  }

  object CatalogName extends CIStringValueCompanion[CatalogName] {
  }
  case class CatalogName(value: CIString) extends SqlIdentifierValue

  object ResolvedTableName {
    def fromMetadataRow(row: Row): ResolvedTableName =
      new ResolvedTableName(
        row.opt[CatalogName]("TABLE_CAT"),
        row.opt[SchemaName]("TABLE_SCHEM"),
        row.get[TableName]("TABLE_NAME"),
      )()
  }

  case class ResolvedTableName(catalog: Option[CatalogName], schema: Option[SchemaName], name: TableName)(alternativeNames: Iterable[TableName] = Iterable.empty) {

    def asLocator = TableLocator(catalog, schema, name)

    def qualifiedName = (catalog.map(_.asString) ++ schema.map(_.asString) ++ Some(name.asString)).mkString(".")
  }

  case class TypeName(name: String, length: Option[Int] = None, digits: Option[Int] = None) extends HasSqlString {
    override def asSqlFragment = SqlString.typeName(this)
  }

  def resultSetToVector(resultSet: ResultSet): Vector[Row] = {
    resultSet.runAsIterator(_.toVector)
  }

  def resultSetToStream[F[_] : Sync](resultSet: ResultSet, chunkSize: Int = 1000): fs2.Stream[F,Row] = {
    val F = Sync[F]
    fs2.Stream.bracket(F.unit)(_ => F.blocking(if ( resultSet.isClosed ) () else resultSet.close()))
      .flatMap { _ =>
        fs2.Stream.fromBlockingIterator(unsafe.resultSetToIterator(resultSet), chunkSize)
      }
  }

}
