package a8.shared.mail

import a8.shared.CompanionGen
import a8.shared.mail.MxMailConfig.MxMailConfig

object MailConfig extends MxMailConfig

@CompanionGen(jsonCodec = true)
case class MailConfig(
  host: String,
  port: Option[Int] = None,
  user: Option[String] = None,
  password: Option[String] = None,
  sslEnabled: Boolean = false,
  startTlsEnabled: Boolean = true,
  certCheckEnabled: Boolean = true,
  debug: Boolean = false, // if true, also make sure your log levels for "com.sun.mail" and "jakarta" are set to DEBUG
)
