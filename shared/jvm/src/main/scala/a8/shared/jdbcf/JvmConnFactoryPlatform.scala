package a8.shared.jdbcf


import a8.shared.jdbcf.Conn.impl
import com.zaxxer.hikari.HikariDataSource

import javax.sql.DataSource
import sttp.model.Uri
import a8.shared.SharedImports._
import a8.shared.jdbcf.ConnFactoryCompanion.{MapperMaterializer, MapperMaterializerImpl}
import a8.shared.jdbcf.mapper.KeyedTableMapper
import zio.{durationInt=>_, _}

trait JvmConnFactoryPlatform extends ConnFactoryCompanion {

  implicit val managedHikariDataSource: Managed[HikariDataSource] = {
    Managed.impl.create[HikariDataSource](
      _.isClosed,
      _.close(),
    )
  }

  override lazy val constructor: ZIO[DatabaseConfig with Scope,Throwable,ConnFactory] = {
    zservice[DatabaseConfig].flatMap { databaseConfig =>
      def createDs = {

        import databaseConfig._

        val dialect = Dialect(databaseConfig.url)

        val jdbcUrlStr = url.toString()

        val temp = new HikariDataSource()
        temp.setJdbcUrl(jdbcUrlStr)
        temp.setUsername(user)
        temp.setPassword(password.value)
        temp.setIdleTimeout(2.minutes.toMillis)
        temp.setMaxLifetime(1.hour.toMillis)
        temp.setMinimumIdle(minIdle)
        temp.setMaximumPoolSize(maxPoolSize)
        dialect.validationQuery.foreach(q => temp.setConnectionTestQuery(q.toString))
        temp.setAutoCommit(autoCommit)
        temp
      }

      def connR(ds: HikariDataSource): Resource[java.sql.Connection] =
        Managed.resource(ds.getConnection)

      val dialect = Dialect(databaseConfig.url)
      for {
        ds <- Managed.resource(createDs)
        cacheRef <- zio.Ref.make(Map.empty[KeyedTableMapper[_, _], KeyedTableMapper.Materialized[_, _]])
        escaper <- dialect.escaper(connR(ds))
      } yield
        new ConnFactory {
          lazy val mapperCache: MapperMaterializer = new MapperMaterializerImpl(cacheRef, this)
          val config = databaseConfig

          def connR: Resource[Conn] =
            Conn.impl.makeResource(ds.getConnection, mapperCache, databaseConfig.url, dialect, escaper)
        }
    }
  }

}
