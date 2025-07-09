package a8.shared.jdbcf


import a8.shared.SharedImports.*
import a8.shared.app.Ctx
import a8.shared.jdbcf.mapper.{KeyedTableMapper, TableMapper}
import zio.*

import scala.collection.concurrent.TrieMap

object ConnFactoryCompanion {

  object MapperMaterializer {
    object noop extends MapperMaterializer {
      override def materialize[A, B](ktm: KeyedTableMapper[A, B]): KeyedTableMapper.Materialized[A, B] =
        KeyedTableMapper.Materialized(ktm)
      override def materialize[A](tm: TableMapper[A]): TableMapper[A] =
        tm
    }
  }

  abstract class MapperMaterializer {
    def materialize[A,B](ktm: KeyedTableMapper[A,B]): KeyedTableMapper.Materialized[A,B]
    def materialize[A](tm: TableMapper[A]): TableMapper[A]
  }

  class MapperMaterializerImpl(
    connFactory: ConnFactory,
  )(
    using ctx: Ctx
  ) extends MapperMaterializer {

    lazy val cache = TrieMap.empty[KeyedTableMapper[?,?],KeyedTableMapper.Materialized[?,?]]

    def createMaterializedMapper[A,B](ktm: KeyedTableMapper[A,B]): KeyedTableMapper.Materialized[A,B] =
      ctx.withSubCtx {
        given Conn = connFactory.connR.unwrap
        ktm.materializeKeyedTableMapper
      }

    def fetchOrCreate[A,B](ktm: KeyedTableMapper[A,B]): KeyedTableMapper.Materialized[A,B] =
      cache
        .getOrElseUpdate(ktm, createMaterializedMapper(ktm))
        .asInstanceOf[KeyedTableMapper.Materialized[A,B]]

    override def materialize[A, B](ktm: KeyedTableMapper[A, B]): KeyedTableMapper.Materialized[A, B] =
      fetchOrCreate(ktm)

    override def materialize[A](tm: TableMapper[A]): TableMapper[A] = {
      tm match {
        case ktm: KeyedTableMapper[A,_] =>
          fetchOrCreate(ktm).value
      }
    }

  }

}


trait ConnFactoryCompanion {

//  lazy val layer: ZLayer[DatabaseConfig & Scope, Throwable, ConnFactory] =
//    ZLayer(constructor)

  def constructor(databaseConfig: DatabaseConfig)(using Ctx): ConnFactory

  def resource(databaseConfig: DatabaseConfig): Resource[ConnFactory] = {
    Resource
      .acquireRelease(
        constructor(databaseConfig)
      )(
        _.safeClose()
      )
  }

}
