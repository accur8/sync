package a8.sync


import a8.common.logging.LoggerF
import a8.shared
import a8.shared.FileSystem.{Directory, File}
import a8.shared.SharedImports
import a8.shared.SharedImports._
import a8.shared.app.BootstrapConfig.TempDir
import a8.common.logging.LoggingF
import a8.sync.ReplayableStream.{ConsumptionState, Replay}
import a8.sync.ReplayableStream.ConsumptionState.StreamingToFile
import a8.sync.ReplayableStream.{ConsumptionState, Replay}
import a8.sync.ReplayableStream.Replay.{ReplayError, ReplayFromBuffer, ReplayFromFile}
import zio.stream.{ZSink, ZStream}
import zio.{Cause, Chunk, Promise, Ref, Scope, Task, UIO, ZIO, ZLayer}

import java.io.FileOutputStream
import java.time.LocalDate

object ReplayableStream extends LoggingF {

  trait Factory {
    def makeReplayable(stream: XStream[Byte], eagerlyConsume: Boolean = true): ZIO[Scope, Throwable, XStream[Byte]]
  }

  object Factory {
    val live: ZLayer[TempDir, Throwable, Factory] = ZLayer {
      for {
        tempDir <- ZIO.service[TempDir]
      } yield FactoryImpl(tempDir.resolved.subdir("ReplayableByteStreams"))
    }
  }

  case class FactoryImpl(cacheDir: Directory, inMemoryBufferSize: Int = 8*1024) extends Factory {

    override def makeReplayable(stream: XStream[Byte], eagerlyConsume: Boolean): ZIO[Scope, Throwable, XStream[Byte]] = {
      val newFileEffect =
        ZIO.attemptBlocking {
          val date = LocalDate.now()
          val uuid = java.util.UUID.randomUUID().toString.replace("-", "")
          cacheDir
            .subdir(date.getYear.toString)
            .subdir(f"${date.getMonthValue}%02d")
            .subdir(f"${date.getDayOfMonth}%02d")
            .subdir(uuid.substring(0,4))
            .file(uuid)
        }
      for {
        shutdownRef <- Ref.make(false)
        consumptionSubmittedRef <- Ref.make(false)
        consumptionStateRef <- Ref.make[ConsumptionState](ConsumptionState.Buffering(Chunk.empty))
        replayPromise <- Promise.make[Nothing,Replay]
        shutdownPromise <- Promise.make[Throwable,Unit]
        replayableStream <-
          ZIO.acquireRelease(
            ZIO.succeed(
              new ReplayableStream(newFileEffect, inMemoryBufferSize, shutdownRef, consumptionSubmittedRef, consumptionStateRef, replayPromise, shutdownPromise, stream)
            )
          )(
            _.shutdown
          )
         _ <- {
           if ( eagerlyConsume )
             replayableStream.submitConsumption
           else
             ZIO.unit
         }

      } yield replayableStream.unscopedReplayableStream
    }

  }


  sealed trait ConsumptionState {
    def asReplay: Replay
  }
  object ConsumptionState {
    case class Buffering(buffer: Chunk[Byte]) extends ConsumptionState {
      override def asReplay: Replay = ReplayFromBuffer(buffer)
    }
    case class StreamingToFile(file: File, out: Chunk[Byte] => Task[Unit], closeEffect: Task[Unit]) extends ConsumptionState {
      override def asReplay: Replay = ReplayFromFile(file)
    }
  }

  sealed trait Replay {
    def playback: XStream[Byte]
  }

  object Replay {
    case class ReplayFromBuffer(buffer: Chunk[Byte]) extends Replay {
      override def playback: SharedImports.XStream[Byte] =
        ZStream.fromChunk(buffer)
    }
    case class ReplayFromFile(file: File) extends Replay {
      override def playback: shared.SharedImports.XStream[Byte] =
        ZStream.fromFile(new java.io.File(file.canonicalPath))
    }
    case class ReplayError(head: Replay, error: Cause[Throwable]) extends Replay {
      override def playback: _root_.a8.shared.SharedImports.XStream[Byte] = {
        val throwable = error.failureOption.getOrElse(new RuntimeException("replaying error unable to get originating throwable"))
        head.playback ++ ZStream.fail(throwable)
      }

    }
  }

  def apply(stream: XStream[Byte], eagerlyConsume: Boolean = true): ZIO[Factory & Scope,Throwable,XStream[Byte]] =
    ZIO.service[Factory]
      .flatMap(_.makeReplayable(stream, eagerlyConsume))

}

/**
 * Worth noting lots of refs to keep state transitions simple and atomic, we tried
 * a few times to do this with single state but then transitions become no longer
 * atomic.  Specifically transition from consumption not started to consumption started.
 *
 * shutdownPromise is needed so we can trigger halting any open streams.
 * replayPromise is for
 *
 */
class ReplayableStream(
  newFileEffect: Task[File],
  inMemoryBufferSize: Int,
  shutdownRef: Ref[Boolean],
  consumptionSubmittedRef: Ref[Boolean],
  consumptionStateRef: Ref[ConsumptionState],
  replayPromise: Promise[Nothing, Replay],
  shutdownPromise: Promise[Throwable, Unit],
  originalStream: XStream[Byte],
) {

  import ReplayableStream._

  def submitConsumption: ZIO[Any,Nothing,Unit] =
    for {
      consumptionSubmitted <- consumptionSubmittedRef.getAndSet(true)
      _ <-
        if (consumptionSubmitted) {
          ZIO.unit
        } else {
          consumeInitialStream
            .fork
        }
    } yield ()

  def shutdown: UIO[Unit] =
    for {
      consumptionState <- consumptionStateRef.get
      _ <- ZIO.attemptBlocking {
        consumptionState match {
          case StreamingToFile(f, _, _) =>
            if (f.exists())
              f.delete()
          case _ =>
          // noop
        }
      }.logVoid
      _ <- shutdownRef.set(true)
      _ <- shutdownPromise.fail(new RuntimeException("replayable stream shutdown"))
    } yield ()

  def unscopedReplayableStream: XStream[Byte] =
    replayPromise
      .await
      .zstreamEval
      .flatMap { replay =>
        shutdownRef
          .get
          .zstreamEval
          .flatMap {
            case true =>
              ZStream.fail(new RuntimeException("replayable stream already shutdown"))
            case false =>
              replay
                .playback
                .haltWhen(shutdownPromise)
          }
      }

  def consumeInitialStream: Task[Unit] = {

    // TODO ??? re-think this since there are cleanup holes
    def shuntToDisk(chunk: Chunk[Byte]): Task[Unit] = {
      for {
        file <- newFileEffect
        newState <-
          ZIO.attemptBlocking {
            if (!file.parent.exists())
              file.parent.makeDirectories()
            val out = new FileOutputStream(file.canonicalPath)
            out.write(chunk.toArray)

            def writeChunk(ch: Chunk[Byte]): Task[Unit] = {
              ZIO.attemptBlocking(out.write(ch.toArray))
            }

            StreamingToFile(
              file,
              writeChunk,
              ZIO.attemptBlocking(out.close()),
            )
          }
        _ <- consumptionStateRef.set(newState)
      } yield ()
    }

    def closeInitialConsumption(causeOpt: Option[Cause[Throwable]]): Task[Unit] =
      for {
        consumptionState <- consumptionStateRef.get
        replay = {
          causeOpt match {
            case None =>
              consumptionState.asReplay
            case Some(cause) =>
              ReplayError(consumptionState.asReplay, cause)
          }
        }
        _ <- replayPromise.succeed(replay)
      } yield ()

    def tail: ZStream[Any, Throwable, Nothing] =
      closeInitialConsumption(None)
        .zstreamExec

    import ConsumptionState._
    val head: ZStream[Any, Throwable, Byte] =
      originalStream
        .mapChunksZIO { chunk =>
          val tapEffect: Task[Unit] =
            consumptionStateRef
              .get
              .flatMap {
                case Buffering(buffer) if (buffer.size + chunk.size) > inMemoryBufferSize =>
                  shuntToDisk(buffer.concat(chunk))
                case Buffering(buffer) =>
                  consumptionStateRef
                    .set(Buffering(buffer.concat(chunk)))
                case StreamingToFile(_, out, _) =>
                  out(chunk)
              }
          tapEffect.as(chunk)
        }
        .onError(cause => closeInitialConsumption(cause.some).logVoid)
    (head ++ tail)
      .runDrain
  }

}
