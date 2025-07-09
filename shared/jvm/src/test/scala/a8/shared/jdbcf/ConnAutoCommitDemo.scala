package a8.shared.jdbcf

import a8.shared.SharedImports.{sharedImportsIntOps as _, *}
import a8.shared.jdbcf.SqlString.*
import a8.shared.app.{AppCtx, BootstrappedIOApp}
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import sttp.model.Uri.*
import zio.*

object ConnAutoCommitDemo extends BootstrappedIOApp {

  lazy val config: DatabaseConfig =
    DatabaseConfig(
      DatabaseId("test"),
      uri"jdbc:postgresql://localhost:5432/qubes",
      "",
      DatabaseConfig.Password(""),
    )


  lazy val autoCommitConnFactoryR: Resource[ConnFactory] = ConnFactory.resource(config.copy(autoCommit = true))
  lazy val noAutoCommitConnFactoryR: Resource[ConnFactory] = ConnFactory.resource(config.copy(autoCommit = false))


  override def run()(using appCtx: AppCtx): Unit = {
    val autoCommitConnFactory = autoCommitConnFactoryR.unwrap
    val noAutoCommitConnFactory = noAutoCommitConnFactoryR.unwrap

    val autoConn = autoCommitConnFactory.connR.unwrap
    deleteRows(autoConn)

    val noAutoConn = noAutoCommitConnFactory.connR.unwrap
    deleteRows(noAutoConn)

    doInserts(autoConn)
    doInserts(noAutoConn)

  }

  def deleteRows(connIO: Conn): Int = {
    logger.info("Delete rows")
    connIO.update(q"delete from keyvalue where createdbyuid = 'ConnDemo'")
  }


  def doInserts(connIO: Conn): Unit = {
    val isAutoCommit = connIO.isAutoCommit
    logger.info(s"Inserting with auto-commit = ${isAutoCommit}")
    val key1 = s"ConnDemo-1-${isAutoCommit}"
    val key2 = s"ConnDemo-2-${isAutoCommit}"
    connIO.update(q"insert into keyvalue (ky, value, created, createdbyuid) values (${key1.escape}, '', now(), 'ConnDemo')")
    logger.info("Sleeping for 10 seconds")
    Thread.sleep(10_000)
    connIO.update(q"insert into keyvalue (ky, value, created, createdbyuid) values (${key2.escape}, null, now(), 'ConnDemo')")
  }

}
