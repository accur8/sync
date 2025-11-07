package playground

import a8.shared.app.{AppCtx, BootstrappedIOApp}
import a8.sync.http.{Request, RequestProcessor, RetryConfig}
import sttp.client4.*
import a8.shared.SharedImports.*

object HttpClientRequestDemo extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit = {
    logger.debug("debug")
    logger.info("info")
    given RequestProcessor = RequestProcessor.asResource(RetryConfig(3, 2.seconds, 20.seconds)).unwrap

    val response =
      Request(uri"https://google.com")
        .execWithStringResponse

    println(response)

  }

}
