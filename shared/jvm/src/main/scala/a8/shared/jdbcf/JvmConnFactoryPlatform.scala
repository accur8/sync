package a8.shared.jdbcf


import a8.shared.jdbcf.Conn.impl
import com.zaxxer.hikari.HikariDataSource

import javax.sql.DataSource
import sttp.model.Uri
import a8.shared.SharedImports._

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

      val temp = new HikariDataSource()
      temp.setJdbcUrl(url.toString())
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

    Managed
      .resource(createDs)
      .map(ds =>
        new ConnFactory[F] {
          val config = databaseConfig
          def connR: Resource[F,Conn[F]] =
            Conn.impl.makeResource[F](ds.getConnection)
        }
      )

  }

}
