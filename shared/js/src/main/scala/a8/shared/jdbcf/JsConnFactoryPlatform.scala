package a8.shared.jdbcf


import zio._

trait JsConnFactoryPlatform extends ConnFactoryImpl {
  override def resource(databaseConfig: DatabaseConfig): ZIO[Scope, Throwable, ConnFactory] = ???
}
