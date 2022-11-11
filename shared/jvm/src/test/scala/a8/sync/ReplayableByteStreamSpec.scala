package a8.sync


import a8.shared.FileSystem
import a8.shared.app.BootstrapConfig.TempDir
import a8.sync.ReplayableStream.Factory
import zio.stream.ZStream
import zio.{Ref, Scope, ULayer, ZIO, ZLayer}
import zio.test._
import a8.shared.SharedImports._

import java.util.concurrent.atomic.AtomicInteger

object ReplayableByteStreamSpec extends ZIOSpecDefault {

  val tempDirLayer: ULayer[TempDir] = ZLayer.succeed(TempDir(FileSystem.dir("temp")))

  def newStream(length: Int) = ZStream.fromIterable((0 to length).map(_.toByte))

  def spec =
    suite("ReplayableByteStreamSpec")(

      test("in memory replay 1k") {
        runTest(1024)
      },

      test("off heap replay 100k") {
        runTest(1024*100)
      },

      test("1k bytes with 100 in parallel") {
        runInParallel(1024, 100)
      },

//      test("50k bytes with 100 in parallel") {
//        runInParallel(50*1024, 100)
//      }

    )


  def runTest(length: Int): ZIO[Any, Throwable, TestResult] = {
    val sourceStream = newStream(length)
    val counter = new AtomicInteger()
    val streamCountingHead = ZIO.attempt(counter.incrementAndGet()).zstreamExec
    val countedStream = streamCountingHead ++ sourceStream
    val effect: ZIO[Factory with Scope, Throwable, TestResult] =
      for {
        replayableStream <- ReplayableStream(countedStream)
        baseLine <- sourceStream.runCollect
        firstRead <- replayableStream.runCollect
        secondRead <- replayableStream.runCollect
      } yield {
        val matchesBaseline = baseLine == firstRead
        val firstReadAndSecondReadMatches = firstRead == secondRead
        assertTrue(matchesBaseline && firstReadAndSecondReadMatches && counter.get() == 1)
      }

    ZIO
      .scoped(
        effect
          .either
          .flatMap {
            case Left(e) =>
//              e.printStackTrace()
              ZIO.fail(e)
            case Right(b) =>
//              println(b)
              zsucceed(b)
          }
      )
      .provideLayer(Factory.live)
      .provideLayer(tempDirLayer)

  }


  def runInParallel(length: Int, numberInParallel: Int) = {
    val sourceStream = newStream(length)
    val counter = new AtomicInteger()
    val streamCountingHead = ZIO.attempt(counter.incrementAndGet()).zstreamExec
    val countedStream = streamCountingHead ++ sourceStream
    val effect =
      for {
        replayableStream <- ReplayableStream(countedStream)
        baseLine <- sourceStream.runCollect
        fibers <-
          ZIO.collectAllPar(
            (1 to numberInParallel)
              .map(_ => replayableStream.runCollect.fork)
          )
        results <-
          ZIO.collectAllPar(
            fibers
              .map(_.join)
          )
      } yield {
        val everythingMatches = results.forall(baseLine == _)
        assertTrue(everythingMatches && counter.get() == 1)
      }

    ZIO
      .scoped(effect)
      .provideLayer(Factory.live)
      .provideLayer(tempDirLayer)
  }

}