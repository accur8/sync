package a8.shared


import cats.syntax
import cats.instances

trait CatsImportsTrait
  extends syntax.AllSyntax
    with syntax.AllSyntaxBinCompat0
    with syntax.AllSyntaxBinCompat1
    with syntax.AllSyntaxBinCompat2
    with syntax.AllSyntaxBinCompat3
    with syntax.AllSyntaxBinCompat4
    with syntax.AllSyntaxBinCompat5
    with syntax.AllSyntaxBinCompat6
    with instances.AllInstances
    with instances.AllInstancesBinCompat0
    with instances.AllInstancesBinCompat1
    with instances.AllInstancesBinCompat2
    with instances.AllInstancesBinCompat3
    with instances.AllInstancesBinCompat4
    with instances.AllInstancesBinCompat5
    with instances.AllInstancesBinCompat6
    with cats.effect.syntax.AllSyntax
    with cats.effect.instances.AllInstances
{

  type Concurrent[F[_]] = cats.effect.kernel.Concurrent[F]
  val Concurrent = cats.effect.kernel.Concurrent

  type Async[F[_]] = cats.effect.kernel.Async[F]
  val Async = cats.effect.kernel.Async

  type Sync[F[_]] = cats.effect.kernel.Sync[F]
  val Sync = cats.effect.kernel.Sync

  type Ref[F[_],A] = cats.effect.kernel.Ref[F,A]
  val Ref = cats.effect.kernel.Ref

  type Semaphore[F[_]] = cats.effect.std.Semaphore[F]
  val Semaphore = cats.effect.std.Semaphore

  type Dispatcher[F[_]] = cats.effect.std.Dispatcher[F]
  val Dispatcher = cats.effect.std.Dispatcher

  type Queue[F[_],A] = cats.effect.std.Queue[F,A]
  val Queue = cats.effect.std.Queue

  type CIString = org.typelevel.ci.CIString
  val CIString = org.typelevel.ci.CIString

  type Resource[F[_],A] = cats.effect.kernel.Resource[F,A]
  val Resource = cats.effect.kernel.Resource

  type MonadCancel[F[_],A] = cats.effect.kernel.MonadCancel[F,A]
  val MonadCancel = cats.effect.kernel.MonadCancel

  type OptionT[F[_],A] = cats.data.OptionT[F,A]
  val OptionT = cats.data.OptionT

  type IOApp = cats.effect.IOApp
  val IOApp = cats.effect.IOApp

  type Chain[A] = cats.data.Chain[A]
  val Chain = cats.data.Chain

  type Monad[F[_]] = cats.Monad[F]
  val Monad = cats.Monad

  type IO[+A] = cats.effect.IO[A]
  val IO = cats.effect.IO

}
