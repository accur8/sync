package a8.nats

import a8.nats.MxNatsConn.MxNatsConfig
import a8.shared.CompanionGen


object NatsConn {


  object NatsConfig extends MxNatsConfig
  @CompanionGen
  case class NatsConfig(
    natsUrl: String,
    user: Option[String] = None,
    password: Option[String] = None,
    maxResponseHeadersSize: Int = 20*1024,
    keyValueBucketName: String = "CaddyMessageOverflow",
    subject: String = "events.http.request",
  )

  case class ReplyTo(value: String)

  case class HttpResponse(
    replyTo: ReplyTo,
    status: Int = 200,
    headers: Map[String, String] = Map.empty,
    body: Array[Byte] = Array.empty,
  )

  case class HttpRequest(
    method: String,
    path: String,
    headers: Map[String, String] = Map.empty,
    body: Array[Byte] = Array.empty,
  )

}
