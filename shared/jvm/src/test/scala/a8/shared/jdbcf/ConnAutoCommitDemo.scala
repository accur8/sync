package a8.shared.jdbcf

import a8.shared.SharedImports._
import a8.shared.jdbcf.SqlString._
import a8.shared.app.{AppLogger, BootstrappedIOApp, IOLogger}
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import cats.effect.{IO, IOApp}
import sttp.model.Uri._

object ConnAutoCommitDemo extends BootstrappedIOApp {

  lazy val config =
    DatabaseConfig(
      DatabaseId("test"),
      uri"jdbc:postgresql://localhost:5432/qubes",
      "",
      "",
    )


  lazy val autoCommitConnFactoryR = ConnFactory.resource[IO](config.copy(autoCommit = true))
  lazy val noAutoCommitConnFactoryR = ConnFactory.resource[IO](config.copy(autoCommit = false))

  override def run: IO[Unit] = {
    autoCommitConnFactoryR.both(noAutoCommitConnFactoryR).use { case (autoCommitConnFactory, noAutoCommitConnFactory) =>
      for {
        _ <- autoCommitConnFactory.connR.use(deleteRows)
        _ <- autoCommitConnFactory.connR.use(doInserts).handleErrorWith(th => loggerIO.error(th.getMessage))
        _ <- noAutoCommitConnFactory.connR.use(doInserts).handleErrorWith(th => loggerIO.error(th.getMessage))
      } yield ()
    }
  }

  def deleteRows(connIO: Conn[IO]): IO[Int] = {
    logger.info("Delete rows")
    connIO.update(q"delete from keyvalue where createdbyuid = 'ConnDemo'")
  }

  def doInserts(connIO: Conn[IO]): IO[Unit] = {
    for {
      isAutoCommit <- connIO.isAutoCommit
      _ <- loggerIO.info(s"Inserting with auto-commit = ${isAutoCommit}")
      key1 = s"ConnDemo-1-${isAutoCommit}"
      key2 = s"ConnDemo-2-${isAutoCommit}"
      _ <- connIO.update(q"insert into keyvalue (ky, value, created, createdbyuid) values (${key1.escape}, '', now(), 'ConnDemo')")
      _ <- loggerIO.info("Sleeping for 10 seconds")
      _ <- IO.sleep(10.seconds)
      _ <- connIO.update(q"insert into keyvalue (ky, value, created, createdbyuid) values (${key2.escape}, null, now(), 'ConnDemo')")
    } yield ()
  }

}
