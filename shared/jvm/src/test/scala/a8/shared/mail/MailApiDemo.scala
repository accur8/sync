package a8.shared.mail


import a8.shared.SharedImports.*
import a8.shared.app.{AppCtx, BootstrappedIOApp}
import zio.*

object MailApiDemo extends BootstrappedIOApp {

  override lazy val defaultAppName: String = "mailapidemo"

//  Logger("com.sun.mail").setLogLevel(LogLevel.DEBUG)
//  Logger("jakarta").setLogLevel(LogLevel.DEBUG)

  lazy val mailConfig: MailConfig = MailConfig(
    host = "smtp.gmail.com",
    port = Some(587),
    user = Some("raphael@accur8software.com"),
    password = Some(""),
    debug = true,
  )

  lazy val mailApiR: Resource[MailApi] = MailApi.asResource(mailConfig)

  override def run()(using appCtx: AppCtx): Unit = {
    val mailApi = mailApiR.unwrap
    val message =
      MailMessage()
       .from("Raphael <raphael@accur8software.com>")
       .to("raphael@accur8software.com")
       .subject("Test")
       .body("This is a test")
    mailApi.send(message)
  }

}
