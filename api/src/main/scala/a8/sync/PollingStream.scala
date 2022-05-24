package a8.sync


import scala.concurrent.duration.FiniteDuration
import Imports._
import a8.shared.app.Logging

import zio._
import zio.stream.ZStream

object PollingStream {

  def tailingStream[A](
    start: Option[A],
    pollFn: Option[A]=>XStream[A],
    interval: Duration,
  ): XStream[A] = {
    pollFn(start)
      .onLastO { last =>
        val delay = ZIO.sleep(interval).zstreamExec
        delay ++ tailingStream[A](last, pollFn, interval)
      }
  }

  def fromStream[A](
    finitePollFn: =>XStream[A],
    interval: Duration,
  ): XStream[A] = {
    tailingStream(
      start = None,
      pollFn = { _: Option[A] => finitePollFn },
      interval = interval,
    )
  }

  def fromIterable[A](
    finitePollFn: =>Task[Iterable[A]],
    pauseOnEmpty: Duration,
    onFailure: Throwable=>XStream[A],
  ): XStream[A] = {

    def runForever: XStream[A] =
      runTilEmpty ++ ZIO.sleep(pauseOnEmpty).zstreamExec ++ runForever

    def runTilEmpty: XStream[A] =
      finitePollFn
        .map { iter =>
          if ( iter.isEmpty )
            ZStream.empty
          else
            ZStream.fromIterable(iter) ++ runTilEmpty
        }
        .zstreamFlat
        .catchAll(onFailure)

    runForever

  }

}
