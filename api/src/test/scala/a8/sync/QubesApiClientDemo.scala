package a8.sync

import a8.shared.CompanionGen
import a8.shared.SharedImports._
import a8.shared.app.{AppLogger, BootstrappedIOApp}
import a8.shared.jdbcf.mapper.PK
import a8.sync.MxQubesApiClientDemo.MxUserGroup
import a8.sync.qubes.{QubesAnno, QubesApiClient}
import cats.effect.{IO, IOApp}
import a8.shared.jdbcf.SqlString._
import a8.shared.json.ast._

object QubesApiClientDemo extends BootstrappedIOApp {

  object UserGroup extends MxUserGroup {
  }
  @CompanionGen(qubesMapper = true, jsonCodec = true)
  @QubesAnno(appSpace="qubes_admin")
  case class UserGroup(
    @PK uid: String,
    name: String,
  )


  lazy val config = Utils.config.load[QubesApiClient.Config]("QubesApiClientDemo.json")
  lazy val qubesApiClientR = QubesApiClient.asResource[IO](config)

  override def run: IO[Unit] = {
    val result =
      for {
        _ <- IO.blocking(logger.info("Starting application"))
        _ <- IO.blocking(logger.info(s"Loaded config:\n${config.prettyJson}"))
        _ <- process()
      } yield ()
    result
      .handleErrorWith(th => IO.blocking(logger.error("Unhandled exception", th)))
      .map(_ => logger.info("Run complete"))
  }

  def process(): IO[Unit] =

    qubesApiClientR.use ( qubesApiClient =>
      loggerIO.info("starting step 1") >>
        qubesApiClient.lowlevel.query(QubesApiClient.QueryRequest("from qubes_admin/usergroup select uid, name"))
          .flatMap { dj =>
            loggerIO.info(dj.prettyJson)
          } >>
        loggerIO.info("starting step 2") >>
        qubesApiClient
          .query[UserGroup](q"1 = 1")
          .flatMap { groups =>
            loggerIO.info(groups.prettyJson)
          } >>
        loggerIO.info("starting step 3 - do an update") >>
        qubesApiClient
          .query[UserGroup](q"1 = 1")
          .flatMap { groups =>
//            loggerIO.info(groups.toPrettyJsonStr)
            qubesApiClient.update(groups.head)
          } >>
        IO.unit
    )

}
