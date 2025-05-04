package a8.nats

import io.nats.client.{Message, Nats, Options}


import java.io.{ByteArrayOutputStream, FileOutputStream}
import NatsConn.{HttpResponse, NatsConfig, ReplyTo}
import io.nats.client.impl.{Headers, NatsMessage}

object NatsDemoApp {

  case class Request(
    method: String,
    path: String,
    headers: Map[String, String],
    body: Array[Byte],
  )


  def main(args: Array[String]): Unit = {
    run(
      NatsConfig(
        natsUrl = "nats://localhost:4222",
      )
    )
  }

  def run(config: NatsConfig): Unit = {

//    val options =
//      new Options.Builder()
//        .server("nats://10.101.0.80:4222")
//        .userInfo("dev", "DELzF7NoDqkCXiJf4QHK")
//        .build()
    val builder = new Options.Builder()
    val b0 = builder.server(config.natsUrl)

    if (config.user.isDefined && config.password.isDefined) {
      val b1 = builder.userInfo(config.user.get, config.password.get)
    }

    val options = builder.build()

    val nc = Nats.connect(options)

    val maxPayloadSize: Int = nc.getMaxPayload.toInt
    val maxInitialResponseSize = maxPayloadSize / 2

    val keyValueStore = nc.keyValue(config.keyValueBucketName)

    val queueGroupName = "myQueueGroup"

    def sendResponse(response: HttpResponse): Unit = {
      val headers = new Headers()
      response
        .headers
        .foreach((k, v) => {
          headers.put(k, v)
        })
      val h0 = headers.put("#status", response.status.toString)

      if (response.body.length < maxInitialResponseSize) {
        nc.publish(response.replyTo.value, headers, response.body)
      } else {

        val bodyId = java.util.UUID.randomUUID().toString
        val chunkSize = maxPayloadSize - 1000
        val partCount = (response.body.length + chunkSize - 1) / chunkSize
        var i = 0
        while (i < partCount) {
          val start = i * chunkSize
          val end = Math.min(start + chunkSize, response.body.length)
          val part = response.body.slice(start, end)
          val key = s"${bodyId}_${i}"
          val h0 = keyValueStore.put(key, part)
          i += 1
        }

        val h1 = headers.put("#of_id", bodyId)
        val h2 = headers.put("#of_count", partCount.toString)
        nc.publish(response.replyTo.value, headers, Array.emptyByteArray)

      }
    }

    def submitDispatcher(dispatcherName: String): Unit = {
      val dispatcher = nc.createDispatcher((msg) => {

        println(s"--- processing message in ${dispatcherName} ---")
        msg.getHeaders.forEach((k, v) => {
          println("  header " + k + " " + v)
        })
        val h0 = msg.getHeaders.put("#status", dispatcherName)
        println("  subject " + msg.getSubject)
        println("  reply to " + msg.getReplyTo)

        var partCount = 1

        val overflowIdOpt = Option(msg.getHeaders.getFirst("#of_id"))
        val messageCountOpt = Option(msg.getHeaders.getFirst("#of_count")).flatMap(_.toIntOption)

        val bodyBytes =
          (overflowIdOpt, messageCountOpt) match {
            case (Some(bodyId), Some(messageCount)) =>
              val baos = new ByteArrayOutputStream()
              baos.write(msg.getData)
              (0 until messageCount) foreach { i =>
                val key = s"${bodyId}_${i}"
                val kve = keyValueStore.get(key)
                if (kve != null) {
                  baos.write(kve.getValue)
                  partCount += 1
                } else {
                  println(s"  missing part ${key}")
                }
              }
              keyValueStore.history(bodyId).forEach((entry) => {
                baos.write(entry.getValue)
                partCount += 1
              })
              baos.close()
              baos.toByteArray
            case _ =>
              msg.getData
          }

        println(s"  we have read ${bodyBytes.length} bytes" )
        println(s"  in ${partCount} messages" )
        sendResponse(
          HttpResponse(
            replyTo = ReplyTo(msg.getReplyTo),
            status = 201,
            body = bodyBytes,
          )
        )

        msg.ack()

      }).subscribe(config.subject, queueGroupName)

      println("dispatcher " + dispatcher)
    }

    submitDispatcher("bob")
    submitDispatcher("bill")

  }


  def spawn(fn: => Unit): Unit = {
    new Thread(new Runnable {
      override def run(): Unit = fn
    }).start()
  }

}
