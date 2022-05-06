package a8.shared.jdbcf

import a8.shared.SharedImports._
import a8.shared.jdbcf.mapper.{KeyedTableMapper, TableMapper}

object ConnFactoryImpl {

  abstract class MapperMaterializer[F[_]: Async] {
    def materialize[A,B](ktm: KeyedTableMapper[A,B]): F[KeyedTableMapper[A,B]]
    def materialize[A](tm: TableMapper[A]): F[TableMapper[A]]
  }

  class MapperMaterializerImpl[F[_]: Async](
    cacheRef: Ref[F,Map[KeyedTableMapper[_,_],KeyedTableMapper[_,_]]],
    connFactory: ConnFactory[F],
  ) extends MapperMaterializer[F] {

    def materializeImpl[A,B](ktm: KeyedTableMapper[A,B]): F[KeyedTableMapper[A,B]] = {
      connFactory
        .connR
        .use(implicit conn =>
          ktm.materializeKeyedTableMapper
        )
    }

    def create[A,B](ktm: KeyedTableMapper[A,B]): F[KeyedTableMapper[A,B]] =
      for {
        cache <- cacheRef.get
        materialized <- materializeImpl(ktm)
        _ <- cacheRef.update(_ + (ktm -> materialized))
      } yield materialized

    def fetchOrCreate[A,B](ktm: KeyedTableMapper[A,B]): F[KeyedTableMapper[A,B]] = {
      for {
        cache <- cacheRef.get
        materialized <- {
          cache.get(ktm) match {
            case None =>
              create(ktm)
            case Some(m) =>
              Async[F].pure(m.asInstanceOf[KeyedTableMapper[A,B]])
          }
        }
      } yield materialized
    }

    override def materialize[A, B](ktm: KeyedTableMapper[A, B]): F[KeyedTableMapper[A, B]] =
      fetchOrCreate(ktm)

    override def materialize[A](tm: TableMapper[A]): F[TableMapper[A]] = {
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


trait ConnFactoryImpl {
  def resource[F[_] : Async](databaseConfig: DatabaseConfig): Resource[F, ConnFactory[F]]
}
