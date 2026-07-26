package a8.hermes.ws

import a8.hermes.proto.process.wsmessages as ws
import com.google.protobuf.ByteString
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Pins IN-FLIGHT REQUEST TRACKING — the specific gap this ticket was filed for.
 *
 * Before this, a SendMessageRequest that was on the wire when the socket dropped was
 * simply lost: no response, no error, the caller waits forever. Tracking a request from
 * send until its response arrives is what lets a reconnect re-send the unanswered ones.
 *
 * These pin the two rules that decide what goes into that map and what comes out. Getting
 * either wrong is silent: track too little and requests vanish on reconnect; retire too
 * eagerly and the same thing happens.
 *
 * See tracker FEATURE-20260724-hermes-ws-reconnect-and-inflight-resend.
 */
class WsInFlightTrackingTest extends AnyFunSuite with Matchers {

  import WsMeshConnection.*

  private def request(correlationId: String): ws.MessageFromClient =
    ws.MessageFromClient(
      ws.MessageFromClient.Message.SendMessageRequest(
        ws.SendMessageRequest(
          to = Seq("aaSomeRecipient"),
          channel = "rpc-inbox",
          message = Some(
            ws.Message(
              header = Some(
                ws.MessageHeader(
                  sender = "aaMySelf",
                  rpcHeader = Some(ws.RpcHeader(correlationId = correlationId, endPoint = "some.Endpoint")),
                )
              ),
              data = ByteString.copyFromUtf8("payload"),
            )
          ),
        )
      )
    )

  private def response(correlationId: String): ws.MessageToClient = {
    val inner =
      ws.Message(
        header = Some(ws.MessageHeader(sender = "aaSomeRecipient", rpcHeader = Some(ws.RpcHeader(correlationId = correlationId)))),
        data = ByteString.copyFromUtf8("result"),
      )
    ws.MessageToClient(
      ws.MessageToClient.Message.MessageEnvelope(
        ws.MessageEnvelope(messageBytes = ByteString.copyFrom(inner.toByteArray))
      )
    )
  }

  // --- what gets tracked -----------------------------------------------------

  test("a SendMessageRequest with a correlationId is tracked") {
    trackingKey(request("corr-1")) shouldBe Some("corr-1")
  }

  test("a request with an EMPTY correlationId is not tracked — nothing could match a response to it") {
    trackingKey(request("")) shouldBe None
  }

  test("a non-request frame is not tracked — re-sending it would duplicate, not recover") {
    // A ClientHello is the handshake, not a request awaiting a reply. Reconnect re-sends the
    // handshake deliberately via ResumeSession; it must not ALSO come back through the
    // in-flight resend path.
    val hello = ws.MessageFromClient(ws.MessageFromClient.Message.ClientHello(ws.ClientHello()))
    trackingKey(hello) shouldBe None
  }

  // --- what retires it -------------------------------------------------------

  test("a response envelope retires the matching request") {
    retiringKey(response("corr-1")) shouldBe Some("corr-1")
  }

  test("a request and its response agree on the key, so track/retire actually pair up") {
    // The round trip is the point: if these two ever disagreed, every request would leak
    // into the map and be re-sent on every future reconnect.
    val cid = "corr-round-trip"
    trackingKey(request(cid)) shouldBe retiringKey(response(cid))
  }

  test("an undecodable envelope retires NOTHING rather than guessing") {
    // Leaving the entry costs at most one duplicate resend (the server dedupes on
    // idempotentId). Dropping it wrongly loses the request permanently — strictly worse.
    val garbage =
      ws.MessageToClient(
        ws.MessageToClient.Message.MessageEnvelope(
          ws.MessageEnvelope(messageBytes = ByteString.copyFrom(Array[Byte](0x42, 0x13, 0x37, 0x7f, 0x5a)))
        )
      )
    retiringKey(garbage) shouldBe None
  }

  test("a non-envelope inbound frame retires nothing") {
    val notification = ws.MessageToClient(ws.MessageToClient.Message.Notification(ws.Notification(message = "hi")))
    retiringKey(notification) shouldBe None
  }

  test("a response with no correlationId retires nothing") {
    retiringKey(response("")) shouldBe None
  }
}
