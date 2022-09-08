package a8.shared.jdbcf


import a8.shared.SharedImports._
import a8.shared.jdbcf.mapper.{KeyedTableMapper, TableMapper}
import zio._

object ConnFactoryCompanion {

  object MapperMaterializer {
    object noop extends MapperMaterializer {
      override def materialize[A, B](ktm: KeyedTableMapper[A, B]): Task[KeyedTableMapper[A, B]] =
        ZIO.succeed(ktm)
      override def materialize[A](tm: TableMapper[A]): Task[TableMapper[A]] =
        ZIO.succeed(tm)
    }
  }

  abstract class MapperMaterializer {
    def materialize[A,B](ktm: KeyedTableMapper[A,B]): Task[KeyedTableMapper[A,B]]
    def materialize[A](tm: TableMapper[A]): Task[TableMapper[A]]
  }

  class MapperMaterializerImpl(
    cacheRef: Ref[Map[KeyedTableMapper[_,_],KeyedTableMapper[_,_]]],
    connFactory: ConnFactory,
  ) extends MapperMaterializer {

    def materializeImpl[A,B](ktm: KeyedTableMapper[A,B]): Task[KeyedTableMapper[A,B]] =
      ZIO
        .scoped {
          connFactory
            .connR
            .flatMap(implicit conn =>
              ktm.materializeKeyedTableMapper
            )
        }

    def create[A,B](ktm: KeyedTableMapper[A,B]): Task[KeyedTableMapper[A,B]] =
      for {
        cache <- cacheRef.get
        materialized <- materializeImpl(ktm)
        _ <- cacheRef.update(_ + (ktm -> materialized))
      } yield materialized

    def fetchOrCreate[A,B](ktm: KeyedTableMapper[A,B]): Task[KeyedTableMapper[A,B]] = {
      for {
        cache <- cacheRef.get
        materialized <- {
          cache.get(ktm) match {
            case None =>
              create(ktm)
            case Some(m) =>
              ZIO.succeed(m.asInstanceOf[KeyedTableMapper[A,B]])
          }
        }
      } yield materialized
    }

    override def materialize[A, B](ktm: KeyedTableMapper[A, B]): Task[KeyedTableMapper[A, B]] =
      fetchOrCreate(ktm)

    override def materialize[A](tm: TableMapper[A]): Task[TableMapper[A]] = {
      tm match {
        case ktm: KeyedTableMapper[A,_] =>
          fetchOrCreate(ktm)
            .map {
              case mtm: TableMapper[A] =>
                mtm
            }
      }
    }

  }

}


trait ConnFactoryCompanion {

  lazy val layer: ZLayer[DatabaseConfig with Scope, Throwable, ConnFactory] =
    ZLayer(constructor)

  val constructor: ZIO[DatabaseConfig with Scope,Throwable,ConnFactory]

  def resource(databaseConfig: DatabaseConfig): Resource[ConnFactory] =
    constructor.provideSome(ZLayer.succeed(databaseConfig))

}
