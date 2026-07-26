package a8.hermes.ws

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

/**
 * Pins the RECONNECT DECISION RULES for the hermes WS mailbox.
 *
 * Each test fixes exactly one rule, which is the shape react-playground uses
 * (remint-decision.test.ts / transport-action.test.ts). The rules are pure functions on
 * WsMeshConnection's companion precisely so they can be pinned without a live socket — a
 * reconnect test that needs a real gateway is a test nobody runs.
 *
 * See tracker FEATURE-20260724-hermes-ws-reconnect-and-inflight-resend.
 */
class WsReconnectDecisionTest extends AnyFunSuite with Matchers {

  import WsMeshConnection.*

  // --- resume vs fresh -------------------------------------------------------
  //
  // THE rule this ticket exists to protect. Re-bootstrapping on reconnect mints a SECOND
  // mailbox and orphans the first; a 2026-07-07 incident left 459 dead mailboxes that way.
  // So a connection that HAS a mailbox must resume it, never re-mint.

  test("a held readerKey resumes the existing mailbox (never re-mints)") {
    resumeAction(Some("rrSomeReaderKey")) shouldBe ResumeAction.Resume
  }

  test("no readerKey means bootstrap never completed — start fresh") {
    resumeAction(None) shouldBe ResumeAction.Fresh
  }

  test("an EMPTY readerKey is not a session — treated as fresh, not resumed with a blank key") {
    // Guards a real footgun: proto3 strings default to "" rather than absent, so a
    // half-populated ClientSessionStarted would otherwise resume with an empty key and be
    // refused by the gateway on every attempt.
    resumeAction(Some("")) shouldBe ResumeAction.Fresh
  }

  // --- backoff ---------------------------------------------------------------
  //
  // Bounded so a down gateway is not hammered; capped so recovery is not absurdly slow.
  // Matches godev's schedule rather than inventing a third cadence.

  test("backoff starts at 500ms and doubles") {
    backoffMillis(1) shouldBe 500L
    backoffMillis(2) shouldBe 1000L
    backoffMillis(3) shouldBe 2000L
    backoffMillis(4) shouldBe 4000L
    backoffMillis(5) shouldBe 8000L
  }

  test("backoff is capped at 10s no matter how long the outage runs") {
    backoffMillis(6) shouldBe 10000L
    backoffMillis(50) shouldBe 10000L
    backoffMillis(10000) shouldBe 10000L
  }

  test("backoff never returns a nonsense delay for a zero or negative attempt") {
    // Defensive: an off-by-one in a caller must not produce a 1<<-1 shift.
    backoffMillis(0) shouldBe 500L
    backoffMillis(-5) shouldBe 500L
  }

  // --- half-open detection ---------------------------------------------------
  //
  // A socket can be dead with no onClose ever firing — peer vanished, NAT dropped the
  // mapping, network moved. TCP does not report it. Inbound SILENCE is the only signal.

  test("silence past the timeout on a live session is half-open") {
    isHalfOpen(hasSession = true, silentFor = 6.minutes) shouldBe true
  }

  test("silence within the timeout is NOT half-open — an idle mailbox is normal") {
    isHalfOpen(hasSession = true, silentFor = 4.minutes) shouldBe false
  }

  test("no session means nothing to keep alive — never reconnect on silence") {
    // Before bootstrap there is no mailbox to resume, so reconnecting on silence would
    // churn sockets for nothing.
    isHalfOpen(hasSession = false, silentFor = 99.minutes) shouldBe false
  }

  test("the boundary is exclusive — exactly at the timeout is still alive") {
    isHalfOpen(hasSession = true, silentFor = 5.minutes) shouldBe false
    isHalfOpen(hasSession = true, silentFor = 5.minutes + 1.milli) shouldBe true
  }

  test("the timeout is injectable so a caller can tighten it without touching the rule") {
    isHalfOpen(hasSession = true, silentFor = 2.seconds, timeout = 1.second) shouldBe true
    isHalfOpen(hasSession = true, silentFor = 2.seconds, timeout = 10.seconds) shouldBe false
  }
}
