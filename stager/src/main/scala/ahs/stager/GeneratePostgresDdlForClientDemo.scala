package ahs.stager

import a8.shared.app.{AppCtx, BootstrappedIOApp}
import a8.shared.jdbcf.JdbcMetadata.ResolvedJdbcTable
import a8.shared.jdbcf.{Conn, JdbcMetadata, SchemaName, TableLocator, TableName}
import ahs.stager.Demo.{DemoConfig, appConfig}
import ahs.stager.model.{ClientId, TableNameResolver, VmDatabaseId}

object GeneratePostgresDdlForClientDemo extends BootstrappedIOApp {

  lazy val config = appConfig[DemoConfig]

  override def run()(using AppCtx): Unit = {

    val vmDbId = VmDatabaseId("0013")
    val clientId = model.ClientId("PWAY")

    given Conn =
      Conn.newConnection(
        url = vmDbId.jdbcUrl,
        user = config.vmDatabaseUser,
        password = config.vmDatabasePassword,
      )

    given TableNameResolver = model.loadTableNameResolver()

    val gtni = GenerateTableAndIndexDdl(config, vmDbId, clientId, AhsData.tables)

    logger.info("Massive DDL:\n\n\n" + gtni.massiveDdl)

  }

}
