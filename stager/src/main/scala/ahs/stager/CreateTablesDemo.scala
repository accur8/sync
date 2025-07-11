package ahs.stager

import a8.shared.app.{AppCtx, BootstrappedIOApp}
import a8.shared.jdbcf.JdbcMetadata.ResolvedJdbcTable
import a8.shared.jdbcf.{Conn, JdbcMetadata, SchemaName, TableLocator, TableName}
import ahs.stager.Demo.appConfig
import ahs.stager.model.{ClientId, StagerConfig, TableNameResolver, VmDatabaseId}

object CreateTablesDemo extends StagerApp {

  override def run()(using AppCtx): Unit = {

    val vmDbId = VmDatabaseId("0013")
    val clientId = model.ClientId("PWAY")

    given Services = Services(config)

    val gtni = GenerateTableAndIndexDdl(config, vmDbId, clientId, AhsData.tables, includeDropTables = true)

    logger.info("Massive DDL:\n\n\n" + gtni.allDdl.map(_.sql).mkString("\n\n;;;\n\n"))

  }

}
