package a8.shared.mail


import a8.shared.SharedImports._
import a8.shared.app.BootstrappedIOApp
import wvlet.log.{LogLevel, Logger}
import zio._

object MailApiDemo extends BootstrappedIOApp {

  override lazy val defaultAppName: String = "mailapidemo"

  Logger("com.sun.mail").setLogLevel(LogLevel.DEBUG)
  Logger("jakarta").setLogLevel(LogLevel.DEBUG)

  lazy val mailConfig: MailConfig = MailConfig(
    host = "smtp.gmail.com",
    port = Some(587),
    user = Some("raphael@accur8software.com"),
    password = Some(""),
    debug = true,
  )

  lazy val mailApiR: ZIO[Scope, Throwable, MailApi] = MailApi.asResource(mailConfig)

  override def runT: Task[Unit] = {
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
