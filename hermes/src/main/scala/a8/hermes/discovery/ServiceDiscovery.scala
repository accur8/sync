package a8.hermes.discovery

import a8.hermes.core.{Mailbox, MailboxTransport}
import a8.hermes.core.MailboxTransport.Envelope
import a8.hermes.rpc.{RpcSchema, RpcServer}
import a8.hermes.bootstrap.StaticServiceDiscovery
import a8.shared.app.Ctx
import a8.common.logging.Logging
import a8.shared.zreplace.Resource
import a8.shared.json
import a8.shared.json.ast.{JsObj, JsStr, JsArr, JsVal, JsNum, JsBool}
import a8.shared.CompanionGen
import a8.shared.SharedImports.jsonCodecOps

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.{FiniteDuration, *}
import java.time.Instant

// ===== Serialization Format =====

/**
 * SerializationFormat defines how RPC messages are encoded
 */
enum SerializationFormat(val value: Int) {
  case UNSPECIFIED extends SerializationFormat(0)
  case JSON extends SerializationFormat(1)
  case PROTOBUF extends SerializationFormat(2)
}

object SerializationFormat {
  // JSON codec - serializes as lowercase strings ("json", "protobuf")
  implicit lazy val jsonCodec: json.JsonTypedCodec[SerializationFormat, json.ast.JsStr] = {
    json.JsonCodec.string.dimap[SerializationFormat](
      s => s.toLowerCase match {
        case "unspecified" => UNSPECIFIED
        case "json" => JSON
        case "protobuf" => PROTOBUF
        case _ => UNSPECIFIED
      },
      format => format match {
        case UNSPECIFIED => "unspecified"
        case JSON => "json"
        case PROTOBUF => "protobuf"
      }
    )
  }
}

// ===== Message Types (matching godev) =====

/**
 * DiscoveryRequest - published to nefario.discovery subject
 */
@CompanionGen(jsonCodec = true)
case class DiscoveryRequest(
  request_id: String,
  reply_suffix: String,
  timestamp: Instant,
  query: DiscoveryQuery,
)

object DiscoveryRequest extends MxServiceDiscovery.MxDiscoveryRequest

/**
 * DiscoveryResponse - published to nefario.discovery.response.{reply_suffix}
 * NOTE: processUid and unixPid use camelCase to match godev's JSON tags
 */
@CompanionGen(jsonCodec = true)
case class DiscoveryResponse(
  request_id: String,
  processUid: String = "",  // From PROCESS_UID env var (camelCase to match godev JSON!)
  unixPid: Int,              // camelCase to match godev JSON!
  app_name: String,
  mailbox_address: String,
  service_name: String = "",  // From SERVICE_NAME env var (empty if not set)
  codebase_name: String = "sync-scala",
  timestamp: Instant,
  capabilities: ProcessCapabilities,
  metadata: Map[String, String],
  extended_metadata: Option[Map[String, String]],
  service_discovery_mapping: Map[String, String],
)

object DiscoveryResponse extends MxServiceDiscovery.MxDiscoveryResponse

/**
 * DiscoveryQuery - filters for finding services
 */
@CompanionGen(jsonCodec = true)
case class DiscoveryQuery(
  implements_rpc: Iterable[String] = Iterable.empty,
  app_name: Option[String] = None,
  service_name: Option[String] = None,
  location: Option[LocationQuery] = None,
  include_extended_metadata: Boolean = false,
  include_schema_details: Boolean = false,
)

object DiscoveryQuery extends MxServiceDiscovery.MxDiscoveryQuery

/**
 * LocationQuery - filters by deployment location
 */
@CompanionGen(jsonCodec = true)
case class LocationQuery(
  server: Option[String] = None,
  user: Option[String] = None,
  user_server: Option[String] = None,
)

object LocationQuery extends MxServiceDiscovery.MxLocationQuery

/**
 * RpcMethod represents a single method within an RPC schema
 */
@CompanionGen(jsonCodec = true)
case class RpcMethod(
  name: String,
  formats: Iterable[SerializationFormat] = Iterable.empty,
  request_type: String = "",
  response_type: String = "",
  description: String = "",
)

object RpcMethod extends MxServiceDiscovery.MxRpcMethod

/**
 * RpcSchemaInfo represents a versioned collection of RPC methods
 */
@CompanionGen(jsonCodec = true)
case class RpcSchemaInfo(
  name: String,
  methods: Iterable[RpcMethod] = Iterable.empty,
  default_formats: Iterable[SerializationFormat] = Iterable.empty,
  schema_version: Int = 0,
  description: String = "",
)

object RpcSchemaInfo extends MxServiceDiscovery.MxRpcSchemaInfo

/**
 * ProcessCapabilities - what a process can do
 */
@CompanionGen(jsonCodec = true)
case class ProcessCapabilities(
  implements_rpc: Iterable[String],
  rpc_schemas: Map[String, Iterable[String]],
  rpc_schema_details: Iterable[RpcSchemaInfo] = Iterable.empty,  // Detailed schema info (only when requested)
  location: ProcessLocation,
  engines: Iterable[String] = Iterable.empty,
)

object ProcessCapabilities extends MxServiceDiscovery.MxProcessCapabilities

/**
 * ProcessLocation - where a process is running
 */
@CompanionGen(jsonCodec = true)
case class ProcessLocation(
  server: String,
  user: String,
  ip_addresses: Iterable[String] = Iterable.empty,
)

object ProcessLocation extends MxServiceDiscovery.MxProcessLocation

// ===== Service Discovery Configuration =====

case class ServiceDiscoveryConfig(
  mailbox: Mailbox,
  transport: MailboxTransport,
  rpcServer: Option[RpcServer] = None,
  discoverySubject: String = "nefario.discovery",
  appName: String,
  serviceName: Option[String] = None,
  location: ProcessLocation,
  metadata: Map[String, String] = Map.empty,
  extendedMetadata: Map[String, String] = Map.empty,
  staticServiceDiscovery: Option[StaticServiceDiscovery] = None,
)

/**
 * Service discovery for Hermes/NATS - ALIGNED WITH GODEV PROTOCOL.
 *
 * Uses pub/sub on nefario.discovery subject with JSON messages matching
 * /Users/glen/code/accur8/godev/pkg/discovery/messages.go exactly.
 *
 * This enables Go services and Scala services to discover each other.
 */
object ServiceDiscovery extends Logging {

  // Type alias for convenience
  type Config = ServiceDiscoveryConfig

  // ===== Utility Methods =====

  /**
   * Get all non-localhost IPv4 addresses (matching godev's GetNonLocalhostIPs)
   */
  def getNonLocalhostIPs(): Iterable[String] = {
    import java.net.NetworkInterface
    import scala.jdk.CollectionConverters.*

    try {
      NetworkInterface.getNetworkInterfaces.asScala
        .flatMap(_.getInetAddresses.asScala)
        .filter(addr => addr.getAddress.length == 4) // IPv4 only
        .filterNot(_.isLoopbackAddress)
        .map(_.getHostAddress)
        .to(Iterable)
    } catch {
      case e: Exception =>
        logger.warn(s"Failed to get non-localhost IPs: ${e.getMessage}")
        Iterable.empty
    }
  }

  /**
   * Read all A8_* prefixed environment variables and return as metadata map.
   * Automatically discovers all env vars starting with A8_ prefix.
   */
  def readA8EnvironmentMetadata(): Map[String, String] = {
    sys.env
      .filter(_._1.startsWith("A8_"))
      .toMap
  }

  /**
   * Create discovery config with sensible defaults
   */
  def defaultConfig(
    mailbox: Mailbox,
    transport: MailboxTransport,
    rpcServer: Option[RpcServer],
    appName: String,
    serviceName: Option[String],
    staticServiceDiscovery: Option[StaticServiceDiscovery] = None,
  ): ServiceDiscoveryConfig = {
    val location = ProcessLocation(
      server = java.net.InetAddress.getLocalHost.getHostName,
      user = System.getProperty("user.name"),
      ip_addresses = getNonLocalhostIPs(),
    )

    ServiceDiscoveryConfig(
      mailbox = mailbox,
      transport = transport,
      rpcServer = rpcServer,
      appName = appName,
      serviceName = serviceName,
      location = location,
      staticServiceDiscovery = staticServiceDiscovery,
    )
  }

  /**
   * Create a service discovery resource
   */
  def resource(config: ServiceDiscoveryConfig): Resource[ServiceDiscovery] = {
    Resource.acquireRelease {
      new ServiceDiscovery(config)
    } { discovery =>
      discovery.stop()
    }
  }

  // ===== Helper Methods =====

  private[discovery] def generateUid(): String = {
    // godev uses UID20() - 20-char base32 ULID
    // For now, use UUID and take first 20 chars
    java.util.UUID.randomUUID().toString.replace("-", "").take(20)
  }

}

class ServiceDiscovery(config: ServiceDiscoveryConfig) extends Logging {
  import ServiceDiscovery.*

  // Read from A8_PROCESS_UID env var, empty string if not present
  private val processUid: String = sys.env.getOrElse("A8_PROCESS_UID", "")

  // Read from A8_SERVICE_NAME env var, empty string if not present
  private val serviceName: String = sys.env.getOrElse("A8_SERVICE_NAME", "")

  @volatile private var running = false

  /**
   * Start service discovery
   */
  def start()(using ctx: Ctx): Unit = {
    if (running) {
      logger.warn("Service discovery already running")
    } else {
      running = true
      logger.info("Service discovery started")
    }
  }

  /**
   * Query for services matching criteria
   *
   * @param query The query filters
   * @param timeout How long to wait for responses
   * @return List of discovered services
   */
  def query(query: DiscoveryQuery, timeout: FiniteDuration = 5.seconds)(using ctx: Ctx): Seq[DiscoveryResponse] = {
    val request = DiscoveryRequest(
      request_id = generateUid(),
      reply_suffix = generateUid(),
      timestamp = Instant.now(),
      query = query,
    )

    val replySubject = s"${config.discoverySubject}.response.${request.reply_suffix}"
    logger.debug(s"Discovery query: request_id=${request.request_id} reply_subject=$replySubject")

    // Subscribe to reply subject
    val responses = TrieMap.empty[String, DiscoveryResponse]

    val subscription = config.transport.subscribe(replySubject)(using ctx).runForeach { envelope =>
      try {
        val payload = new String(envelope.payload, "UTF-8")
        val jsObj = json.parse(payload).toOption.get.asInstanceOf[JsObj]
        val response = jsObj.unsafeAs[DiscoveryResponse]

        if (response.request_id == request.request_id) {
          responses.put(response.mailbox_address, response)
          val processUidStr = if (response.processUid.isEmpty) "<none>" else response.processUid
          val serviceNameStr = if (response.service_name.isEmpty) "<none>" else response.service_name
          logger.debug(s"Received response: process=$processUidStr service=$serviceNameStr mailbox=${response.mailbox_address}")
        }
      } catch {
        case e: Exception =>
          logger.error(s"Error parsing discovery response: ${e.getMessage}", e)
      }
    }

    // Publish request
    val requestJson = request.compactJson
    config.transport.publish(
      subject = config.discoverySubject,
      headers = Map.empty,
      payload = requestJson.getBytes("UTF-8"),
    )(using ctx)

    // Wait for timeout
    Thread.sleep(timeout.toMillis)

    val results = responses.values.toSeq
    logger.debug(s"Discovery query complete: found ${results.size} services")
    results
  }

  /**
   * Find first service implementing a specific RPC
   */
  def findService(rpcEndpoint: String, timeout: FiniteDuration = 5.seconds)(using ctx: Ctx): Option[DiscoveryResponse] = {
    query(DiscoveryQuery(implements_rpc = Seq(rpcEndpoint)), timeout).headOption
  }

  /**
   * Find services by app name
   */
  def findByAppName(appName: String, timeout: FiniteDuration = 5.seconds)(using ctx: Ctx): Seq[DiscoveryResponse] = {
    query(DiscoveryQuery(app_name = Some(appName)), timeout)
  }

  /**
   * Register this service to respond to discovery queries
   */
  def register()(using ctx: Ctx): Unit = {
    val serviceNameStr = if (serviceName.isEmpty) "<none>" else serviceName
    val processUidStr = if (processUid.isEmpty) "<none>" else processUid
    logger.info(s"Registering service for discovery: app=${config.appName} service=$serviceNameStr")
    logger.info(s"  Subscribing to subject: ${config.discoverySubject}")
    logger.info(s"  Process UID (A8_PROCESS_UID): $processUidStr")
    logger.info(s"  Unix PID: ${getPid()}")
    logger.info(s"  Location: ${config.location.user}@${config.location.server}")
    if (config.metadata.nonEmpty) {
      logger.info(s"  Metadata keys: ${config.metadata.keys.mkString(", ")}")
    }

    // Run subscription in background thread to avoid blocking
    val thread = new Thread(() => {
      try {
        config.transport.subscribe(config.discoverySubject)(using ctx).runForeach { envelope =>
          if (running) {
            try {
              logger.debug(s"Received discovery request (${envelope.payload.length} bytes)")
              val payload = new String(envelope.payload, "UTF-8")
              logger.debug(s"Request payload: $payload")

              val jsObj = json.parse(payload).toOption.get.asInstanceOf[JsObj]
              val request = jsObj.unsafeAs[DiscoveryRequest]

              logger.debug(s"Parsed request: request_id=${request.request_id}")
              logger.debug(s"  Query: implements_rpc=${request.query.implements_rpc}, app_name=${request.query.app_name}, service_name=${request.query.service_name}")

              if (matchesQuery(request.query)) {
                logger.info(s"Query matched! Sending response for request_id=${request.request_id}")
                sendResponse(request)(using ctx)
              } else {
                logger.debug(s"Query did not match our capabilities")
              }
            } catch {
              case e: Exception =>
                logger.error(s"Error processing discovery request: ${e.getMessage}", e)
                e.printStackTrace()
            }
          }
        }
      } catch {
        case e: Exception =>
          logger.error(s"Discovery subscription thread failed: ${e.getMessage}", e)
      }
    }, "discovery-listener")

    thread.setDaemon(true)
    thread.start()
    logger.info(s"✓ Discovery listener started in background")
  }

  /**
   * Auto-register from RPC schemas
   */
  def autoRegister(capabilities: Map[String, String] = Map.empty)(using ctx: Ctx): Unit = {
    register()(using ctx)
  }

  /**
   * Send discovery response
   */
  private def sendResponse(request: DiscoveryRequest)(using ctx: Ctx): Unit = {
    val capabilities = buildCapabilities(
      includeSchemaDetails = request.query.include_schema_details
    )

    val response = DiscoveryResponse(
      request_id = request.request_id,
      processUid = processUid,
      unixPid = getPid(),
      app_name = config.appName,
      mailbox_address = config.mailbox.address.value,
      service_name = serviceName,
      codebase_name = "sync-scala",
      timestamp = Instant.now(),
      capabilities = capabilities,
      metadata = config.metadata,
      extended_metadata = if (request.query.include_extended_metadata) Some(config.extendedMetadata) else None,
      service_discovery_mapping = config.staticServiceDiscovery
        .map(_.getAllMailboxes)
        .getOrElse(Map.empty),
    )

    val replySubject = s"${config.discoverySubject}.response.${request.reply_suffix}"
    val responseJson = response.compactJson

    val serviceNameStr = if (response.service_name.isEmpty) "<none>" else response.service_name
    logger.info(s"Sending discovery response:")
    logger.info(s"  Reply subject: $replySubject")
    logger.info(s"  Process: ${response.app_name} / $serviceNameStr")
    logger.info(s"  Codebase: ${response.codebase_name}")
    logger.info(s"  Mailbox: ${response.mailbox_address}")
    logger.info(s"  Unix PID: ${response.unixPid}")
    logger.info(s"  Implemented RPCs: ${capabilities.implements_rpc.mkString(", ")}")
    logger.info(s"  RPC Schemas: ${capabilities.rpc_schemas.size} schemas")
    capabilities.rpc_schemas.foreach { case (name, methods) =>
      logger.info(s"    - $name: ${methods.mkString(", ")}")
    }
    logger.info(s"  Response JSON: $responseJson")

    config.transport.publish(
      subject = replySubject,
      headers = Map.empty,
      payload = responseJson.getBytes("UTF-8"),
    )(using ctx)

    logger.info(s"✓ Discovery response sent")
  }

  /**
   * Build capabilities from RPC server
   */
  private def buildCapabilities(includeSchemaDetails: Boolean = false): ProcessCapabilities = {
    // Group schemas by name.version to get list of methods
    // e.g. "ping.v1" -> ["Ping"], "process.v1" -> ["Shutdown", "Status"]
    val allSchemas = RpcSchema.all
    logger.debug(s"Building capabilities from ${allSchemas.size} registered schemas:")
    allSchemas.foreach { schema =>
      logger.debug(s"  - ${schema.name.value} (requiresAuth=${schema.requiresAuth})")
    }

    // Group by schema name (e.g., "process.v1")
    val schemasByName: Map[String, Seq[RpcSchema.Schema]] = allSchemas
      .groupBy(schema => s"${schema.name.name}.${schema.name.version}")
      .view
      .mapValues(_.toSeq)
      .toMap

    // Extract implemented RPC schema names
    val implementedRpcs: Iterable[String] = schemasByName.keys

    // Build legacy rpc_schemas map (always included)
    val rpcSchemas: Map[String, Iterable[String]] = schemasByName
      .view
      .mapValues(schemas => schemas.map(_.name.method).distinct)
      .toMap

    // Build rich schema details (only when requested)
    val rpcSchemaDetails: Iterable[RpcSchemaInfo] =
      if (includeSchemaDetails) {
        schemasByName.map { case (schemaName, schemas) =>
          val methods = schemas.map { schema =>
            RpcMethod(
              name = schema.name.method,
              formats = Iterable(SerializationFormat.JSON),
              request_type = "",
              response_type = "",
              description = schema.description.getOrElse(""),
            )
          }

          RpcSchemaInfo(
            name = schemaName,
            methods = methods,
            default_formats = Iterable(SerializationFormat.JSON),
            schema_version = 1,
            description = schemas.headOption.flatMap(_.description).getOrElse(""),
          )
        }.toSeq
      } else {
        Iterable.empty
      }

    ProcessCapabilities(
      implements_rpc = implementedRpcs,
      rpc_schemas = rpcSchemas,
      rpc_schema_details = rpcSchemaDetails,
      location = config.location,
      engines = Iterable.empty, // Can be populated from HermesBootstrap
    )
  }

  /**
   * Check if this service matches the query
   */
  private def matchesQuery(query: DiscoveryQuery): Boolean = {
    // ALL RPCs in query must be implemented (not just one!)
    val rpcMatch = query.implements_rpc.isEmpty || {
      // Extract name.version (e.g. "process.v1") from schemas, matching buildCapabilities logic
      val ourRpcs = RpcSchema.all
        .map(schema => s"${schema.name.name}.${schema.name.version}")
        .toSet
      query.implements_rpc.forall(ourRpcs.contains)
    }

    val appMatch = query.app_name.forall(matchesPattern(config.appName, _))
    val serviceMatch =
      config
        .serviceName
        .map { sn =>
          query.service_name.forall(matchesPattern(sn, _))
        }
        .getOrElse(query.service_name.isEmpty)

    val locationMatch = query.location.forall { locQuery =>
      locQuery.server.forall(_ == config.location.server) &&
      locQuery.user.forall(_ == config.location.user) &&
      locQuery.user_server.forall(_ == s"${config.location.user}@${config.location.server}")
    }

    rpcMatch && appMatch && serviceMatch && locationMatch
  }

  /**
   * Wildcard pattern matching (supports trailing *)
   */
  private def matchesPattern(value: String, pattern: String): Boolean = {
    if (pattern == "*") true
    else if (pattern.endsWith("*")) value.startsWith(pattern.dropRight(1))
    else value == pattern
  }

  /**
   * Stop service discovery
   */
  def stop(): Unit = {
    if (running) {
      logger.info("Stopping service discovery")
      running = false
    }
  }

  /**
   * Get all cached services (for compatibility with old API)
   */
  def cachedServices: Seq[DiscoveryResponse] = {
    logger.warn("cachedServices is deprecated - use query() instead")
    Seq.empty
  }

  // ===== Utility Methods =====

  private def getPid(): Int = {
    ProcessHandle.current().pid().toInt
  }

}
