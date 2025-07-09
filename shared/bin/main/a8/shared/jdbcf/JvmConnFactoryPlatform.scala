package a8.shared.jdbcf


import a8.shared.jdbcf.Conn.impl
import com.zaxxer.hikari.HikariDataSource

import javax.sql.DataSource
import sttp.model.Uri
import a8.shared.SharedImports.*
import a8.shared.app.Ctx
import a8.shared.jdbcf.ConnFactoryCompanion.{MapperMaterializer, MapperMaterializerImpl}
import a8.shared.jdbcf.mapper.KeyedTableMapper
import a8.shared.zreplace.Resource

trait JvmConnFactoryPlatform extends ConnFactoryCompanion {

  def constructor(databaseConfig: DatabaseConfig)(using Ctx): ConnFactory = {
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
      maxLifeTimeInMillis.foreach(temp.setMaxLifetime)
      dialect.validationQuery.foreach(q => temp.setConnectionTestQuery(q.toString))
      temp.setAutoCommit(autoCommit)
      temp
    }

    def connR(ds: HikariDataSource): Resource[java.sql.Connection] =
      Resource
        .acquireRelease(ds.getConnection)(_.close())

    val ds = createDs
    val dialect = Dialect(databaseConfig.url)
    val escaper = {
      val conn = ds.getConnection
      try {
        dialect.buildEscaper(conn)
      } finally {
        conn.close()
      }
    }
    new ConnFactory {
      lazy val mapperCache: MapperMaterializer = new MapperMaterializerImpl(this)
      val config = databaseConfig
      def connR: Resource[Conn] =
        Conn.impl.makeResource(ds.getConnection, mapperCache, databaseConfig.url, dialect, escaper)
      override def safeClose(): Unit =
        tryLogDebug("")(ds.close())
    }
  }

}
