package a8.sync


import java.util.concurrent.TimeUnit

import cats._
import cats.implicits._

import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds
import zio._

object Cache {
//
//  def apply[F[_] : FlatMap : Monad : Clock : Make, A](generator: F[A]): CacheBuilder[F,A] =
//    impl.CacheBuilderImpl(generator)
//
//  /**
//   * Memoizes generator then after the regenerateAfter it will regenerate a new one on the first request after the regenerateAfter timeout (i.e. passively regenerate).
//   * Any errors that happen in delegate bubble up.  So if recovery or other things are wanted
//   * bake that into the delegate passed in.
//   *
//   * We assume delegate is idempotent
//   *
//   */
//  def raw[F[_] : FlatMap : Monad : Clock, A](generator: F[A], regenerateAfter: FiniteDuration)(implicit mk: Make[F]): F[F[A]] = {
//    apply(generator)
//      .regenerateAfter(regenerateAfter)
//      .compile
//      .map(_.get)
//  }
//
//  object impl {
//
//    /**
//     *  A separate method for using a provided ref to manage the memoizeAndForget.
//     *  We are okay with race conditions (last one overwrites)
//     */
//    def memoizeAndForgetViaRef[F[_] : FlatMap : Monad : Clock, A](generator: F[A], forgetAfter: FiniteDuration, ref: Ref[F,Option[(A,FiniteDuration)]])(implicit mk: Make[F]): F[A] = {
//      val F = Monad[F]
//
//      for {
//        refValue <- ref.get
//        currentTime <- Clock[F].realTime // we don't really care if the currentTime drifts a bit we are going to assume forgetAfter time is significantly large than time to run delegate
//        memoizedValue <-
//          refValue match {
//            case Some((v, rememberUntilTime)) =>
//              if (currentTime < rememberUntilTime)
//                F.pure(Some(v))
//              else
//                ref.set(None).map(_ => None)
//            case None =>
//              F.pure(None)
//          }
//        resolvedValue <-
//          memoizedValue match {
//            case None =>
//              for {
//                v <- generator
//                currentTime <- Clock[F].realTime
//                _ <- ref.set(Some(v, currentTime + forgetAfter))
//              } yield v
//            case Some(v) =>
//              F.pure(v)
//          }
//      } yield resolvedValue
//
//    }
//
//    case class CacheBuilderImpl[F[_] : FlatMap : Monad : Clock : Make,A](
//      generatorFn: F[A],
//      regenerateAfter: Option[FiniteDuration] = None
//    )
//      extends CacheBuilder[F, A]
//    {
//
//      def generator[B](fn: F[B]): CacheBuilder[F, B] =
//        copy(generatorFn = fn)
//
//      def regenerateAfter(finiteDuration: FiniteDuration): CacheBuilder[F,A] =
//        copy(regenerateAfter = Some(finiteDuration))
//
//      def compile: F[Cache[F,A]] =
//        for {
//          ref <- Ref.of(none[(A, FiniteDuration)])
//        } yield CacheImpl(this, ref)
//
//    }
//
//    case class CacheImpl[F[_] : FlatMap : Monad : Clock : Make,A](
//      builder: CacheBuilderImpl[F,A],
//      ref: Ref[F, Option[(A, FiniteDuration)]],
//    )
//      extends Cache[F,A]
//    {
//      override val get: F[A] = memoizeAndForgetViaRef(builder.generatorFn, builder.regenerateAfter.getOrElse(FiniteDuration(9999, TimeUnit.DAYS)), ref)
//      override def reset: F[Unit] = ref.set(None)
//      override def state: F[Option[A]] = ref.get.map(_.map(_._1))
//    }
//
//  }
//
//  trait CacheBuilder[F[_],A] {
//    def generator[B](fn: F[B]): CacheBuilder[F, B]
//    def regenerateAfter(finiteDuration: FiniteDuration): CacheBuilder[F,A]
//    //  def activelyForget(finiteDuration: FiniteDuration): CacheBuilder[F,A]
//    def compile: F[Cache[F,A]]
//  }

}

trait Cache[A] {
  def get: Task[A]
  def reset: Task[Unit]
  def state: Task[Option[A]]
}
