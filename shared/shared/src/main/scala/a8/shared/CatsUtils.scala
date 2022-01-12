package a8.shared


import SharedImports._

object CatsUtils {

  object Memo {
    def create[F[_] : Concurrent, A](fa: F[A]): F[Memo[F, A]] =
      fa.memoize.map { delegate =>
        new Memo[F, A] {
          override def get: F[A] = delegate
        }
      }
  }


  trait Memo[F[_], A] {
    def get: F[A]
  }

  /**
   * no rentrant lock in cats effect 3 so this gives us the ability to acuire a lock
   * and pass around (usually via implicits) the object the lock granted us as a way to sort of kind of do reentrant lock kinds of things
   */
  trait FLock[F[_], A] {
    def greenLight[B](fn: A => F[B]): F[B]

    def greenLight0[B](fn: => F[B]): F[B] = greenLight(_ => fn)
  }

  object FLock {
    def create[F[_] : Concurrent, A](value: A): F[FLock[F, A]] =
      Semaphore(1).map { sem =>
        new FLock[F, A] {
          def greenLight[B](fn: A => F[B]): F[B] =
            sem.permit.surround(fn(value))
        }
      }
  }


  object CacheBuilder {
    def apply[F[_] : Async, A, B]: F[CacheBuilder[F, A, B]] =
      for {
        lock <- FLock.create[F, Unit](())
        ref <- Ref.of[F, Map[A, java.lang.ref.WeakReference[B]]](Map.empty)
      } yield CacheBuilder(lock, ref)
  }

  case class CacheBuilder[F[_] : Async, A, B](
    lock: FLock[F, Unit],
    cacheRef: Ref[F, Map[A, java.lang.ref.WeakReference[B]]],
  ) {
    def withGenerator(generatorFn: A => F[B]): Cache[F, A, B] =
      Cache(this, generatorFn)
  }

  case class Cache[F[_] : Async, A, B](
    builder: CacheBuilder[F, A, B],
    generatorFn: A => F[B],
  ) {

    import builder._

    object impl {
      val F = Async[F]
    }

    import impl._

    def invalidate(a: A): F[Unit] = {
      lock.greenLight0(
        cacheRef.update(_.removed(a))
      )
    }

    def get(a: A): F[B] =
      lock.greenLight0 {
        cacheRef
          .get
          .flatMap { cache =>
            cache.get(a).flatMap(wr => Option(wr.get)) match {
              case Some(b) =>
                F.pure(b)
              case None =>
                for {
                  b <- generatorFn(a)
                  cache <- cacheRef.get
                  newCache = cache.updated(a, new java.lang.ref.WeakReference[B](b))
                  _ <- cacheRef.set(newCache)
                } yield b
            }
          }
      }


  }

}
