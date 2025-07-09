package a8.shared.mail

import a8.shared.SharedImports.*
import a8.shared.zreplace.Resource
import jakarta.mail.{Authenticator, PasswordAuthentication, Session, Transport}

import java.util.Properties
/**
 * Usage:
 *
 * lazy val mailApiR = MailApi.asResource[IO](mailConfig)
 *
 * mailApiR.use { mailApi =>
 *   val message =
 *     MailMessage()
 *      .from("Test <from@example.com>")
 *      .to("to1@example.com", "to2@example.com")
 *      .to("to3@example.com")
 *      .subject("Test")
 *      .body("This is a test")
 *   mailApi.send(message)
 * }
 */
object MailApi extends Logging {

  def asResource(config: MailConfig): zio.Resource[MailApi] = {

    def acquire: MailApi = {
      val props = new Properties()
      def put(name: String, value: String): Unit =
        props.put(name,value): @scala.annotation.nowarn
      put("mail.debug", config.debug.toString)
      put("mail.debug.auth", config.debug.toString)
      put("mail.smtp.host", config.host)
      config.port.foreach(port => put("mail.smtp.port", port.toString))
      put("mail.smtp.auth", config.user.nonEmpty.toString)
      config.user.foreach(user => put("mail.smtp.user", user))
      put("mail.smtp.ssl.enable", config.sslEnabled.toString)
      put("mail.smtp.starttls.enable", config.startTlsEnabled.toString)
      put("mail.smtp.starttls.required", config.startTlsEnabled.toString)
      if (!config.certCheckEnabled) put("mail.smtp.ssl.trust", "*")

      val session = config.user match {
        case Some(user) =>
          val authenticator = new Authenticator {
            override def getPasswordAuthentication: PasswordAuthentication =
              new PasswordAuthentication(user, config.password.orNull)
          }
          Session.getInstance(props, authenticator)
        case None =>
          Session.getInstance(props)
      }

      val transport = session.getTransport("smtp")
      transport.connect(config.user.orNull, config.password.orNull)

      MailApi(session, transport)
    }

    def release(mailApi: MailApi): Unit = {
      try {
        mailApi.transport.close()
      } catch {
        case IsNonFatal(th) =>
          logger.debug("catching and swallowing likely benign error on release", th)
      }
      }
    Resource.acquireRelease(acquire)(release)
  }

}

case class MailApi(session: Session, transport: Transport) {

  def send(message: MailMessage): Unit = {
    val mimeMessage = message.toMimeMessage(session)
    transport.sendMessage(mimeMessage, mimeMessage.getAllRecipients)
  }

}
