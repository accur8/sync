package ahs.stager

import a8.shared.app.Ctx
import a8.shared.jdbcf.{Conn, ConnFactory, DatabaseConfig}
import a8.shared.jdbcf.DatabaseConfig.DatabaseId
import ahs.stager.CopyDataDemo.config
import ahs.stager.model.{TableNameResolver, VmDatabaseId}

import scala.collection.concurrent.TrieMap

object Services {
}

case class Services(
  config: model.StagerConfig,
  vmDatabaseId: VmDatabaseId,
)(using Ctx) {

  lazy val connectionManager: ConnectionManager =
    ConnectionManager(fetchDatabaseConfig)

  lazy val tableNameResolver: TableNameResolver =
    summon[Ctx].withSubCtx(
      model.loadTableNameResolver(vmDatabaseId)(using summon[Ctx], connectionManager)
    )

  def fetchDatabaseConfig(id: DatabaseId): Option[DatabaseConfig] = {
    if ( id.value.toString.startsWith("00") ) {
      val vmDatabaseId = VmDatabaseId(id.value.toString)
      Some(
        DatabaseConfig(
          id = id,
          url = vmDatabaseId.jdbcUrl,
          user = config.vmDatabaseUser,
          password = config.vmDatabasePassword,
        )
      )
    } else if ( id == config.postgresStagingDb.id ) {
      Some(config.postgresStagingDb)
    } else {
      None
    }
  }

}

object ConnectionManager {

  def apply(configFn: DatabaseId => Option[DatabaseConfig])(using Ctx): ConnectionManager =
    new ConnectionManager {

      val factoriesById = TrieMap.empty[DatabaseId, ConnFactory]

      def createConnFactory(id: DatabaseId): ConnFactory = {
        configFn(id) match {
          case Some(config) =>
            ConnFactory.constructor(config)
          case None =>
            throw new IllegalArgumentException(s"No configuration found for database id: $id")
        }
      }

      override def conn(id: DatabaseId)(using Ctx): Conn = {
        val connFactory =
          factoriesById
            .getOrElseUpdate(
              id,
              createConnFactory(id)
            )
        connFactory.connR.unwrap
      }
    }

}

trait ConnectionManager {
  def conn(id: DatabaseId)(using Ctx): Conn
}
