package a8.shared.jdbcf


import a8.shared.CompanionGen

import java.sql.ResultSet
import a8.shared.StringValue.{CIStringValue, CIStringValueCompanion}
import a8.shared.jdbcf.Mxmodel.MxResolvedTableName
import a8.shared.jdbcf.SqlString.HasSqlString
import a8.shared.jdbcf.UnsafeResultSetOps.asImplicit
import org.typelevel.ci.CIString
import scala.language.implicitConversions

object ColumnName extends CIStringValueCompanion[ColumnName] {
}
case class ColumnName(value: CIString) extends SqlIdentifierValue {
  def ~(right: ColumnName) =
    ColumnName(value.toString + right.value.toString)
}

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

object TableName extends CIStringValueCompanion[TableName]
case class TableName(value: CIString) extends SqlIdentifierValue {
  def asLowerCaseStringValue = SqlString.EscapedSqlString(asString.toLowerCase)
  def asSqlString: SqlString = ???
}

object SchemaName extends CIStringValueCompanion[SchemaName] {
}
case class SchemaName(value: CIString) extends SqlIdentifierValue {
  def asLowerCaseStringValue = SqlString.EscapedSqlString(asString.toLowerCase)
}

object CatalogName extends CIStringValueCompanion[CatalogName] {
}
case class CatalogName(value: CIString) extends SqlIdentifierValue

object ResolvedTableName extends MxResolvedTableName {
  def fromMetadataRow(row: Row): ResolvedTableName =
    new ResolvedTableName(
      row.opt[CatalogName]("TABLE_CAT"),
      row.opt[SchemaName]("TABLE_SCHEM"),
      row.get[TableName]("TABLE_NAME"),
    )
}
@CompanionGen(jdbcMapper = true)
case class ResolvedTableName(catalog: Option[CatalogName], schema: Option[SchemaName], name: TableName) {

  def asLocator = TableLocator(catalog, schema, name)

  def qualifiedName = (catalog.map(_.asString) ++ schema.map(_.asString) ++ Some(name.asString)).mkString(".")
}

case class TypeName(name: String, length: Option[Int] = None, digits: Option[Int] = None) extends HasSqlString {
  override def asSqlFragment = SqlString.typeName(this)
}
