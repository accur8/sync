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

  /**
   * THE INVARIANT THE wsconform `sent=` COUNT RESTS ON.
   *
   * WsMeshConnection.send tracks BEFORE it attempts the socket write, so a frame whose
   * first attempt throws is already in the in-flight map and will be delivered by
   * resendInFlight() on the next reconnect. The wsconform client therefore counts the
   * HANDOFF, not the first attempt — it used to count only non-throwing sends and reported
   * sent=781 for a partition-heal sample that put index 10346 on the wire, roughly 9.5k
   * messages delivered by the resend path and omitted from the count. The harness uses that
   * number as the gap denominator, so under-reporting let the client shrink what it would
   * be held to.
   *
   * This pins the premise: every frame the conform client sends is trackable, so
   * handoff-implies-tracked holds and counting the handoff cannot over-count.
   * BUG-20260804-hermes-ws-under-reports-sent-count-after-partition.
   */
  test("a conform-shaped send frame is always trackable, so a throwing send is still owed") {
    // Exactly the shape WsConformClientMain builds: one shared correlationId for the whole
    // run, and a per-message idempotentId.
    val frame = requestWithIdempotentId(correlationId = "wsconform-counter", idempotentId = "id-42")
    trackingKey(frame) shouldBe Some("id-42")
  }

  test("tracking prefers the per-message idempotentId over the shared correlationId") {
    // The conform run sends every message under ONE correlation. Keying on correlationId
    // would collapse 20,000 sends into a single in-flight entry, so exactly one could ever
    // be resent — and the count would then be honest about a delivery that never happened.
    val a = requestWithIdempotentId("shared-correlation", "id-1")
    val b = requestWithIdempotentId("shared-correlation", "id-2")
    trackingKey(a) should not be trackingKey(b)
  }

  private def requestWithIdempotentId(correlationId: String, idempotentId: String): ws.MessageFromClient =
    ws.MessageFromClient(
      ws.MessageFromClient.Message.SendMessageRequest(
        ws.SendMessageRequest(
          to = Seq("aaSomeRecipient"),
          channel = "rpc-inbox",
          idempotentId = idempotentId,
          message = Some(
            ws.Message(
              header = Some(
                ws.MessageHeader(
                  sender = "aaSender",
                  rpcHeader = Some(ws.RpcHeader(correlationId = correlationId, endPoint = "wsconform.counter")),
                )
              ),
              data = ByteString.copyFromUtf8("wsc:1:0"),
            )
          ),
        )
      )
    )
}
