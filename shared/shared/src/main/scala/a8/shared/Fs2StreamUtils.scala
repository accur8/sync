package a8.shared


import SharedImports._

object Fs2StreamUtils {

  sealed trait Event[A]

  object Event {
    case class Value[A](a: A) extends Event[A]

    case class Error[A](th: Throwable) extends Event[A]

    case class EndOfStream[A]() extends Event[A]
  }


  object ErrorHandler {
    case object noop extends ErrorHandler {
      override def process[F[_] : Async](throwable: Throwable): F[Unit] = {
        Async[F].unit
      }
    }
  }

  trait ErrorHandler {
    def process[F[_] : Async](throwable: Throwable): F[Unit]
  }

  abstract class AsyncCallback[F[_] : Async, A] {

    val errorHandler: ErrorHandler
    val dispatcher: Dispatcher[F]

    protected def force[A](fa: F[A]): Unit = {
      dispatcher.unsafeRunAndForget(
        fa.onError {
          case th =>
            errorHandler.process(th)
        }
      )
    }

    def nextF(a: A): F[Unit]

    def errorF(th: Throwable): F[Unit]

    def closeF(): F[Unit]

    val stream: fs2.Stream[F, A]

    def forceNext(a: A): Unit =
      force(nextF(a))

    def forceError(th: Throwable): Unit =
      force(errorF(th))

    def forceClose(): Unit =
      force(closeF())

  }

  def createAsyncCallback[F[_] : Async, A](errorHandler0: ErrorHandler = ErrorHandler.noop): F[AsyncCallback[F, A]] = {

    val F = Async[F]

    Dispatcher[F].use { dispatcher0 =>
      Queue.unbounded[F, Event[A]].map { queue =>
        val stream0 =
          fs2.Stream.fromQueueUnterminated(queue)
            .flatMap { // raise errors
              case Event.Error(th) =>
                fs2.Stream.raiseError[F](th)
              case Event.EndOfStream() =>
                fs2.Stream.empty
              case Event.Value(a) =>
                fs2.Stream.emit[F, A](a)
            }

        def enqueueF(event: Event[A]): F[Unit] = queue.offer(event)

        new AsyncCallback[F, A] {
          val dispatcher = dispatcher0
          val errorHandler = errorHandler0

          override def nextF(a: A): F[Unit] = enqueueF(Event.Value(a))

          override def errorF(th: Throwable): F[Unit] = enqueueF(Event.Error(th))

          override def closeF(): F[Unit] = enqueueF(Event.EndOfStream())

          val stream = stream0
        }

      }
    }

  }

  def createAsyncCallback2[F[_] : Async, A](errorHandler0: ErrorHandler = ErrorHandler.noop)(implicit dispatcher0: Dispatcher[F]): F[AsyncCallback[F, A]] = {

    val F = Async[F]

    //    Dispatcher[F].use { dispatcher0 =>
    Queue.unbounded[F, Event[A]].map { queue =>
      val stream0 =
        fs2.Stream.fromQueueUnterminated(queue)
          .takeWhile {
            case Event.EndOfStream() =>
              false
            case _ =>
              true
          }
          .flatMap { // raise errors
            case Event.Error(th) =>
              fs2.Stream.raiseError[F](th)
            case Event.EndOfStream() =>
              fs2.Stream.empty
            case Event.Value(a) =>
              fs2.Stream.emit[F, A](a)
          }

      def enqueueF(event: Event[A]): F[Unit] = queue.offer(event)

      new AsyncCallback[F, A] {
        val dispatcher = dispatcher0
        val errorHandler = errorHandler0

        override def nextF(a: A): F[Unit] = enqueueF(Event.Value(a))

        override def errorF(th: Throwable): F[Unit] = enqueueF(Event.Error(th))

        override def closeF(): F[Unit] = enqueueF(Event.EndOfStream())

        val stream = stream0
      }

    }
    //    }

  }

  def materialize[F[_], A](source: fs2.Stream[F, A]): fs2.Stream[F, Event[A]] = {
    source
      .attempt
      .map {
        case Left(e) =>
          Event.Error[A](e)
        case Right(v) =>
          Event.Value[A](v)
      }
      .onComplete(fs2.Stream.emit[F, Event[A]](Event.EndOfStream[A]()))
  }


}
