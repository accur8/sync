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

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.{FiniteDuration, *}
import java.time.Instant

/**
 * Service discovery for Hermes/NATS - ALIGNED WITH GODEV PROTOCOL.
 *
 * Uses pub/sub on nefario.discovery subject with JSON messages matching
 * /Users/glen/code/accur8/godev/pkg/discovery/messages.go exactly.
 *
 * This enables Go services and Scala services to discover each other.
 */
object ServiceDiscovery extends Logging {

  // ===== Message Types (matching godev) =====

  /**
   * DiscoveryRequest - published to nefario.discovery subject
   */
  case class DiscoveryRequest(
    requestId: String,
    replySuffix: String,
    timestamp: Instant,
    query: DiscoveryQuery,
  )

  /**
   * DiscoveryResponse - published to nefario.discovery.response.{reply_suffix}
   */
  case class DiscoveryResponse(
    requestId: String,
    processUid: Option[String],  // From PROCESS_UID env var
    unixPid: Int,
    appName: String,
    mailboxAddress: String,
    serviceName: Option[String],  // From SERVICE_NAME env var
    timestamp: Instant,
    capabilities: ProcessCapabilities,
    metadata: Map[String, String],
    extendedMetadata: Option[Map[String, String]],
    serviceDiscoveryMapping: Map[String, String],
  )

  /**
   * DiscoveryQuery - filters for finding services
   */
  case class DiscoveryQuery(
    implementsRpc: Seq[String] = Seq.empty,
    appName: Option[String] = None,
    serviceName: Option[String] = None,
    location: Option[LocationQuery] = None,
    includeExtendedMetadata: Boolean = false,
  )

  /**
   * LocationQuery - filters by deployment location
   */
  case class LocationQuery(
    server: Option[String] = None,
    user: Option[String] = None,
    userServer: Option[String] = None,
  )

  /**
   * ProcessCapabilities - what a process can do
   */
  case class ProcessCapabilities(
    implementsRpc: Seq[String],
    rpcSchemas: Map[String, Seq[String]],
    location: ProcessLocation,
    engines: Seq[String] = Seq.empty,
  )

  /**
   * ProcessLocation - where a process is running
   */
  case class ProcessLocation(
    server: String,
    user: String,
    ipAddresses: Seq[String] = Seq.empty,
  )

  // ===== Configuration =====

  case class Config(
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

  // ===== Utility Methods =====

  /**
   * Get all non-localhost IPv4 addresses (matching godev's GetNonLocalhostIPs)
   */
  def getNonLocalhostIPs(): Seq[String] = {
    import java.net.NetworkInterface
    import scala.jdk.CollectionConverters.*

    try {
      NetworkInterface.getNetworkInterfaces.asScala
        .flatMap(_.getInetAddresses.asScala)
        .filter(addr => addr.getAddress.length == 4) // IPv4 only
        .filterNot(_.isLoopbackAddress)
        .map(_.getHostAddress)
        .toSeq
    } catch {
      case e: Exception =>
        logger.warn(s"Failed to get non-localhost IPs: ${e.getMessage}")
        Seq.empty
    }
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
  ): Config = {
    val location = ProcessLocation(
      server = java.net.InetAddress.getLocalHost.getHostName,
      user = System.getProperty("user.name"),
      ipAddresses = getNonLocalhostIPs(),
    )

    Config(
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
  def resource(config: Config): Resource[ServiceDiscovery] = {
    Resource.acquireRelease {
      new ServiceDiscovery(config)
    } { discovery =>
      discovery.stop()
    }
  }

  // ===== Helper Methods =====

  private def generateUid(): String = {
    // godev uses UID20() - 20-char base32 ULID
    // For now, use UUID and take first 20 chars
    java.util.UUID.randomUUID().toString.replace("-", "").take(20)
  }

}

class ServiceDiscovery(config: ServiceDiscovery.Config) extends Logging {
  import ServiceDiscovery.*

  // Read from PROCESS_UID env var, omit if not present
  private val processUid: Option[String] = sys.env.get("PROCESS_UID")

  // Read from SERVICE_NAME env var, omit if not present
  private val serviceName: Option[String] = sys.env.get("SERVICE_NAME")

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
      requestId = generateUid(),
      replySuffix = generateUid(),
      timestamp = Instant.now(),
      query = query,
    )

    val replySubject = s"${config.discoverySubject}.response.${request.replySuffix}"
    logger.debug(s"Discovery query: request_id=${request.requestId} reply_subject=$replySubject")

    // Subscribe to reply subject
    val responses = TrieMap.empty[String, DiscoveryResponse]

    val subscription = config.transport.subscribe(replySubject)(using ctx).runForeach { envelope =>
      try {
        val payload = new String(envelope.payload, "UTF-8")
        val jsObj = json.parse(payload).toOption.get.asInstanceOf[JsObj]
        val response = DiscoveryJson.parseResponse(jsObj)

        if (response.requestId == request.requestId) {
          responses.put(response.mailboxAddress, response)
          logger.debug(s"Received response: process=${response.processUid.getOrElse("<none>")} service=${response.serviceName.getOrElse("<none>")} mailbox=${response.mailboxAddress}")
        }
      } catch {
        case e: Exception =>
          logger.error(s"Error parsing discovery response: ${e.getMessage}", e)
      }
    }

    // Publish request
    val requestJson = DiscoveryJson.requestToJson(request).compactJson
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
    query(DiscoveryQuery(implementsRpc = Seq(rpcEndpoint)), timeout).headOption
  }

  /**
   * Find services by app name
   */
  def findByAppName(appName: String, timeout: FiniteDuration = 5.seconds)(using ctx: Ctx): Seq[DiscoveryResponse] = {
    query(DiscoveryQuery(appName = Some(appName)), timeout)
  }

  /**
   * Register this service to respond to discovery queries
   */
  def register()(using ctx: Ctx): Unit = {
    logger.info(s"Registering service for discovery: app=${config.appName} service=${serviceName.getOrElse("<none>")}")
    logger.info(s"  Subscribing to subject: ${config.discoverySubject}")
    logger.info(s"  Process UID: ${processUid.getOrElse("<none>")}")
    logger.info(s"  Unix PID: ${getPid()}")
    logger.info(s"  Location: ${config.location.user}@${config.location.server}")

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
              val request = DiscoveryJson.parseRequest(jsObj)

              logger.debug(s"Parsed request: request_id=${request.requestId}")
              logger.debug(s"  Query: implementsRpc=${request.query.implementsRpc}, appName=${request.query.appName}, serviceName=${request.query.serviceName}")

              if (matchesQuery(request.query)) {
                logger.info(s"Query matched! Sending response for request_id=${request.requestId}")
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
    val capabilities = buildCapabilities()

    val response = DiscoveryResponse(
      requestId = request.requestId,
      processUid = processUid,
      unixPid = getPid(),
      appName = config.appName,
      mailboxAddress = config.mailbox.address.value,
      serviceName = serviceName,
      timestamp = Instant.now(),
      capabilities = capabilities,
      metadata = config.metadata,
      extendedMetadata = if (request.query.includeExtendedMetadata) Some(config.extendedMetadata) else None,
      serviceDiscoveryMapping = config.staticServiceDiscovery
        .map(_.getAllMailboxes)
        .getOrElse(Map.empty),
    )

    val replySubject = s"${config.discoverySubject}.response.${request.replySuffix}"
    val responseJson = DiscoveryJson.responseToJson(response).compactJson

    logger.info(s"Sending discovery response:")
    logger.info(s"  Reply subject: $replySubject")
    logger.info(s"  Process: ${response.appName} / ${response.serviceName.getOrElse("<none>")}")
    logger.info(s"  Mailbox: ${response.mailboxAddress}")
    logger.info(s"  Implemented RPCs: ${capabilities.implementsRpc.mkString(", ")}")
    logger.debug(s"  Response JSON: $responseJson")

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
  private def buildCapabilities(): ProcessCapabilities = {
    // Group schemas by name.version to get list of methods
    // e.g. "ping.v1" -> ["Ping"], "process.v1" -> ["Shutdown", "Status"]
    val allSchemas = RpcSchema.all
    logger.debug(s"Building capabilities from ${allSchemas.size} registered schemas:")
    allSchemas.foreach { schema =>
      logger.debug(s"  - ${schema.name.value} (requiresAuth=${schema.requiresAuth})")
    }

    val rpcSchemas: Map[String, Seq[String]] = allSchemas
      .groupBy(schema => s"${schema.name.name}.${schema.name.version}")
      .view
      .mapValues(schemas => schemas.map(_.name.method).distinct)
      .toMap

    // Get list of implemented RPC schemas (name.version)
    val implementedRpcs = rpcSchemas.keys.toSeq.distinct

    ProcessCapabilities(
      implementsRpc = implementedRpcs,
      rpcSchemas = rpcSchemas,
      location = config.location,
      engines = Seq.empty, // Can be populated from HermesBootstrap
    )
  }

  /**
   * Check if this service matches the query
   */
  private def matchesQuery(query: DiscoveryQuery): Boolean = {
    // ALL RPCs in query must be implemented (not just one!)
    val rpcMatch = query.implementsRpc.isEmpty || {
      // Extract name.version (e.g. "process.v1") from schemas, matching buildCapabilities logic
      val ourRpcs = RpcSchema.all
        .map(schema => s"${schema.name.name}.${schema.name.version}")
        .toSet
      query.implementsRpc.forall(ourRpcs.contains)
    }

    val appMatch = query.appName.forall(matchesPattern(config.appName, _))
    val serviceMatch =
      config
        .serviceName
        .map { sn =>
          query.serviceName.forall(matchesPattern(sn, _))
        }
        .getOrElse(query.serviceName.isEmpty)

    val locationMatch = query.location.forall { locQuery =>
      locQuery.server.forall(_ == config.location.server) &&
      locQuery.user.forall(_ == config.location.user) &&
      locQuery.userServer.forall(_ == s"${config.location.user}@${config.location.server}")
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
