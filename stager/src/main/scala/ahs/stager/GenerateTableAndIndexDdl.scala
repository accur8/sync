package ahs.stager

import a8.shared.app.Ctx
import a8.shared.jdbcf.JdbcMetadata.ResolvedJdbcTable
import a8.shared.jdbcf.{Conn, JdbcMetadata, ResolvedTableName, TableLocator, TableName}
import ahs.stager.GenerateTableAndIndexDdl.{Ddl, DdlKind, postgresType}
import ahs.stager.model.{ClientId, StagerConfig, TableNameResolver, VmDatabaseId}
import model.*

object GenerateTableAndIndexDdl {

  enum DdlKind {
    case CreateTable
    case CreateIndex
    case DropTable
  }

  case class Ddl(
    kind: DdlKind,
    tableName: TableName,
    sql: String,
  )

  def postgresType(col: JdbcMetadata.JdbcColumn): String = {
    if ( col.columnName.value.toString.toLowerCase == "ocuid" ) {
      "char(32)"
    } else {
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

}

case class GenerateTableAndIndexDdl(
  config: StagerConfig,
  vmDbId: VmDatabaseId,
  clientId: model.ClientId,
  tables: Iterable[model.Table],
  includeDropTables: Boolean = false,
)(
  using
    ctx: Ctx,
    services: Services,
) {

  given Conn = summon[Services].connectionManager.conn(vmDbId.asDatabaseId)

  lazy val allDdl: Iterable[Ddl] =
    tables
      .flatMap { table =>
        postgresTableDdl(table)
      }

  def postgresTableDdl(table: model.Table): Iterable[Ddl] = {
    postgresCreateTableDdl(table) ++ postgresCreateIndexesDdl(table)
  }

  def postgresCreateIndexesDdl(table: model.Table): Iterable[Ddl] = {
    table
      .indexes
      .map { index =>
        val tableName = services.tableNameResolver.resolveTableName(table, clientId).postgresTable
        val indexName = s"${tableName.value}_${index.nameSuffix}"
        val indexDdl =
          index
            .ddl
            .replace("{{tableName}}", tableName.value.toString)
            .replace("{{indexName}}", indexName)
        Ddl(
          kind = DdlKind.CreateIndex,
          tableName = tableName,
          sql = indexDdl,
        )
      }
      .toList
  }

  def postgresCreateTableDdl(table: model.Table): Iterable[Ddl] = {

    val jdbcTable = given_Conn.tableMetadata(TableLocator(schemaName = vmDbId.schemaName, tableName = table.name))

    val resolvedTableName = services.tableNameResolver.resolveTableName(table, clientId).postgresTable

    val cols =
      jdbcTable
        .columns
        .map { col =>
          col.name.transformForPostgres.value.toString + " " + postgresType(col.jdbcColumn)
        }
        .mkString(",")

    val drop =
      Some(Ddl(
        kind = DdlKind.DropTable,
        tableName = resolvedTableName,
        sql = "DROP TABLE IF EXISTS " + resolvedTableName.value
      ))
        .filter(_ => includeDropTables)

    val create =
      Some(Ddl(
        kind = DdlKind.CreateTable,
        tableName = resolvedTableName,
        sql = s"CREATE TABLE ${resolvedTableName.value} ($cols)",
      ))

    drop ++ create

  }

}
