package a8.shared.jdbcf


import a8.shared.CompanionGen
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import sttp.model.Uri
import a8.shared.SharedImports.*
import SqlString.*
import a8.shared.app.{AppCtx, BootstrappedIOApp, Ctx}
import a8.shared.jdbcf.MxMaterializedMapperDemo.*
import a8.shared.jdbcf.mapper.{PK, SqlTable}
import zio.*

object MaterializedMapperDemo extends BootstrappedIOApp {

  lazy val databaseConfig: DatabaseConfig =
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


  override def run()(using AppCtx): Unit = {
    val connFactory = ConnFactory.resource(databaseConfig).unwrap
    val conn = connFactory.connR.unwrap
    conn.update(sql"""create table BIGBOO ("GrouP" int, name varchar(255))""")
    runit(connFactory)
  }

  def runit(connFactory: ConnFactory)(using Ctx): Unit = {
    val conn = connFactory.connR.unwrap
    conn.selectRows[BigBoo](sql"1 = 0")
  }

}
