package a8.shared.jdbcf

import a8.shared.SharedImports.{sharedImportsIntOps => _, _}
import a8.shared.app.BootstrappedIOApp
import a8.shared.jdbcf.MaterializedMapperDemo.BigBoo
import a8.shared.jdbcf.SqlString._
import zio.{Task, ZIO}

object UpdateRowWhereDemo extends BootstrappedIOApp {

  import MaterializedMapperDemo.databaseConfig

  lazy val connFactoryR = ConnFactory.resource(databaseConfig)

  override def runT: Task[Unit] =
    ZIO.scoped {
      for {
        connFactory <- connFactoryR
          _ <-
            connFactory.connR.use(conn =>
              conn.update(sql"""create table BIGBOO ("grOup" int, "name" varchar(255))""")
            )
          _ <- runit(connFactory)
      } yield ()
    }

  def runit(connFactory: ConnFactory): Task[Unit] = {
    connFactory.connR.use { implicit conn =>
      BigBoo
        .queryDsl
        .updateRow(BigBoo(1, "a"))(aa => aa.name === "123")
        .as(())
    }
  }

}
