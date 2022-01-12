package a8.shared.jdbcf

import a8.shared.SharedImports.{Async, Resource}

trait JsConnFactoryPlatform extends ConnFactoryImpl {
  override def resource[F[_] : Async](databaseConfig: DatabaseConfig): Resource[F, ConnFactory[F]] = ???
}
