package a8.shared.mail

import a8.shared.app.BootstrappedIOApp
import cats.effect.IO
import wvlet.log.{LogLevel, Logger}

object MailApiDemo extends BootstrappedIOApp("mailapidemo") {

  Logger("com.sun.mail").setLogLevel(LogLevel.DEBUG)
  Logger("jakarta").setLogLevel(LogLevel.DEBUG)

  lazy val mailConfig = MailConfig(
    host = "smtp.gmail.com",
    port = Some(587),
    user = Some("raphael@accur8software.com"),
    password = Some(""),
    debug = true,
  )

  lazy val mailApiR = MailApi.asResource[IO](mailConfig)

  override def run: IO[Unit] = {
    mailApiR.use { mailApi =>
      val message =
        MailMessage()
         .from("Raphael <raphael@accur8software.com>")
         .to("raphael@accur8software.com")
         .subject("Test")
         .body("This is a test")
      mailApi.send(message)
    }
  }

}
