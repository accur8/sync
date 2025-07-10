package ahs.stager

import a8.shared.app.Ctx
import a8.shared.jdbcf.JdbcMetadata.ResolvedJdbcTable
import a8.shared.jdbcf.{Conn, JdbcMetadata, TableLocator}
import ahs.stager.Demo.DemoConfig
import ahs.stager.model.{ClientId, TableNameResolver, VmDatabaseId}

case class GenerateTableAndIndexDdl(
  config: DemoConfig,
  vmDbId: VmDatabaseId,
  clientId: model.ClientId,
  tables: Iterable[model.Table],
)(using Ctx) {

  given Conn =
    Conn.newConnection(
      url = vmDbId.jdbcUrl,
      user = config.vmDatabaseUser,
      password = config.vmDatabasePassword,
    )

  given TableNameResolver = model.loadTableNameResolver()

  lazy val massiveDdl =
    tables
      .flatMap { table =>
        postgresTableDdl(table)
      }
      .mkString("\n\n;;;\n\n")

  def postgresTableDdl(table: model.Table): List[String] = {
    postgresCreateTableDdl(table) :: postgresCreateIndexesDdl(table)
  }

  def postgresCreateIndexesDdl(table: model.Table): List[String] = {
    table
      .indexes
      .map { index =>
        val tableName = given_TableNameResolver.resolveTableName(table, clientId)
        val indexName = s"${tableName.value}_${index.nameSuffix}"
        val indexDdl =
          index
            .ddl
            .replace("{{tableName}}", tableName.value)
            .replace("{{indexName}}", indexName)
        indexDdl
      }
      .toList
  }

  def postgresCreateTableDdl(table: model.Table): String = {

    val jdbcTable = given_Conn.tableMetadata(TableLocator(schemaName = vmDbId.schemaName, tableName = table.name))

    val resolvedTableName = given_TableNameResolver.resolveTableName(table, clientId)

    val cols =
      jdbcTable
        .columns
        .map { col =>
          col.name.value.toString.toLowerCase + " " + postgresType(col.jdbcColumn)
        }
        .mkString(",")
    s"CREATE TABLE ${resolvedTableName.value} ($cols)"
  }

  def postgresType(col: JdbcMetadata.JdbcColumn): String = {
    col.typeName.toLowerCase match {
      case "char" =>
        s"CHAR(${col.columnSize})"
      case "decimal" | "numeric" =>
        s"NUMERIC(${col.bufferLength.get}, ${col.decimalDigits.get})"
      case "date" =>
        "DATE"
      case "time" =>
        "TIME"
      case "timestamp" =>
        "TIMESTAMP"
      case _ =>
        throw new IllegalArgumentException(s"Unsupported data type: ${col}")
    }
  }
}
