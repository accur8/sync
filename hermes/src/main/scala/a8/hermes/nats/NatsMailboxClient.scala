package a8.hermes.nats

import a8.hermes.core.{Mailbox, MailboxTransport, Uid}
import a8.hermes.core.Mailbox._
import a8.common.logging.Logging
import a8.shared.CompanionGen
import a8.shared.json.ast.{JsObj, JsVal, JsNothing, JsNull}
import a8.shared.SharedImports.jsonCodecOps
import a8.hermes.nats.MxNatsMailboxClient.MxMailboxKVData

import java.time.{Duration, Instant}
import java.io.Closeable
import java.util.concurrent.{Executors, TimeUnit}
import scala.util.Try

/**
 * NATS-specific mailbox client.
 *
 * Mailbox RECORDS live in postgres on the mesh server (mesh-sprint,
 * TASK-20260704-drop-nats-kv-postgres — the MeshMailboxesBy* KV buckets are gone).
 * Leaves — this client — reach them through the mesh server's records endpoints:
 * plain core-NATS request/reply with JSON payloads on `mesh.mbxstore.v1.*`
 * (godev `mesh/mailbox-records-remote.go` is the Go twin of this file).
 *
 * This is ONLY for direct NATS transport. For websocket/other transports,
 * use the RPC-based mailbox service client instead.
 *
 * WIRE-VISIBLE names that MUST match godev:
 * - endpoint subjects: mesh.mbxstore.v1.{fetch,insert,update,remove}
 * - record JSON: MailboxDto (godev mesh/mailbox.go) == MailboxKVData below
 * - message subjects: mesh.<ADDRESS>.<channel> (the address/write key — the
 *   capability-aligned naming; the adminKey appears in NO subject or stream name)
 * - stream names: mesh-<kind>-<READERKEY>-<channel>, kind = daemon | eph
 */
object NatsMailboxClient extends Logging {

  private val MbxStoreSubjectPrefix = "mesh.mbxstore.v1."
  private val MbxStoreTimeout = Duration.ofSeconds(10)

  // Timeouts matching godev
  private val NamedMailboxTimeoutMillis = 90L * 24 * 60 * 60 * 1000 // 90 days
  private val NonDurablePurgeTimeoutMillis = 15L * 60 * 1000 // 15 minutes

  // Retention backstop on eph rpc streams, matching godev TransientRpcMaxAge —
  // the eph streams are a wire, not storage.
  private val TransientRpcMaxAgeMillis = 60L * 60 * 1000 // 1 hour

  // Coarse lifecycle labels stored on the mailbox record (observability only; the
  // purge reads the timeout NUMBERS, not this label). Loosely aligned with godev's
  // LifecycleShortLivedCli / LifecycleLongLivedDaemon.
  val LifecycleShortLivedCli = "short-lived-cli"
  val LifecycleLongLivedDaemon = "long-lived-daemon"

  // A ping persists lastActivity only if the stored value is more than this old —
  // so a chatty pinger doesn't spam the store (matches godev's 5-min
  // AlivenessPingDebounce).
  private val TouchDebounceMillis = 5L * 60 * 1000 // 5 minutes

  // How often a live owner pings its own mailbox: shorter than its purge timeout so
  // a pinged mailbox never expires — min(5min, purgeTimeout/3), floored at 30s.
  def pingIntervalMillis(purgeTimeoutInMillis: Long): Long = {
    val derived = if (purgeTimeoutInMillis > 0) math.min(TouchDebounceMillis, purgeTimeoutInMillis / 3) else TouchDebounceMillis
    math.max(derived, 30_000L)
  }

  /**
   * shouldTouch is the pure debounce rule: persist only when the STORED
   * lastActivity is older than the debounce window. Package-visible for unit tests.
   */
  private[nats] def shouldTouch(storedLastActivity: Long, nowMillis: Long): Boolean =
    nowMillis - storedLastActivity >= TouchDebounceMillis

  /**
   * The mailbox record as it travels the mbxstore endpoints and rests in the pg
   * `record`-derived columns — godev's MailboxDto (camelCase JSON, both sides).
   */
  @CompanionGen
  case class MailboxKVData(
    adminKey: String,
    readerKey: String,
    address: String,
    created: Long,
    lastActivity: Long,
    purgeTimeoutInMillis: Long,
    closeTimeoutInMillis: Long,
    channels: List[String] = List("rpc-inbox"),
    isNamed: Boolean = false,
    mailboxKind: String = "", // coarse lifecycle label
    processRunUid: String = "", // owning processrun uid when known; empty otherwise
    // godev models these on the DTO; privateMetadata is freeform client data (the
    // auth identity now lives in dedicated columns server-side and is PRESERVED by
    // the server when an update omits it — see godev Mailbox.ApplyDto).
    publicMetadata: JsObj = JsObj.empty,
    privateMetadata: JsObj = JsObj.empty,
  )
  object MailboxKVData extends MxMailboxKVData

  // --- mbxstore endpoint client ---------------------------------------------

  /**
   * One request/reply against a mesh.mbxstore.v1.* endpoint. Reply envelope:
   * {"ok":bool, "error":string?, "data":...?}. Returns the data JsVal (JsNothing
   * when absent/null).
   */
  private def mbxRequest(natsTransport: NatsTransport, op: String, payloadJson: String): Try[JsVal] = Try {
    import a8.shared.json.parse
    import a8.shared.json.ast.{JsBool, JsStr}

    val reply = natsTransport.connection.request(
      MbxStoreSubjectPrefix + op,
      payloadJson.getBytes("UTF-8"),
      MbxStoreTimeout,
    )
    if (reply == null)
      throw new RuntimeException(s"mbxstore $op: no reply (is a mesh server running?)")

    val env = parse(new String(reply.getData, "UTF-8")) match {
      case Right(o: JsObj) => o
      case Right(_)        => throw new RuntimeException(s"mbxstore $op: reply is not a JSON object")
      case Left(err)       => throw new RuntimeException(s"mbxstore $op: unparseable reply: $err")
    }
    val ok = env.values.get("ok").collect { case JsBool(b) => b }.getOrElse(false)
    if (!ok) {
      val msg = env.values.get("error").collect { case JsStr(s) => s }.getOrElse("unknown error")
      throw new RuntimeException(s"mbxstore $op: $msg")
    }
    env.values.getOrElse("data", JsNothing)
  }

  private def readRecord(data: JsVal): Option[MailboxKVData] =
    data match {
      case JsNothing | JsNull => None
      case v =>
        MailboxKVData.jsonCodec.read(a8.shared.json.ast.JsDoc.jsDocRoot(v)) match {
          case Right(d)  => Some(d)
          case Left(err) => throw new RuntimeException(s"failed to read mailbox record: $err")
        }
    }

  /** fetch returns the record for a key, or None when absent. kind: admin|reader|address */
  private def fetchRecord(natsTransport: NatsTransport, kind: String, key: String): Try[Option[MailboxKVData]] = {
    val payload = JsObj(Map("kind" -> a8.shared.json.ast.JsStr(kind), "key" -> a8.shared.json.ast.JsStr(key))).compactJson
    mbxRequest(natsTransport, "fetch", payload).map(readRecord)
  }

  private def insertRecord(natsTransport: NatsTransport, data: MailboxKVData): Try[Unit] =
    mbxRequest(natsTransport, "insert", data.compactJson).map(_ => ())

  private def updateRecord(natsTransport: NatsTransport, data: MailboxKVData): Try[Unit] =
    mbxRequest(natsTransport, "update", data.compactJson).map(_ => ())

  // --- aliveness ------------------------------------------------------------

  /**
   * Refresh a mailbox's lastActivity — the sync-side "mailbox ping": fetch the
   * CURRENT record (so a concurrent server-side change — e.g. an identity bind —
   * is never clobbered), apply the debounce against the STORED lastActivity, and
   * update with only that field bumped. Returns true if it wrote, false if
   * debounced/absent.
   */
  private def touchMailbox(adminKey: AdminKey, natsTransport: NatsTransport, nowMillis: Long): Try[Boolean] =
    fetchRecord(natsTransport, "admin", adminKey.value).flatMap {
      case None =>
        Try(false) // record gone (already purged) — nothing to refresh
      case Some(stored) if !shouldTouch(stored.lastActivity, nowMillis) =>
        Try(false)
      case Some(stored) =>
        updateRecord(natsTransport, stored.copy(lastActivity = nowMillis)).map(_ => true)
    }

  /**
   * Start a background pinger that keeps `adminKey`'s mailbox alive by refreshing
   * its lastActivity at the derived interval (shorter than its purge timeout).
   * Returns a Closeable that stops the loop (call it when the mailbox is released).
   */
  def startMailboxPingLoop(adminKey: AdminKey, natsTransport: NatsTransport, purgeTimeoutInMillis: Long): Closeable = {
    val intervalMillis = pingIntervalMillis(purgeTimeoutInMillis)
    val exec = Executors.newSingleThreadScheduledExecutor { r =>
      val t = new Thread(r, s"mailbox-ping-${adminKey.value.take(8)}")
      t.setDaemon(true)
      t
    }
    val task = new Runnable {
      override def run(): Unit =
        touchMailbox(adminKey, natsTransport, System.currentTimeMillis()) match {
          case scala.util.Failure(e) => logger.warn(s"mailbox ping failed for ${adminKey.value}", e)
          case _                     => ()
        }
    }
    exec.scheduleAtFixedRate(task, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS)
    () => exec.shutdownNow()
  }

  // --- construction ---------------------------------------------------------

  /**
   * Generate a random key with double prefix (matching godev)
   * e.g., randomKey("a") -> "aa" + uid32
   */
  private def randomKey(prefix: String): String = {
    s"$prefix$prefix${Uid.uid32()}"
  }

  private def mailboxFromData(
    data: MailboxKVData,
    natsTransport: NatsTransport,
  )(using ctx: a8.shared.app.Ctx): Mailbox = {
    val lifecycle = if (data.isNamed) {
      LifecycleType.Named(data.address)
    } else {
      LifecycleType.Ephemeral
    }

    val metadata = MailboxMetadata(
      adminKey = AdminKey(data.adminKey),
      readerKey = ReaderKey(data.readerKey),
      address = MailboxAddress(data.address),
      lifecycle = lifecycle,
      createdAt = Instant.ofEpochMilli(data.created),
      expiresAt = Instant.ofEpochMilli(data.created + data.purgeTimeoutInMillis),
      lastAccessedAt = Instant.ofEpochMilli(data.lastActivity),
    )

    val touchFn: () => Unit = () => {
      touchMailbox(AdminKey(data.adminKey), natsTransport, System.currentTimeMillis()) match {
        case scala.util.Failure(e) => logger.warn(s"mailbox touch failed for ${data.adminKey}", e)
        case _                     => ()
      }
    }

    new a8.hermes.bootstrap.SimpleMailbox(metadata, natsTransport, touchFn)
  }

  /**
   * Fetch or create a named mailbox.
   * Named mailboxes use the provided address and have long timeouts (90 days).
   */
  def fetchOrCreateNamedMailbox(
    address: MailboxAddress,
    natsTransport: NatsTransport,
  )(using ctx: a8.shared.app.Ctx): Try[Mailbox] = {
    fetchRecord(natsTransport, "address", address.value).map {
      case Some(data) =>
        logger.info(s"Found existing named mailbox with address: ${address.value}")
        mailboxFromData(data, natsTransport)
      case None =>
        logger.info(s"Creating new named mailbox: ${address.value}")
        createMailboxImpl(
          address = Some(address),
          purgeTimeoutMillis = NamedMailboxTimeoutMillis,
          closeTimeoutMillis = NamedMailboxTimeoutMillis,
          isNamed = true,
          mailboxKind = LifecycleLongLivedDaemon,
          processRunUid = "", // named/durable mailbox; no processrun link
          natsTransport = natsTransport,
        )(using ctx)
    }
  }

  /**
   * Resolve a coarse lifecycle kind to concrete purge/close timeout millis + a label.
   * Local to sync (loosely aligned with godev); the reader uses the stored numbers, so
   * this mapping is not a wire contract. An anonymous long-lived daemon still gets the
   * long timeout even without a named address.
   */
  private def resolveLifecycleTimeouts(kind: String): (Long, Long, String) =
    kind match {
      case LifecycleLongLivedDaemon => (NamedMailboxTimeoutMillis, NamedMailboxTimeoutMillis, LifecycleLongLivedDaemon)
      case _                        => (NonDurablePurgeTimeoutMillis, NonDurablePurgeTimeoutMillis, LifecycleShortLivedCli)
    }

  /**
   * Create a non-durable / anonymous mailbox. The coarse `lifecycleKind`
   * (short-lived-cli by default, or long-lived-daemon) resolves to concrete stored
   * timeouts — a long-lived daemon that has no named address still gets the long
   * purge window so its pinger keeps it alive rather than being reaped at 15 minutes.
   */
  def createNonDurableMailbox(
    natsTransport: NatsTransport,
    lifecycleKind: String = LifecycleShortLivedCli,
    processRunUid: String = "", // owning processrun uid when the creator has one; empty otherwise
  )(using ctx: a8.shared.app.Ctx): Try[Mailbox] = Try {
    val (purge, close, label) = resolveLifecycleTimeouts(lifecycleKind)
    logger.info(s"Creating mailbox kind=$label")
    createMailboxImpl(
      address = None,
      purgeTimeoutMillis = purge,
      closeTimeoutMillis = close,
      isNamed = false,
      mailboxKind = label,
      processRunUid = processRunUid,
      natsTransport = natsTransport,
    )(using ctx)
  }

  /**
   * Create mailbox implementation (matching godev's _CreateMailboxImpl): generate
   * keys, dup-check, insert the record via the mbxstore endpoint, and create the
   * rpc-inbox stream under the capability-aligned naming.
   */
  private def createMailboxImpl(
    address: Option[MailboxAddress],
    purgeTimeoutMillis: Long,
    closeTimeoutMillis: Long,
    isNamed: Boolean,
    mailboxKind: String,
    processRunUid: String,
    natsTransport: NatsTransport,
  )(using ctx: a8.shared.app.Ctx): Mailbox = {
    // Generate keys (matching godev)
    val mailboxAddress = address.getOrElse(MailboxAddress(randomKey("a")))
    val readerKey = ReaderKey(randomKey("r"))
    val adminKey = AdminKey(randomKey("z"))

    logger.debug(s"Generated keys: address=${mailboxAddress.value}, readerKey=${readerKey.value}, adminKey=${adminKey.value}")

    // Dup checks (should never trip with random UIDs; the pg unique indexes are
    // the real backstop).
    def exists(kind: String, key: String): Boolean =
      fetchRecord(natsTransport, kind, key).get.isDefined
    if (exists("address", mailboxAddress.value))
      throw new RuntimeException(s"duplicate address - ${mailboxAddress.value}")
    if (exists("reader", readerKey.value))
      throw new RuntimeException("duplicate readerkey")
    if (exists("admin", adminKey.value))
      throw new RuntimeException("duplicate adminkey")

    val now = System.currentTimeMillis()

    val mailboxData = MailboxKVData(
      adminKey = adminKey.value,
      readerKey = readerKey.value,
      address = mailboxAddress.value,
      created = now,
      lastActivity = now,
      purgeTimeoutInMillis = purgeTimeoutMillis,
      closeTimeoutInMillis = closeTimeoutMillis,
      isNamed = isNamed,
      mailboxKind = mailboxKind,
      processRunUid = processRunUid,
    )

    insertRecord(natsTransport, mailboxData).get

    logger.info(s"Created mailbox: ${mailboxAddress.value} (named=$isNamed)")

    // Create the rpc-inbox stream (capability-aligned naming, matching godev
    // Mailbox.NatsSubject/NatsStreamName): the SUBJECT carries the address (write
    // key), the STREAM NAME carries kind + reader key; the adminKey appears in
    // neither. Eph streams get the 1h MaxAge backstop; daemon streams keep their
    // purge-timeout horizon.
    val kindToken = if (isNamed) "daemon" else "eph"
    val rpcInboxSubject = s"mesh.${mailboxAddress.value}.rpc-inbox"
    val rpcInboxStream = s"mesh-$kindToken-${readerKey.value}-rpc-inbox"
    val maxAgeMillis = if (isNamed) purgeTimeoutMillis else TransientRpcMaxAgeMillis
    natsTransport.createStream(
      name = rpcInboxStream,
      subjects = Seq(rpcInboxSubject),
      retention = MailboxTransport.StreamRetention.Limits,
      maxAge = scala.concurrent.duration.FiniteDuration(maxAgeMillis, "milliseconds"),
    )
    logger.debug(s"created channel rpc-inbox in ${mailboxAddress.value} (stream $rpcInboxStream)")

    mailboxFromData(mailboxData, natsTransport)
  }

}
