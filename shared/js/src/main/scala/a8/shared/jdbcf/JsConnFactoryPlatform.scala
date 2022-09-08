package a8.shared.jdbcf


import zio._

trait JsConnFactoryPlatform extends ConnFactoryCompanion {

  override val constructor: ZIO[DatabaseConfig with Scope, Throwable, ConnFactory] = ???

}
