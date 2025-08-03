package a8.sync

import zio.{ZIO, ZIOAppDefault}
import zio.{ Scope, ZIOAppArgs }
import zio.stream.ZStream

object ReplayableByteStreamDemo extends ZIOAppDefault {

  val stream: ZStream[Any,Nothing,Byte] = ReplayableByteStreamSpec.newStream(1024)

  def run: ZIO[Environment & ZIOAppArgs & Scope,Any,Any] = {
    for {
      buffer <-
        stream
          .mapChunksZIO(chunk =>
            ZIO.succeed(chunk)
          )
          .runCollect
    } yield buffer
  }

}
