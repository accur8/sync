package a8.shared.jdbcf

import a8.shared.CompanionGen
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import a8.shared.jdbcf.MaterializedMapperDemo.BigBoo
import a8.shared.jdbcf.MxMaterializedMapperDemo._
import a8.shared.jdbcf.SqlString._
import a8.shared.jdbcf.mapper.{PK, SqlTable}
import cats.effect.{ExitCode, IO, IOApp}

object UpdateRowWhereDemo extends IOApp {

  import MaterializedMapperDemo.databaseConfig

  override def run(args: List[String]): IO[ExitCode] =
    ConnFactory.resource[IO](databaseConfig).use { connFactory =>
      for {
        _ <-
          connFactory.connR.use(conn =>
            conn.update(sql"""create table BIGBOO ("grOup" int, "name" varchar(255))""")
          )
        _ <- runit(connFactory)
      } yield ExitCode(0)
    }

  def runit(connFactory: ConnFactory[IO]): IO[Unit] = {
    connFactory.connR.use { implicit conn =>
      BigBoo
        .queryDsl
        .updateRow[IO](BigBoo(1, "a"))(aa => aa.name === "123")
        .as(())
    }
  }

}
