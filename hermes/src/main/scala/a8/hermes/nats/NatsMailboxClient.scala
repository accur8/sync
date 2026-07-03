package a8.hermes.nats

import a8.hermes.core.{Mailbox, MailboxTransport, Uid}
import a8.hermes.core.Mailbox._
import a8.common.logging.Logging
import a8.shared.CompanionGen
import a8.hermes.nats.MxNatsMailboxClient.MxMailboxKVData
import io.nats.client.api.{KeyValueConfiguration, StorageType}
import io.nats.client.{JetStream, KeyValue}

import java.time.Instant
import java.io.Closeable
import java.util.concurrent.{Executors, TimeUnit}
import scala.util.Try
import scala.jdk.CollectionConverters._

/**
 * NATS-specific mailbox client.
 *
 * Creates and fetches mailboxes using NATS KV stores, following godev's pattern:
 * - MeshMailboxesByAdminKey: adminKey → full mailbox JSON
 * - MeshMailboxesByRWKeys: (address|readerKey) → adminKey
 *
 * This is ONLY for direct NATS transport. For websocket/other transports,
 * use the RPC-based mailbox service client instead.
 *
 * NOTE: the bucket names + subject prefix below are WIRE-VISIBLE and MUST match godev's
 * renamed mesh transport (godev `mesh/mailbox-store.go` MeshMailboxesBy*, `mesh/mailbox.go`
 * "mesh.%s.%s" / "mesh-%s-%s"). The godev hermes→mesh rename (20260620) renamed these on the
 * wire; this is the sync-side half of that rename so the checkpoint worker can reach the mesh.
 */
object NatsMailboxClient extends Logging {

  private val AdminMailboxesKVBucket = "MeshMailboxesByAdminKey"
  private val RWKeysKVBucket = "MeshMailboxesByRWKeys"

  // Timeouts matching godev
  private val NamedMailboxTimeoutMillis = 90L * 24 * 60 * 60 * 1000 // 90 days
  private val DefaultPurgeTimeoutMillis = 24L * 60 * 60 * 1000 // 24 hours
  private val NonDurablePurgeTimeoutMillis = 15L * 60 * 1000 // 15 minutes

  // Coarse lifecycle labels stored in the mailbox KV (observability only; the purge
  // reads the timeout NUMBERS, not this label). Local to the sync side — loosely
  // aligned with godev's LifecycleShortLivedCli / LifecycleLongLivedDaemon, not a
  // wire contract.
  val LifecycleShortLivedCli = "short-lived-cli"
  val LifecycleLongLivedDaemon = "long-lived-daemon"

  // A ping bumps lastActivity in the KV only if the last write was more than this
  // long ago — so a chatty pinger doesn't spam the KV store (matches godev's 5-min
  // AlivenessPingDebounce).
  private val TouchDebounceMillis = 5L * 60 * 1000 // 5 minutes

  // How often a live owner pings its own mailbox: shorter than its purge timeout so
  // a pinged mailbox never expires — min(5min, purgeTimeout/3), floored at 30s.
  def pingIntervalMillis(purgeTimeoutInMillis: Long): Long = {
    val derived = if (purgeTimeoutInMillis > 0) math.min(TouchDebounceMillis, purgeTimeoutInMillis / 3) else TouchDebounceMillis
    math.max(derived, 30_000L)
  }

  /**
   * Mailbox data as stored in NATS KV (matching godev's JSON structure)
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
    mailboxKind: String = "", // coarse lifecycle label; default keeps legacy KV records readable
    processRunUid: String = "", // owning processrun uid when known; empty otherwise. Default keeps legacy records readable.
  )
  object MailboxKVData extends MxMailboxKVData

  /**
   * Get or create NATS KV bucket
   */
  private def getOrCreateKV(natsTransport: NatsTransport, bucketName: String): Try[KeyValue] = {
    val connection = natsTransport.connection
    val kvm = connection.keyValueManagement()

    // Try to get existing KV bucket
    Try {
      connection.keyValue(bucketName)
    }.recoverWith { case _ =>
      // Create if it doesn't exist
      Try {
        logger.info(s"Creating NATS KV bucket: $bucketName")
        val config = KeyValueConfiguration.builder()
          .name(bucketName)
          .storageType(StorageType.File)
          .build()
        kvm.create(config) // Returns KeyValueStatus
        connection.keyValue(bucketName) // Get the actual KeyValue object
      }
    }
  }

  /**
   * Generate a random key with double prefix (matching godev)
   * e.g., randomKey("a") -> "aa" + uid32
   */
  private def randomKey(prefix: String): String = {
    s"$prefix$prefix${Uid.uid32()}"
  }

  /**
   * Refresh a mailbox's lastActivity in the KV store — the sync-side "mailbox ping".
   *
   * CRITICAL: this is a RAW JSON PATCH, not a MailboxKVData re-marshal. The Scala
   * case class is a lossy subset (it does not model publicMetadata / privateMetadata),
   * and privateMetadata carries the bound SSH identity the SQL firewall depends on. A
   * re-marshal would WIPE that identity. So we parse the stored JSON, replace ONLY the
   * lastActivity field, and put it back — every other key survives byte-for-byte.
   *
   * Debounced: only writes when more than TouchDebounceMillis has elapsed since the
   * STORED lastActivity, so a chatty pinger doesn't spam the KV store. Returns true if
   * it wrote, false if debounced/absent.
   */
  /**
   * The pure raw-JSON-patch: given the stored mailbox JSON and now, return the new JSON
   * with ONLY lastActivity replaced (every other key — including privateMetadata —
   * preserved byte-for-byte), or None if the debounce window has not elapsed since the
   * stored lastActivity. Package-visible so the identity-preservation invariant is unit
   * testable without a live NATS KV.
   */
  private[nats] def patchLastActivity(json: String, nowMillis: Long): Option[String] = {
    import a8.shared.json.parse
    import a8.shared.json.ast.{JsObj, JsNum}
    import a8.shared.SharedImports.jsonCodecOps

    val obj = parse(json) match {
      case Right(o: JsObj) => o
      case Right(_)        => throw new RuntimeException("mailbox KV is not a JSON object")
      case Left(err)       => throw new RuntimeException(s"failed to parse mailbox KV: $err")
    }
    val storedLastActivity = obj.values.get("lastActivity").collect { case JsNum(v) => v.toLong }.getOrElse(0L)
    if (nowMillis - storedLastActivity < TouchDebounceMillis) {
      None // still fresh — skip the KV write
    } else {
      Some(JsObj(obj.values + ("lastActivity" -> JsNum(BigDecimal(nowMillis)))).compactJson)
    }
  }

  private def touchMailbox(adminKey: AdminKey, adminKV: KeyValue, nowMillis: Long): Try[Boolean] = Try {
    Option(adminKV.get(adminKey.value)) match {
      case None =>
        false // mailbox record gone (already purged) — nothing to refresh
      case Some(e) =>
        patchLastActivity(new String(e.getValue, "UTF-8"), nowMillis) match {
          case Some(newJson) =>
            adminKV.put(adminKey.value, newJson.getBytes("UTF-8"))
            true
          case None =>
            false
        }
    }
  }

  /** The admin-key KV bucket handle, for the bootstrap pinger. */
  def getOrCreateKVForPinger(natsTransport: NatsTransport): Try[KeyValue] =
    getOrCreateKV(natsTransport, AdminMailboxesKVBucket)

  /**
   * Start a background pinger that keeps `adminKey`'s mailbox alive by refreshing its
   * lastActivity at the derived interval (shorter than its purge timeout). Mirrors
   * [[a8.hermes.continuum.ContinuumRunnerClient.startPingLoop]]. Returns a Closeable
   * that stops the loop (call it when the mailbox is released).
   */
  def startMailboxPingLoop(adminKey: AdminKey, adminKV: KeyValue, purgeTimeoutInMillis: Long): Closeable = {
    val intervalMillis = pingIntervalMillis(purgeTimeoutInMillis)
    val exec = Executors.newSingleThreadScheduledExecutor { r =>
      val t = new Thread(r, s"mailbox-ping-${adminKey.value.take(8)}")
      t.setDaemon(true)
      t
    }
    val task = new Runnable {
      override def run(): Unit =
        touchMailbox(adminKey, adminKV, System.currentTimeMillis()) match {
          case scala.util.Failure(e) => logger.warn(s"mailbox ping failed for ${adminKey.value}", e)
          case _                     => ()
        }
    }
    exec.scheduleAtFixedRate(task, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS)
    () => exec.shutdownNow()
  }

  /**
   * Parse mailbox JSON and reconstruct Mailbox instance
   */
  private def parseMailboxJson(
    json: String,
    adminKey: AdminKey,
    natsTransport: NatsTransport,
    rwKV: KeyValue,
    adminKV: KeyValue,
  )(using ctx: a8.shared.app.Ctx): Try[Mailbox] = Try {
    import a8.shared.json.{parse, ast}

    val jsval = parse(json) match {
      case Right(v) => v
      case Left(err) => throw new RuntimeException(s"Failed to parse JSON: $err")
    }
    val jsdoc = ast.JsDoc.jsDocRoot(jsval)
    val data = MailboxKVData.jsonCodec.read(jsdoc) match {
      case Right(d) => d
      case Left(err) => throw new RuntimeException(s"Failed to read MailboxKVData: $err")
    }

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

    // Create lookup function for resolving adminKeys from addresses
    val lookupAdminKey: MailboxAddress => Option[AdminKey] = { address =>
      // Look up address → adminKey in RWKeysKV
      Try(rwKV.get(address.value)).toOption
        .flatMap(Option(_))
        .map(entry => AdminKey(new String(entry.getValue, "UTF-8")))
    }

    val touchFn: () => Unit = () => {
      touchMailbox(AdminKey(data.adminKey), adminKV, System.currentTimeMillis()) match {
        case scala.util.Failure(e) => logger.warn(s"mailbox touch failed for ${data.adminKey}", e)
        case _                     => ()
      }
    }

    new a8.hermes.bootstrap.SimpleMailbox(metadata, natsTransport, lookupAdminKey, touchFn)
  }

  /**
   * Fetch or create a named mailbox.
   * Named mailboxes use the provided address and have long timeouts (90 days).
   */
  def fetchOrCreateNamedMailbox(
    address: MailboxAddress,
    natsTransport: NatsTransport,
  )(using ctx: a8.shared.app.Ctx): Try[Mailbox] = {
    for {
      adminKV <- getOrCreateKV(natsTransport, AdminMailboxesKVBucket)
      rwKV <- getOrCreateKV(natsTransport, RWKeysKVBucket)
    } yield {
      // Try to fetch existing mailbox first
      val existingMailbox = Try(rwKV.get(address.value)).toOption
        .flatMap(Option(_))
        .flatMap { entry =>
          val adminKeyValue = new String(entry.getValue, "UTF-8")
          logger.info(s"Found existing named mailbox with address: ${address.value}, adminKey: $adminKeyValue")

          // Fetch full mailbox data from adminKV
          Try(adminKV.get(adminKeyValue)).toOption
            .flatMap(Option(_))
            .flatMap { adminEntry =>
              val json = new String(adminEntry.getValue, "UTF-8")
              parseMailboxJson(json, AdminKey(adminKeyValue), natsTransport, rwKV, adminKV) match {
                case scala.util.Success(mailbox) =>
                  logger.info(s"Successfully reconstructed existing mailbox: ${address.value}")
                  Some(mailbox)
                case scala.util.Failure(e) =>
                  logger.error(s"Failed to parse mailbox JSON for ${address.value}: ${e.getMessage}", e)
                  None
              }
            }
        }

      existingMailbox.getOrElse {
        // Create new mailbox if it doesn't exist
        logger.info(s"Creating new named mailbox: ${address.value}")
        createMailboxImpl(
          address = Some(address),
          purgeTimeoutMillis = NamedMailboxTimeoutMillis,
          closeTimeoutMillis = NamedMailboxTimeoutMillis,
          isNamed = true,
          mailboxKind = LifecycleLongLivedDaemon,
          processRunUid = "", // named/durable mailbox; no processrun link
          adminKV = adminKV,
          rwKV = rwKV,
          natsTransport = natsTransport,
        )(using ctx)
      }
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
  )(using ctx: a8.shared.app.Ctx): Try[Mailbox] = {
    for {
      adminKV <- getOrCreateKV(natsTransport, AdminMailboxesKVBucket)
      rwKV <- getOrCreateKV(natsTransport, RWKeysKVBucket)
    } yield {
      val (purge, close, label) = resolveLifecycleTimeouts(lifecycleKind)
      logger.info(s"Creating mailbox kind=$label")
      createMailboxImpl(
        address = None,
        purgeTimeoutMillis = purge,
        closeTimeoutMillis = close,
        isNamed = false,
        mailboxKind = label,
        processRunUid = processRunUid,
        adminKV = adminKV,
        rwKV = rwKV,
        natsTransport = natsTransport,
      )(using ctx)
    }
  }

  /**
   * Create mailbox implementation (matching godev's _CreateMailboxImpl)
   */
  private def createMailboxImpl(
    address: Option[MailboxAddress],
    purgeTimeoutMillis: Long,
    closeTimeoutMillis: Long,
    isNamed: Boolean,
    mailboxKind: String,
    processRunUid: String,
    adminKV: KeyValue,
    rwKV: KeyValue,
    natsTransport: NatsTransport,
  )(using ctx: a8.shared.app.Ctx): Mailbox = {
    // Generate keys (matching godev)
    val mailboxAddress = address.getOrElse(MailboxAddress(randomKey("a")))
    val readerKey = ReaderKey(randomKey("r"))
    val adminKey = AdminKey(randomKey("z"))

    logger.debug(s"Generated keys: address=${mailboxAddress.value}, readerKey=${readerKey.value}, adminKey=${adminKey.value}")

    // Check for duplicates (should never happen with random UIDs, but just in case)
    // Note: NATS KV returns null when key doesn't exist, so we need to filter nulls
    val addrExists = Try(rwKV.get(mailboxAddress.value)).toOption.flatMap(Option(_))
    if (addrExists.isDefined) {
      val existingValue = new String(addrExists.get.getValue, "UTF-8")
      throw new RuntimeException(s"duplicate address - ${mailboxAddress.value}")
    }

    val rKeyExists = Try(rwKV.get(readerKey.value)).toOption.flatMap(Option(_))
    if (rKeyExists.isDefined) {
      throw new RuntimeException(s"duplicate readerkey")
    }

    val aKeyExists = Try(adminKV.get(adminKey.value)).toOption.flatMap(Option(_))
    if (aKeyExists.isDefined) {
      throw new RuntimeException(s"duplicate adminkey")
    }

    val now = System.currentTimeMillis()

    // Create mailbox data
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

    // Serialize to JSON (simple manual serialization for now)
    val json = s"""{
      "adminKey": "${mailboxData.adminKey}",
      "readerKey": "${mailboxData.readerKey}",
      "address": "${mailboxData.address}",
      "created": ${mailboxData.created},
      "lastActivity": ${mailboxData.lastActivity},
      "purgeTimeoutInMillis": ${mailboxData.purgeTimeoutInMillis},
      "closeTimeoutInMillis": ${mailboxData.closeTimeoutInMillis},
      "publicMetadata": {},
      "privateMetadata": {},
      "channels": [${mailboxData.channels.map(c => s""""$c"""").mkString(", ")}],
      "isNamed": ${mailboxData.isNamed},
      "mailboxKind": "${mailboxData.mailboxKind}",
      "processRunUid": "${mailboxData.processRunUid}"
    }"""

    // Store in KV (following godev's triple-index pattern)
    rwKV.put(readerKey.value, adminKey.value.getBytes("UTF-8"))
    rwKV.put(mailboxAddress.value, adminKey.value.getBytes("UTF-8"))
    adminKV.put(adminKey.value, json.getBytes("UTF-8"))

    logger.info(s"Created mailbox: ${mailboxAddress.value} (named=$isNamed)")

    // Create JetStream streams for mailbox channels (matching godev)
    val rpcInboxSubject = s"mesh.${adminKey.value}.rpc-inbox"
    val rpcInboxStream = s"mesh-${adminKey.value}-rpc-inbox"
    natsTransport.createStream(
      name = rpcInboxStream,
      subjects = Seq(rpcInboxSubject),
      retention = MailboxTransport.StreamRetention.WorkQueue,
      maxAge = scala.concurrent.duration.FiniteDuration(purgeTimeoutMillis, "milliseconds"),
    )
    logger.debug(s"created channel rpc-inbox in ${adminKey.value}")

    // Create the Mailbox instance
    val lifecycle = if (isNamed) {
      LifecycleType.Named(mailboxAddress.value)
    } else {
      LifecycleType.Ephemeral
    }

    val metadata = MailboxMetadata(
      adminKey = adminKey,
      readerKey = readerKey,
      address = mailboxAddress,
      lifecycle = lifecycle,
      createdAt = Instant.ofEpochMilli(now),
      expiresAt = Instant.ofEpochMilli(now + purgeTimeoutMillis),
      lastAccessedAt = Instant.ofEpochMilli(now),
    )

    // Create lookup function for resolving adminKeys from addresses
    val lookupAdminKey: MailboxAddress => Option[AdminKey] = { address =>
      // Look up address → adminKey in RWKeysKV
      Try(rwKV.get(address.value)).toOption
        .flatMap(Option(_))
        .map(entry => AdminKey(new String(entry.getValue, "UTF-8")))
    }

    val touchFn: () => Unit = () => {
      touchMailbox(adminKey, adminKV, System.currentTimeMillis()) match {
        case scala.util.Failure(e) => logger.warn(s"mailbox touch failed for ${adminKey.value}", e)
        case _                     => ()
      }
    }

    new a8.hermes.bootstrap.SimpleMailbox(metadata, natsTransport, lookupAdminKey, touchFn)
  }

}
