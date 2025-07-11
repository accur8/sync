package ahs.stager

import a8.shared.app.{AppCtx, BootstrappedIOApp, Ctx}
import a8.shared.jdbcf.{Conn, DatabaseConfig, SchemaName}
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import ahs.stager.CopyData.TableHandle
import ahs.stager.CreateTablesDemo.{appConfig, config, logger}
import ahs.stager.model.{StagerConfig, TableNameResolver, VmDatabaseId}

object CopyDataDemo extends StagerApp {

  override def run()(using AppCtx): Unit = {

    val sourceVmDbId = VmDatabaseId("0013")
    val clientId = model.ClientId("PWAY")

    val targetDatabaseId = DatabaseId("primelinkstaging")

    given Services = Services(config)

    copyTables(
      sourceVmDatabaseId = sourceVmDbId,
      targetDatabaseId = targetDatabaseId,
      clientId = clientId,
      tables = AhsData.tables,
    )

  }

  def copyTables(
    sourceVmDatabaseId: VmDatabaseId,
    targetDatabaseId: DatabaseId,
    clientId: model.ClientId,
    tables: Seq[model.Table],
  )(
    using
      Ctx,
      Services,
  ): Unit = {
    tables
      .foreach( table =>
        summon[Ctx].withSubCtx(
          CopyData.runFullDataCopy(
            sourceVmInfo = Some((sourceVmDatabaseId, clientId)),
            source =
              TableHandle(
                databaseId = sourceVmDatabaseId.asDatabaseId,
                schema = sourceVmDatabaseId.schemaName,
                tableName = table.name,
              ),
            target =
              TableHandle(
                databaseId = targetDatabaseId,
                schema = SchemaName("public"),
                tableName = summon[Services].tableNameResolver.resolveTableName(table, clientId),
              )
          )
        )
      )
  }


}
