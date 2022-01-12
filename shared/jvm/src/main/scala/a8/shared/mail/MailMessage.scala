package a8.shared.mail

import jakarta.mail.{Address, Message, Session}
import jakarta.mail.internet.{InternetAddress, MimeMessage}

case class MailMessage(
  from: Option[InternetAddress] = None,
  to: Vector[InternetAddress] = Vector(),
  cc: Vector[InternetAddress] = Vector(),
  bcc: Vector[InternetAddress] = Vector(),
  replyTo: Option[InternetAddress] = None,
  subject: Option[String] = None,
  body: Option[String] = None,
) {
  def toMimeMessage(session: Session): MimeMessage = {
    val message = new MimeMessage(session)
    from.foreach(message.setFrom)
    message.setRecipients(Message.RecipientType.TO, to.toArray: Array[Address])
    message.setRecipients(Message.RecipientType.CC, cc.toArray: Array[Address])
    message.setRecipients(Message.RecipientType.BCC, bcc.toArray: Array[Address])
    message.setReplyTo(replyTo.toArray)
    subject.foreach(message.setSubject)
    body.foreach(message.setContent(_, "text/html"))
    message.saveChanges()
    message
  }

  def from(email: String): MailMessage = copy(from = Some(new InternetAddress(email)))
  def to(emails: String*): MailMessage = copy(to = to ++ emails.map(new InternetAddress(_)))
  def cc(emails: String*): MailMessage = copy(cc = cc ++ emails.map(new InternetAddress(_)))
  def bcc(emails: String*): MailMessage = copy(bcc = bcc ++ emails.map(new InternetAddress(_)))
  def replyTo(email: String): MailMessage = copy(replyTo = Some(new InternetAddress(email)))
  def subject(subject: String): MailMessage = copy(subject = Some(subject))
  def body(body: String): MailMessage = copy(body = Some(body))
}
