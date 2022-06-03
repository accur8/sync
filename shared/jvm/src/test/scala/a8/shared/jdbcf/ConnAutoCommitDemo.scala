package a8.shared.jdbcf

import a8.shared.SharedImports.{sharedImportsIntOps=>_, _}
import a8.shared.jdbcf.SqlString._
import a8.shared.app.{AppLogger, BootstrappedIOApp}
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import sttp.model.Uri._
import zio._

object ConnAutoCommitDemo extends BootstrappedIOApp {

  lazy val config =
    DatabaseConfig(
      DatabaseId("test"),
      uri"jdbc:postgresql://localhost:5432/qubes",
      "",
      "",
    )


  lazy val autoCommitConnFactoryR = ConnFactory.resource(config.copy(autoCommit = true))
  lazy val noAutoCommitConnFactoryR = ConnFactory.resource(config.copy(autoCommit = false))


  override def runT: Task[Unit] = {
    ZIO.scoped {
      for {
        autoCommitConnFactory <- autoCommitConnFactoryR
        noAutoCommitConnFactory <- noAutoCommitConnFactoryR
        _ <- autoCommitConnFactory.connR.use(deleteRows)
        _ <- autoCommitConnFactory.connR.use(doInserts).catchAll(th => loggerF.error(th.getMessage))
        _ <- noAutoCommitConnFactory.connR.use(doInserts).catchAll(th => loggerF.error(th.getMessage))
      } yield ()
    }
  }

  def deleteRows(connIO: Conn): Task[Int] =
    loggerF.info("Delete rows") *>
      connIO.update(q"delete from keyvalue where createdbyuid = 'ConnDemo'")


  def doInserts(connIO: Conn): Task[Unit] = {
    import zio.durationInt
    for {
      isAutoCommit <- connIO.isAutoCommit
      _ <- loggerF.info(s"Inserting with auto-commit = ${isAutoCommit}")
      key1 = s"ConnDemo-1-${isAutoCommit}"
      key2 = s"ConnDemo-2-${isAutoCommit}"
      _ <- connIO.update(q"insert into keyvalue (ky, value, created, createdbyuid) values (${key1.escape}, '', now(), 'ConnDemo')")
      _ <- loggerF.info("Sleeping for 10 seconds")
      _ <- ZIO.sleep(10.seconds)
      _ <- connIO.update(q"insert into keyvalue (ky, value, created, createdbyuid) values (${key2.escape}, null, now(), 'ConnDemo')")
    } yield ()
  }

}
