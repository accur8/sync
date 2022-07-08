package playground

import a8.shared.app.BootstrappedIOApp
import a8.sync.http.{Request, RequestProcessor, RetryConfig}
import sttp.client3.UriContext
import zio.{Task, ZIO}
import a8.shared.SharedImports._

object HttpClientRequestDemo extends BootstrappedIOApp {

  override def runT: Task[Unit] = {
    ZIO.scoped {
      loggerF.debug("debug") *> loggerF.info("info") *>
      RequestProcessor.asResource(RetryConfig(3, 2.seconds, 20.seconds)).flatMap { implicit rp =>
//        Request(uri"http://googlsasdfasde.com")
        Request(uri"http://localhost")
          .execWithStringResponse
          .map { s => println(s) }
      }
    }
  }

}
