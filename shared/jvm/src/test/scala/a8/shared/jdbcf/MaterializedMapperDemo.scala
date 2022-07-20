package a8.shared.jdbcf


import a8.shared.CompanionGen
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import sttp.model.Uri
import a8.shared.SharedImports._
import SqlString._
import a8.shared.jdbcf.MxMaterializedMapperDemo._
import a8.shared.jdbcf.mapper.{PK, SqlTable}
import zio._

object MaterializedMapperDemo extends ZIOAppDefault {

  lazy val databaseConfig =
    DatabaseConfig(
      id = DatabaseId("demo"),
      url = unsafeParseUri("jdbc:hsqldb:mem:demo"),
      user = "SA",
      password = DatabaseConfig.Password(""),
    )

  object JsonCC extends MxJsonCC {
    implicit val jsonCodecMapper: JsonCodecMapper[JsonCC] = JsonCodecMapper[JsonCC]
  }
  @CompanionGen
  case class JsonCC(foo: Int, bar: String)

  object BigBoo extends MxBigBoo
  @CompanionGen(jdbcMapper = true)
  @SqlTable(name="BIGBOO")
  case class BigBoo(
    @PK grOup: Int,
    name: String,
  )


  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    ConnFactory.resource(databaseConfig).use { connFactory =>
      for {
        _ <-
          connFactory.connR.use(conn =>
            conn.update(sql"""create table BIGBOO ("GrouP" int, name varchar(255))""")
          )
        _ <- runit(connFactory)
      } yield ()
    }

  def runit(connFactory: ConnFactory): Task[Unit] = {
    connFactory.connR.use { implicit conn =>
      conn
        .selectRows[BigBoo](sql"1 = 0")
        .as(())
    }
  }

}
