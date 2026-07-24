package a8.hermes.ws

import a8.common.logging.Logging
import a8.hermes.core.Mailbox
import a8.hermes.core.Mailbox.*
import a8.hermes.proto.process.wsmessages as ws
import a8.shared.app.Ctx
import a8.shared.zreplace.XStream
import com.google.protobuf.ByteString

import java.time.Instant
import scala.concurrent.duration.*

/**
 * A Mailbox whose transport is the WEBSOCKET that created it.
 *
 * This is the Scala counterpart of godev's rpcclient.WsMailbox, and the endpoint of the
 * WS-native bootstrap: hermes dials the gateway, authenticates, the gateway mints the
 * processrun and the mailbox, and the SAME socket then carries the mailbox's traffic. No
 * NATS connection, no mesh.mailbox.v1.* req/reply (the surface being starved out), and no
 * database credentials on the client.
 * See tracker FEATURE-20260724-ws-native-bootstrap-and-mailbox-transport.
 *
 * Sending is a SendMessageRequest addressed to the recipient; receiving is the gateway
 * pushing MessageEnvelopes for the subscriptions asserted on FirstMessage. The subject
 * arithmetic SimpleMailbox does (mesh.<address>.<channel>) has no analogue here — the
 * gateway routes, which is the point of going through it.
 */
class WsMailbox(
  val metadata: MailboxMetadata,
  conn: WsMeshConnection,
) extends Mailbox with Logging {

  override def send(
    to: MailboxAddress,
    message: MailboxMessage,
  )(using ctx: Ctx): Unit = {
    val contentType =
      message.contentType match {
        case ContentType.Json => ws.ContentType.Json
        case _                => ws.ContentType.Protobuf
      }

    val wireMessage =
      ws.Message(
        header = Some(
          ws.MessageHeader(
            sender = metadata.address.value,
            contentType = contentType,
            rpcHeader = Some(
              ws.RpcHeader(
                correlationId = message.correlationId,
                endPoint = message.endpoint,
              )
            ),
            extraHeaders = message.metadata.toSeq.map { case (k, v) => ws.KeyValPair(key = k, `val` = v) },
          )
        ),
        senderEnvelope = Some(ws.SenderEnvelope(created = System.currentTimeMillis())),
        data = ByteString.copyFrom(message.payload),
      )

    conn.send(
      ws.MessageFromClient(
        ws.MessageFromClient.Message.SendMessageRequest(
          ws.SendMessageRequest(
            to = Seq(to.value),
            message = Some(wireMessage),
            channel = Channel.RpcInbox.name,
          )
        )
      )
    )
  }

  /**
   * Inbound messages for `channel`, as they arrive on the socket.
   *
   * The subscription itself was asserted on FirstMessage during bootstrap (the gateway
   * pushes without a further request), so this only decodes what arrives. Envelopes for
   * other channels are skipped rather than failing the stream: one socket carries every
   * subscription, so seeing another channel's traffic is normal, not an error.
   */
  override def subscribe(channel: Channel)(using ctx: Ctx): XStream[MailboxMessage] =
    XStream.acquireRelease {
      val iterator =
        new Iterator[MailboxMessage] {
          private var nextValue: Option[MailboxMessage] = None
          private var finished = false
          override def hasNext: Boolean = {
            if (!finished && nextValue.isEmpty) {
              nextValue = pullNext(channel)
              if (nextValue.isEmpty) finished = true
            }
            nextValue.isDefined
          }
          override def next(): MailboxMessage = {
            if (!hasNext) throw new NoSuchElementException("ws mailbox stream exhausted")
            val v = nextValue.get
            nextValue = None
            v
          }
        }
      ((), iterator)
    }(_ => ())

  private def pullNext(channel: Channel): Option[MailboxMessage] = {
    var out: Option[MailboxMessage] = None
    while (out.isEmpty) {
      conn.receive(WsMailbox.ReceivePollTimeout) match {
        case None => return None // socket idle/closed — end the stream
        case Some(m2c) =>
          m2c.message match {
            case ws.MessageToClient.Message.MessageEnvelope(env) =>
              out = decode(env)
            case ws.MessageToClient.Message.Notification(n) =>
              logger.debug(s"ws notification: ${n.message}")
            case _ =>
              () // pings/subscribe-responses/etc — not this stream's business
          }
      }
    }
    out
  }

  private def decode(env: ws.MessageEnvelope): Option[MailboxMessage] =
    try {
      val inner = ws.Message.parseFrom(env.messageBytes.toByteArray)
      val header = inner.header.getOrElse(ws.MessageHeader())
      val rpc = header.rpcHeader.getOrElse(ws.RpcHeader())
      Some(
        MailboxMessage(
          correlationId = rpc.correlationId,
          fromMailbox = MailboxAddress(if (header.sender.nonEmpty) header.sender else "aa_unknown"),
          endpoint = rpc.endPoint,
          contentType =
            header.contentType match {
              case ws.ContentType.Json => ContentType.Json
              case _                   => ContentType.Protobuf
            },
          payload = inner.data.toByteArray,
          metadata = header.extraHeaders.map(kv => kv.key -> kv.`val`).toMap,
        )
      )
    } catch {
      case e: Exception =>
        logger.warn("ws: undecodable inbound message envelope", e)
        None
    }

  /**
   * No-op: a WS mailbox's liveness IS the connection. The mesh mailbox pinger exists so a
   * mailbox whose owner is alive-but-quiet is not purged; an open socket already says that,
   * and the gateway tracks it on the connection.
   */
  override def touch()(using ctx: Ctx): Unit = ()

  /** Close the underlying socket. The mailbox is useless afterwards. */
  def close(): Unit = conn.close()

}

object WsMailbox extends Logging {

  /** How long a subscribe pull waits before treating the socket as idle. */
  val ReceivePollTimeout: FiniteDuration = 5.minutes

  /**
   * WS-NATIVE BOOTSTRAP: dial, authenticate, and have the gateway write the processrun and
   * mint the mailbox — then keep that socket as the mailbox's transport.
   *
   * workerUid is OWNERSHIP-CHECKED server-side against the authenticated user (claiming a
   * worker you do not own is refused, not silently accepted); empty is legal for an ad-hoc
   * CLI. The gateway commits the processrun row BEFORE minting, so the mailbox can never
   * name an owner that does not exist.
   */
  def bootstrap(
    meshRootUrl: String,
    authToken: String,
    processUid: String,
    workerUid: String = "",
    appName: String = "hermes",
    lifecycleKind: String = "short-lived-cli",
    channels: Seq[String] = Seq(Channel.RpcInbox.name),
  ): WsMailbox = {
    val conn = WsMeshConnection.connect(meshRootUrl)
    try {
      val started = conn.bootstrap(authToken, processUid, workerUid, appName, lifecycleKind, channels)

      // Assert the subscription for this mailbox's inbox, exactly as the godev client does
      // after its own bootstrap: FirstMessage carries the keys the gateway just handed us.
      conn.send(
        ws.MessageFromClient(
          ws.MessageFromClient.Message.FirstMessage(
            ws.FirstMessage(
              senderInfo = Some(ws.SenderInfo(readerKey = started.readerKey, address = started.address)),
              subscriptions = Seq(
                ws.Subscription(
                  ws.Subscription.Oneof.Mailbox(
                    ws.MailboxSubscription(
                      id = Channel.RpcInbox.name,
                      channel = Channel.RpcInbox.name,
                      readerKey = started.readerKey,
                      startSeq = "first",
                    )
                  )
                )
              ),
              authToken = authToken,
            )
          )
        )
      )

      val now = Instant.now()
      val metadata =
        MailboxMetadata(
          adminKey = AdminKey(started.adminKey),
          readerKey = ReaderKey(started.readerKey),
          address = MailboxAddress(started.address),
          lifecycle = LifecycleType.Ephemeral,
          createdAt = now,
          expiresAt = now.plusSeconds(LifecycleType.Ephemeral.ttl.toSeconds),
          lastAccessedAt = now,
        )

      logger.info(s"ws-native mailbox ${started.address} bootstrapped (processrun ${started.processUid})")
      new WsMailbox(metadata, conn)
    } catch {
      case e: Throwable =>
        conn.close()
        throw e
    }
  }

}
