package a8.shared.jdbcf

import a8.shared.SharedImports.{sharedImportsIntOps as _, *}
import a8.shared.app.{AppCtx, BootstrappedIOApp, Ctx}
import a8.shared.jdbcf.MaterializedMapperDemo.BigBoo
import a8.shared.jdbcf.SqlString.*

object UpdateRowWhereDemo extends BootstrappedIOApp {

  import MaterializedMapperDemo.databaseConfig

  lazy val connFactoryR: zio.Resource[ConnFactory] = ConnFactory.resource(databaseConfig)

  override def run()(using AppCtx): Unit = {
    val connFactory = connFactoryR.unwrap
    val conn = connFactory.connR.unwrap
    conn.update(sql"""create table BIGBOO ("grOup" int, "name" varchar(255))""")
    runit(connFactory)
  }

  def runit(connFactory: ConnFactory)(using Ctx): Unit = {
    given Conn = connFactory.connR.unwrap
    BigBoo
      .queryDsl
      .updateRow(BigBoo(1, "a"))(aa => aa.name === "123")

  }

}
