package a8.sync

import a8.shared.CompanionGen
import a8.shared.SharedImports._
import a8.shared.app.{AppLogger, BootstrappedIOApp}
import a8.shared.jdbcf.mapper.PK
import a8.sync.MxQubesApiClientDemo.MxUserGroup
import a8.sync.qubes.{QubesAnno, QubesApiClient}
import a8.shared.jdbcf.SqlString._
import a8.shared.json.ast._
import zio._

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
  lazy val qubesApiClientR = QubesApiClient.asResource(config)


  override def runT: Task[Unit] = {
    val result =
      for {
        _ <- loggerF.info("Starting application")
        _ <- loggerF.info(s"Loaded config:\n${config.prettyJson}")
        _ <- process()
      } yield ()
    result
      .catchAll(th => ZIO.attemptBlocking(logger.error("Unhandled exception", th)))
      .flatMap(_ => loggerF.info("Run complete"))
  }

  def process(): Task[Unit] =

    qubesApiClientR.use ( qubesApiClient =>
      loggerF.info("starting step 1") *>
        qubesApiClient.lowlevel.query(QubesApiClient.QueryRequest("from qubes_admin/usergroup select uid, name"))
          .flatMap { dj =>
            loggerF.info(dj.prettyJson)
          } *>
        loggerF.info("starting step 2") *>
        qubesApiClient
          .query[UserGroup](q"1 = 1")
          .flatMap { groups =>
            loggerF.info(groups.prettyJson)
          } *>
        loggerF.info("starting step 3 - do an update") *>
        qubesApiClient
          .query[UserGroup](q"1 = 1")
          .flatMap { groups =>
//            loggerIO.info(groups.toPrettyJsonStr)
            qubesApiClient.update(groups.head)
          } *>
        ZIO.unit
    )

}
