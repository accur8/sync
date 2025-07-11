package ahs.stager

import a8.shared.CompanionGen
import a8.shared.app.{AppCtx, BootstrappedIOApp}
import a8.shared.jdbcf.DatabaseConfig.Password
import a8.shared.jdbcf.{Conn, ConnFactory, DatabaseConfig, TableName}
import ahs.stager.model.{StagerConfig, TableNameResolver, VmDatabaseId}
import sttp.model.Uri

object Demo extends BootstrappedIOApp {

  override lazy val defaultAppName: String = "ahsstager"

  lazy val config = appConfig[StagerConfig]

  override def run()(using AppCtx): Unit = {

    val vmDbId = VmDatabaseId("0002")
    val clientId = model.ClientId("BASA")

    given conn: Conn =
      Conn.newConnection(
        url = vmDbId.jdbcUrl,
//        url = Uri.unsafeParse("jdbc:as400://vmd0002;naming=system;libraries=VMD0002;errors=full"),
        user = config.vmDatabaseUser,
        password = config.vmDatabasePassword,
      )

    logger.debug("we have a db connection")

    val tableNameResolver = model.loadTableNameResolver()

    val blpcar =
      model.Table(
        name = TableName("BLPCAR"),
        syncType = model.SyncType.Full,
        correlationColumns = Seq("BACAR"),
      )

    val resolvedTableName =
      tableNameResolver
        .resolveTableName(blpcar, model.ClientId("BASA"))

    logger.info("resolved table name: " + resolvedTableName)

  }

}
