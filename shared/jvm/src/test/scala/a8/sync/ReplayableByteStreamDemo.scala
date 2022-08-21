package a8.sync

import zio.{ZIO, ZIOAppDefault}

object ReplayableByteStreamDemo extends ZIOAppDefault {

  val stream = ReplayableByteStreamSpec.newStream(1024)

  def run = {
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
