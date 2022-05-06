package playground


import cats.effect.{ExitCode, IO, IOApp}
import a8.shared.SharedImports._

object CancelFs2Stream extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    fs2.Stream.iterable(0 to 99999)
      .evalMap { i =>
        val effect =
          for {
            _ <- IO.println(s"processing ${i}")
            _ <- IO.sleep(10.seconds)
            _ <- IO.println(s"done processing ${i}")
          } yield ()
        effect.uncancelable
      }
      .compile
      .drain
      .as(ExitCode(0))

}
