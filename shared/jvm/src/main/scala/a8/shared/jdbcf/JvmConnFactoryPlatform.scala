package a8.shared.jdbcf


import a8.shared.jdbcf.Conn.impl
import com.zaxxer.hikari.HikariDataSource

import javax.sql.DataSource
import sttp.model.Uri
import a8.shared.SharedImports._
import a8.shared.jdbcf.ConnFactoryImpl.{MapperMaterializer, MapperMaterializerImpl}
import a8.shared.jdbcf.mapper.KeyedTableMapper

trait JvmConnFactoryPlatform extends ConnFactoryImpl {

  implicit val managedHikariDataSource: Managed[HikariDataSource] = {
    Managed.impl.create[HikariDataSource](
      _.isClosed,
      _.close(),
    )
  }

  override def resource[F[_] : Async](databaseConfig: DatabaseConfig): Resource[F, ConnFactory[F]] = {

    def createDs = {

      import databaseConfig._

      val dialect = Dialect(databaseConfig.url)

      val jdbcUrlStr = url.toString()

      val temp = new HikariDataSource()
      temp.setJdbcUrl(jdbcUrlStr)
      temp.setUsername(user)
      temp.setPassword(password)
      temp.setIdleTimeout(2.minutes.toMillis)
      temp.setMaxLifetime(1.hour.toMillis)
      temp.setMinimumIdle(minIdle)
      temp.setMaximumPoolSize(maxPoolSize)
      dialect.validationQuery.foreach(q => temp.setConnectionTestQuery(q.toString))
      temp.setAutoCommit(autoCommit)
      temp
    }

    def connR(ds: HikariDataSource): Resource[F,java.sql.Connection] =
      Managed.resource(ds.getConnection)

    val dialect = Dialect(databaseConfig.url)
    for {
      ds <- Managed.resource(createDs)
      cacheRef <- Resource.eval(Async[F].ref(Map.empty[KeyedTableMapper[_,_],KeyedTableMapper[_,_]]))
      escaper <- dialect.escaper[F](connR(ds))
    } yield
      new ConnFactory[F] {
        lazy val mapperCache: MapperMaterializer[F] = new MapperMaterializerImpl[F](cacheRef, this)
        val config = databaseConfig
        def connR: Resource[F,Conn[F]] =
          Conn.impl.makeResource[F](ds.getConnection, mapperCache, databaseConfig.url, dialect, escaper)
      }

  }

}
