package a8.hermes.nats

import a8.hermes.core.{Mailbox, MailboxTransport, Uid}
import a8.hermes.core.Mailbox._
import a8.common.logging.Logging
import a8.shared.CompanionGen
import a8.hermes.nats.MxNatsMailboxClient.MxMailboxKVData
import io.nats.client.api.{KeyValueConfiguration, StorageType}
import io.nats.client.{JetStream, KeyValue}

import java.time.Instant
import scala.util.Try
import scala.jdk.CollectionConverters._

/**
 * NATS-specific mailbox client.
 *
 * Creates and fetches mailboxes using NATS KV stores, following godev's pattern:
 * - HermesMailboxesByAdminKey: adminKey → full mailbox JSON
 * - HermesMailboxesByRWKeys: (address|readerKey) → adminKey
 *
 * This is ONLY for direct NATS transport. For websocket/other transports,
 * use the RPC-based mailbox service client instead.
 */
object NatsMailboxClient extends Logging {

  private val AdminMailboxesKVBucket = "HermesMailboxesByAdminKey"
  private val RWKeysKVBucket = "HermesMailboxesByRWKeys"

  // Timeouts matching godev
  private val NamedMailboxTimeoutMillis = 90L * 24 * 60 * 60 * 1000 // 90 days
  private val DefaultPurgeTimeoutMillis = 24L * 60 * 60 * 1000 // 24 hours
  private val NonDurablePurgeTimeoutMillis = 15L * 60 * 1000 // 15 minutes

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
   * Parse mailbox JSON and reconstruct Mailbox instance
   */
  private def parseMailboxJson(
    json: String,
    adminKey: AdminKey,
    natsTransport: NatsTransport,
    rwKV: KeyValue,
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

    new a8.hermes.bootstrap.SimpleMailbox(metadata, natsTransport, lookupAdminKey)
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
              parseMailboxJson(json, AdminKey(adminKeyValue), natsTransport, rwKV) match {
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
          adminKV = adminKV,
          rwKV = rwKV,
          natsTransport = natsTransport,
        )(using ctx)
      }
    }
  }

  /**
   * Create a non-durable mailbox (for CLI apps with short lifetime)
   */
  def createNonDurableMailbox(
    natsTransport: NatsTransport,
  )(using ctx: a8.shared.app.Ctx): Try[Mailbox] = {
    for {
      adminKV <- getOrCreateKV(natsTransport, AdminMailboxesKVBucket)
      rwKV <- getOrCreateKV(natsTransport, RWKeysKVBucket)
    } yield {
      logger.info("Creating non-durable mailbox")
      createMailboxImpl(
        address = None,
        purgeTimeoutMillis = NonDurablePurgeTimeoutMillis,
        closeTimeoutMillis = NonDurablePurgeTimeoutMillis,
        isNamed = false,
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
      "isNamed": ${mailboxData.isNamed}
    }"""

    // Store in KV (following godev's triple-index pattern)
    rwKV.put(readerKey.value, adminKey.value.getBytes("UTF-8"))
    rwKV.put(mailboxAddress.value, adminKey.value.getBytes("UTF-8"))
    adminKV.put(adminKey.value, json.getBytes("UTF-8"))

    logger.info(s"Created mailbox: ${mailboxAddress.value} (named=$isNamed)")

    // Create JetStream streams for mailbox channels (matching godev)
    val rpcInboxSubject = s"hermes.${adminKey.value}.rpc-inbox"
    val rpcInboxStream = s"hermes-${adminKey.value}-rpc-inbox"
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

    new a8.hermes.bootstrap.SimpleMailbox(metadata, natsTransport, lookupAdminKey)
  }

}
