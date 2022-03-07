package a8.shared.ops


import a8.shared.CatsUtils.Memo
import a8.shared.SharedImports._
import a8.shared.app.LoggerF

class AsyncOps[F[_] : Async, A](fa: F[A]) {

  def memo: F[Memo[F,A]] = Memo.create[F,A](fa)

  def logVoid(implicit loggerF: LoggerF[F]): F[Unit] =
    fa
      .onError(th => loggerF.warn("got an error and logging it and then swallowing it", th))
      .void

  def logError(implicit loggerF: LoggerF[F]): F[A] =
    fa.onError(th => loggerF.warn("got an error and logging it and then re-throwing it", th))

  def unsafeRunSync(implicit dispatcher: Dispatcher[F]): A =
    dispatcher.unsafeRunSync(fa)

  def fs2StreamExec: fs2.Stream[F,fs2.INothing] =
    fs2.Stream.exec[F](fa)

  def fs2StreamEval: fs2.Stream[F,A] =
    fs2.Stream.eval[F,A](fa)

}