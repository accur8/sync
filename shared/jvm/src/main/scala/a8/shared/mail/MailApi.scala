package a8.shared.mail

import a8.shared.SharedImports._
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
object MailApi {

  def asResource[F[_] : Sync](config: MailConfig): Resource[F, MailApi[F]] = {

    def acquire: F[MailApi[F]] =
      Sync[F].blocking {
        val props = new Properties()
        props.put("mail.debug", config.debug.toString)
        props.put("mail.debug.auth", config.debug.toString)
        props.put("mail.smtp.host", config.host)
        config.port.map(port => props.put("mail.smtp.port", port.toString))
        props.put("mail.smtp.auth", config.user.nonEmpty.toString)
        config.user.map(user => props.put("mail.smtp.user", user))
        props.put("mail.smtp.ssl.enable", config.sslEnabled.toString)
        props.put("mail.smtp.starttls.enable", config.startTlsEnabled.toString)
        props.put("mail.smtp.starttls.required", config.startTlsEnabled.toString)
        if (!config.certCheckEnabled) props.put("mail.smtp.ssl.trust", "*")

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

    def release(mailApi: MailApi[F]): F[Unit] =
      Sync[F].blocking {
        mailApi.transport.close()
      }

    Resource.make(acquire)(release)
  }

}

case class MailApi[F[_]: Sync](session: Session, transport: Transport) {

  def send(message: MailMessage): F[Unit] = {
    Sync[F].blocking {
      val mimeMessage = message.toMimeMessage(session)
      transport.sendMessage(mimeMessage, mimeMessage.getAllRecipients)
    }
  }

}
