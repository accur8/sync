# Service Discovery - godev Protocol Alignment

## Progress Summary

### ✅ COMPLETED:
- **Static Service Discovery** - Map-based named mailbox resolution (PRIMARY method)
- **Dynamic Discovery Framework** - Basic pub/sub implementation on `nefario.discovery`
- **Service Registration** - Auto-register from RpcSchema.all
- **Query/Response Flow** - Basic discovery query mechanism

### ⚠️ CURRENT GAPS:
- **Protocol Mismatch** - Scala uses different JSON structure than godev
- **Missing Fields** - No Unix PID, RPC schemas map, location details, metadata
- **Reply Subject Pattern** - Uses mailbox address instead of random suffix
- **Filter Semantics** - Single RPC filter vs. array-based "match ALL" in godev

### 🎯 THIS PLAN:
**Full alignment of Scala service discovery with godev protocol for Go/Scala interoperability**

## Overview

Rewrite `hermes/src/main/scala/a8/hermes/discovery/ServiceDiscovery.scala` to exactly match the godev discovery protocol defined in `/Users/glen/code/accur8/godev/pkg/discovery/messages.go`.

**Why**: The user requires Go services and Scala services to discover each other using the same protocol.

**Reference Implementation**: `/Users/glen/code/accur8/godev/pkg/discovery/service.go` and `messages.go`
**Reference Documentation**: `/Users/glen/code/accur8/godev/docs/NATS_CLIENT_IMPLEMENTATION_GUIDE.md`

## godev Discovery Protocol

### Message Structure

**DiscoveryRequest** (published to `nefario.discovery`):
```json
{
  "request_id": "ulid-12345",
  "reply_suffix": "ulid-67890",
  "timestamp": "2025-12-19T10:30:00Z",
  "query": {
    "implements_rpc": ["scheduler.v1", "process.v1"],
    "app_name": "minion",
    "service_name": "job-runner",
    "location": {
      "server": "server1.example.com",
      "user": "deploy",
      "user_server": "deploy@server1.example.com"
    },
    "include_extended_metadata": true
  }
}
```

**DiscoveryResponse** (published to `nefario.discovery.response.{reply_suffix}`):
```json
{
  "request_id": "ulid-12345",
  "processUid": "ulid-process-abc",
  "unixPid": 12345,
  "app_name": "minion",
  "mailbox_address": "mb_abc123",
  "service_name": "job-runner",
  "timestamp": "2025-12-19T10:30:01Z",
  "capabilities": {
    "implements_rpc": ["scheduler.v1", "process.v1", "logging.v1"],
    "rpc_schemas": {
      "scheduler.v1": ["Start", "Stop", "Status"],
      "process.v1": ["Shutdown", "GetBuildInfo"]
    },
    "location": {
      "server": "server1.example.com",
      "user": "deploy",
      "ip_addresses": ["192.168.1.10", "10.0.0.5"]
    },
    "engines": ["scheduler", "logging"]
  },
  "metadata": {
    "minion_uid": "ulid-minion",
    "minion_label": "server1",
    "service_name": "job-runner",
    "version": "1.2.3",
    "git_commit": "abc123"
  },
  "extended_metadata": {
    "build_timestamp": "2025-12-15T08:00:00Z",
    "build_machine": "build-server",
    "build_user": "ci",
    "git_branch": "main",
    "git_dirty": "false"
  },
  "service_discovery_mapping": {
    "auth": "auth-rpc",
    "nefario": "nefario-rpc"
  }
}
```

### Key Differences from Current Scala Implementation

| Aspect | godev | Current Scala | Status |
|--------|-------|---------------|--------|
| Request structure | `request_id`, `reply_suffix`, `timestamp`, `query` | Query in payload, metadata in headers | ❌ Different |
| ImplementsRpc filter | Array of strings (match ALL) | Single string (match one) | ❌ Wrong semantics |
| Location | Structured object with server/user/IPs | Simple string | ❌ Missing detail |
| Capabilities.rpc_schemas | Map of schema → methods array | Not present | ❌ Missing |
| Metadata | Separate `metadata` and `extended_metadata` | Not present | ❌ Missing |
| Unix PID | `unixPid` field | Not present | ❌ Missing |
| Timestamp | ISO 8601 in both request/response | Not present | ❌ Missing |
| Reply subject | `nefario.discovery.response.{random-suffix}` | `nefario.discovery.response.{mailbox-address}` | ❌ Wrong pattern |
| Service discovery mapping | Map of service names → addresses | Not present | ❌ Missing |

## Implementation Steps

### Step 1: Define godev-Compatible Case Classes

**File**: `hermes/src/main/scala/a8/hermes/discovery/ServiceDiscovery.scala`

```scala
package a8.hermes.discovery

import java.time.Instant

object ServiceDiscovery {

  // Core message types
  case class DiscoveryRequest(
    requestId: String,
    replySuffix: String,
    timestamp: Instant,
    query: DiscoveryQuery,
  )

  case class DiscoveryResponse(
    requestId: String,
    processUid: String,
    unixPid: Int,
    appName: String,
    mailboxAddress: String,
    serviceName: String,
    timestamp: Instant,
    capabilities: ProcessCapabilities,
    metadata: Map[String, String],
    extendedMetadata: Option[Map[String, String]],
    serviceDiscoveryMapping: Map[String, String],
  )

  case class DiscoveryQuery(
    implementsRpc: Seq[String] = Seq.empty,
    appName: Option[String] = None,
    serviceName: Option[String] = None,
    location: Option[LocationQuery] = None,
    includeExtendedMetadata: Boolean = false,
  )

  case class LocationQuery(
    server: Option[String] = None,
    user: Option[String] = None,
    userServer: Option[String] = None,
  )

  case class ProcessCapabilities(
    implementsRpc: Seq[String],
    rpcSchemas: Map[String, Seq[String]],
    location: ProcessLocation,
    engines: Seq[String] = Seq.empty,
  )

  case class ProcessLocation(
    server: String,
    user: String,
    ipAddresses: Seq[String] = Seq.empty,
  )

  // Configuration
  case class Config(
    mailbox: Mailbox,
    transport: MailboxTransport,
    rpcServer: Option[RpcServer] = None,
    discoverySubject: String = "nefario.discovery",
    appName: String,
    serviceName: String,
    location: ProcessLocation,
    metadata: Map[String, String] = Map.empty,
    extendedMetadata: Map[String, String] = Map.empty,
    staticServiceDiscovery: Option[StaticServiceDiscovery] = None,
  )
}
```

### Step 2: Implement JSON Serialization

**File**: `hermes/src/main/scala/a8/hermes/discovery/DiscoveryJson.scala`

```scala
package a8.hermes.discovery

import a8.shared.json.ast.*
import java.time.Instant

object DiscoveryJson {

  def requestToJson(req: DiscoveryRequest): JsObj = {
    JsObj(Map(
      "request_id" -> JsStr(req.requestId),
      "reply_suffix" -> JsStr(req.replySuffix),
      "timestamp" -> JsStr(req.timestamp.toString),
      "query" -> queryToJson(req.query),
    ))
  }

  def queryToJson(query: DiscoveryQuery): JsObj = {
    val fields = Map.newBuilder[String, JsVal]

    if (query.implementsRpc.nonEmpty) {
      fields += "implements_rpc" -> JsArr(query.implementsRpc.map(JsStr(_)).toList)
    }
    query.appName.foreach(v => fields += "app_name" -> JsStr(v))
    query.serviceName.foreach(v => fields += "service_name" -> JsStr(v))
    query.location.foreach(loc => fields += "location" -> locationQueryToJson(loc))
    if (query.includeExtendedMetadata) {
      fields += "include_extended_metadata" -> JsBool(true)
    }

    JsObj(fields.result())
  }

  def locationQueryToJson(loc: LocationQuery): JsObj = {
    val fields = Map.newBuilder[String, JsVal]
    loc.server.foreach(v => fields += "server" -> JsStr(v))
    loc.user.foreach(v => fields += "user" -> JsStr(v))
    loc.userServer.foreach(v => fields += "user_server" -> JsStr(v))
    JsObj(fields.result())
  }

  def responseToJson(resp: DiscoveryResponse): JsObj = {
    val fields = Map(
      "request_id" -> JsStr(resp.requestId),
      "processUid" -> JsStr(resp.processUid),
      "unixPid" -> JsNum(resp.unixPid),
      "app_name" -> JsStr(resp.appName),
      "mailbox_address" -> JsStr(resp.mailboxAddress),
      "service_name" -> JsStr(resp.serviceName),
      "timestamp" -> JsStr(resp.timestamp.toString),
      "capabilities" -> capabilitiesToJson(resp.capabilities),
      "metadata" -> mapToJson(resp.metadata),
      "service_discovery_mapping" -> mapToJson(resp.serviceDiscoveryMapping),
    )

    val withExtended = resp.extendedMetadata match {
      case Some(ext) => fields + ("extended_metadata" -> mapToJson(ext))
      case None => fields
    }

    JsObj(withExtended)
  }

  def capabilitiesToJson(caps: ProcessCapabilities): JsObj = {
    JsObj(Map(
      "implements_rpc" -> JsArr(caps.implementsRpc.map(JsStr(_)).toList),
      "rpc_schemas" -> JsObj(
        caps.rpcSchemas.map { case (k, methods) =>
          k -> JsArr(methods.map(JsStr(_)).toList)
        }
      ),
      "location" -> locationToJson(caps.location),
      "engines" -> JsArr(caps.engines.map(JsStr(_)).toList),
    ))
  }

  def locationToJson(loc: ProcessLocation): JsObj = {
    JsObj(Map(
      "server" -> JsStr(loc.server),
      "user" -> JsStr(loc.user),
      "ip_addresses" -> JsArr(loc.ipAddresses.map(JsStr(_)).toList),
    ))
  }

  def mapToJson(m: Map[String, String]): JsObj = {
    JsObj(m.map { case (k, v) => k -> JsStr(v) })
  }

  // Parsing methods
  def parseRequest(jsObj: JsObj): DiscoveryRequest = {
    DiscoveryRequest(
      requestId = jsObj.values("request_id").asInstanceOf[JsStr].value,
      replySuffix = jsObj.values("reply_suffix").asInstanceOf[JsStr].value,
      timestamp = Instant.parse(jsObj.values("timestamp").asInstanceOf[JsStr].value),
      query = parseQuery(jsObj.values("query").asInstanceOf[JsObj]),
    )
  }

  def parseQuery(jsObj: JsObj): DiscoveryQuery = {
    DiscoveryQuery(
      implementsRpc = jsObj.values.get("implements_rpc")
        .map(_.asInstanceOf[JsArr].values.map(_.asInstanceOf[JsStr].value))
        .getOrElse(Seq.empty),
      appName = jsObj.values.get("app_name").map(_.asInstanceOf[JsStr].value),
      serviceName = jsObj.values.get("service_name").map(_.asInstanceOf[JsStr].value),
      location = jsObj.values.get("location").map(v => parseLocationQuery(v.asInstanceOf[JsObj])),
      includeExtendedMetadata = jsObj.values.get("include_extended_metadata")
        .map(_.asInstanceOf[JsBool].value)
        .getOrElse(false),
    )
  }

  def parseLocationQuery(jsObj: JsObj): LocationQuery = {
    LocationQuery(
      server = jsObj.values.get("server").map(_.asInstanceOf[JsStr].value),
      user = jsObj.values.get("user").map(_.asInstanceOf[JsStr].value),
      userServer = jsObj.values.get("user_server").map(_.asInstanceOf[JsStr].value),
    )
  }

  def parseResponse(jsObj: JsObj): DiscoveryResponse = {
    DiscoveryResponse(
      requestId = jsObj.values("request_id").asInstanceOf[JsStr].value,
      processUid = jsObj.values("processUid").asInstanceOf[JsStr].value,
      unixPid = jsObj.values("unixPid").asInstanceOf[JsNum].value.toInt,
      appName = jsObj.values("app_name").asInstanceOf[JsStr].value,
      mailboxAddress = jsObj.values("mailbox_address").asInstanceOf[JsStr].value,
      serviceName = jsObj.values("service_name").asInstanceOf[JsStr].value,
      timestamp = Instant.parse(jsObj.values("timestamp").asInstanceOf[JsStr].value),
      capabilities = parseCapabilities(jsObj.values("capabilities").asInstanceOf[JsObj]),
      metadata = parseMap(jsObj.values("metadata").asInstanceOf[JsObj]),
      extendedMetadata = jsObj.values.get("extended_metadata").map(v => parseMap(v.asInstanceOf[JsObj])),
      serviceDiscoveryMapping = parseMap(jsObj.values("service_discovery_mapping").asInstanceOf[JsObj]),
    )
  }

  def parseCapabilities(jsObj: JsObj): ProcessCapabilities = {
    ProcessCapabilities(
      implementsRpc = jsObj.values("implements_rpc").asInstanceOf[JsArr].values
        .map(_.asInstanceOf[JsStr].value),
      rpcSchemas = jsObj.values("rpc_schemas").asInstanceOf[JsObj].values.map { case (k, v) =>
        k -> v.asInstanceOf[JsArr].values.map(_.asInstanceOf[JsStr].value)
      },
      location = parseLocation(jsObj.values("location").asInstanceOf[JsObj]),
      engines = jsObj.values.get("engines")
        .map(_.asInstanceOf[JsArr].values.map(_.asInstanceOf[JsStr].value))
        .getOrElse(Seq.empty),
    )
  }

  def parseLocation(jsObj: JsObj): ProcessLocation = {
    ProcessLocation(
      server = jsObj.values("server").asInstanceOf[JsStr].value,
      user = jsObj.values("user").asInstanceOf[JsStr].value,
      ipAddresses = jsObj.values.get("ip_addresses")
        .map(_.asInstanceOf[JsArr].values.map(_.asInstanceOf[JsStr].value))
        .getOrElse(Seq.empty),
    )
  }

  def parseMap(jsObj: JsObj): Map[String, String] = {
    jsObj.values.map { case (k, v) => k -> v.asInstanceOf[JsStr].value }
  }
}
```

### Step 3: Update ServiceDiscovery Class

**File**: `hermes/src/main/scala/a8/hermes/discovery/ServiceDiscovery.scala`

```scala
class ServiceDiscovery(config: ServiceDiscovery.Config) extends Logging {
  import ServiceDiscovery.*

  private val processUid = generateProcessUid()
  @volatile private var running = false

  /**
   * Start service discovery (no subscription needed for queries)
   */
  def start()(using ctx: Ctx): Unit = {
    running = true
    logger.info("Service discovery started")
  }

  /**
   * Query for services matching criteria
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
          responses.put(response.processUid, response)
          logger.debug(s"Received response: process=${response.processUid} service=${response.serviceName}")
        }
      } catch {
        case e: Exception =>
          logger.error("Error parsing discovery response", e)
      }
    }

    // Publish request
    val requestJson = DiscoveryJson.requestToJson(request).toString
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
   * Register this service to respond to discovery queries
   */
  def register()(using ctx: Ctx): Unit = {
    logger.info(s"Registering service: app=${config.appName} service=${config.serviceName}")

    config.transport.subscribe(config.discoverySubject)(using ctx).runForeach { envelope =>
      if (running) {
        try {
          val payload = new String(envelope.payload, "UTF-8")
          val jsObj = json.parse(payload).toOption.get.asInstanceOf[JsObj]
          val request = DiscoveryJson.parseRequest(jsObj)

          if (matchesQuery(request.query)) {
            sendResponse(request)(using ctx)
          }
        } catch {
          case e: Exception =>
            logger.error("Error processing discovery request", e)
        }
      }
    }
  }

  /**
   * Send discovery response
   */
  private def sendResponse(request: DiscoveryRequest)(using ctx: Ctx): Unit = {
    val response = DiscoveryResponse(
      requestId = request.requestId,
      processUid = processUid,
      unixPid = getPid(),
      appName = config.appName,
      mailboxAddress = config.mailbox.address.value,
      serviceName = config.serviceName,
      timestamp = Instant.now(),
      capabilities = buildCapabilities(),
      metadata = config.metadata,
      extendedMetadata = if (request.query.includeExtendedMetadata) Some(config.extendedMetadata) else None,
      serviceDiscoveryMapping = config.staticServiceDiscovery
        .map(_.getAllMailboxes)
        .getOrElse(Map.empty),
    )

    val replySubject = s"${config.discoverySubject}.response.${request.replySuffix}"
    val responseJson = DiscoveryJson.responseToJson(response).toString

    config.transport.publish(
      subject = replySubject,
      headers = Map.empty,
      payload = responseJson.getBytes("UTF-8"),
    )(using ctx)

    logger.debug(s"Sent discovery response: request_id=${request.requestId} reply_subject=$replySubject")
  }

  /**
   * Build capabilities from RPC server
   */
  private def buildCapabilities(): ProcessCapabilities = {
    val implementedRpcs = RpcSchema.all.map(_.name.value)

    val rpcSchemas: Map[String, Seq[String]] = config.rpcServer match {
      case Some(server) =>
        RpcSchema.all.map { schema =>
          schema.name.value -> schema.handlers.map(_.method)
        }.toMap
      case None =>
        Map.empty
    }

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
      val ourRpcs = RpcSchema.all.map(_.name.value).toSet
      query.implementsRpc.forall(ourRpcs.contains)
    }

    val appMatch = query.appName.forall(matchesPattern(config.appName, _))
    val serviceMatch = query.serviceName.forall(matchesPattern(config.serviceName, _))

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
    running = false
    logger.info("Service discovery stopped")
  }

  // Utility methods
  private def generateUid(): String = {
    // godev uses UID20() - 20-char base32 ULID
    java.util.UUID.randomUUID().toString.replace("-", "").take(20)
  }

  private def generateProcessUid(): String = {
    s"process-${generateUid()}"
  }

  private def getPid(): Int = {
    ProcessHandle.current().pid().toInt
  }
}
```

### Step 4: Add Utility Methods

**File**: `hermes/src/main/scala/a8/hermes/discovery/ServiceDiscovery.scala`

Add to companion object:

```scala
object ServiceDiscovery {
  // ... case classes ...

  /**
   * Get all non-localhost IPv4 addresses (matching godev's GetNonLocalhostIPs)
   */
  def getNonLocalhostIPs(): Seq[String] = {
    import java.net.NetworkInterface
    import scala.jdk.CollectionConverters.*

    NetworkInterface.getNetworkInterfaces.asScala
      .flatMap(_.getInetAddresses.asScala)
      .filter(addr => addr.getAddress.length == 4) // IPv4 only
      .filterNot(_.isLoopbackAddress)
      .map(_.getHostAddress)
      .toSeq
  }

  /**
   * Create discovery config with sensible defaults
   */
  def defaultConfig(
    mailbox: Mailbox,
    transport: MailboxTransport,
    rpcServer: Option[RpcServer],
    appName: String,
    serviceName: String,
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
}
```

### Step 5: Update HermesBootstrap Integration

**File**: `hermes/src/main/scala/a8/hermes/bootstrap/HermesBootstrap.scala`

```scala
// In resource() method, Step 6
dynamicServiceDiscovery <- if (config.enableDynamicDiscovery.getOrElse(false)) {
  logger.info("Starting dynamic service discovery...")

  val discoveryConfig = ServiceDiscovery.defaultConfig(
    mailbox = mailbox,
    transport = natsTransport,
    rpcServer = Some(rpcServer),
    appName = config.appName.getOrElse("hermes"),
    serviceName = config.appName.getOrElse("hermes"),
    staticServiceDiscovery = Some(staticServiceDiscovery),
  )

  // Add metadata if BuildInfo available
  val configWithMetadata = discoveryConfig.copy(
    metadata = Map(
      "service_name" -> config.appName.getOrElse("hermes"),
      // "version" -> BuildInfo.version,
      // "git_commit" -> BuildInfo.gitCommit,
    )
  )

  Resource.acquireRelease {
    val discovery = new ServiceDiscovery(configWithMetadata)
    discovery.start()(using ctx)
    discovery.register()(using ctx)
    Some(discovery)
  } { discovery =>
    discovery.foreach(_.stop())
  }
} else {
  Resource.pure(None)
}
```

### Step 6: Update Config

**File**: `hermes/src/main/scala/a8/hermes/bootstrap/HermesBootstrapConfig.scala`

```scala
case class HermesBootstrapConfig(
  // ... existing fields ...
  enableDynamicDiscovery: Option[Boolean] = Some(false), // Add this
)
```

### Step 7: Update PingService

**File**: `examples/hermes-test/src/main/scala/a8/examples/hermes/PingService.scala`

Add after RPC handler registration:

```scala
// Test discovery if enabled
hermes.dynamicServiceDiscovery.foreach { discovery =>
  logger.info("✓ Dynamic service discovery enabled")
  logger.info("  Querying for ping.v1 services...")

  val query = ServiceDiscovery.DiscoveryQuery(
    implementsRpc = Seq("ping.v1")
  )

  val services = discovery.query(query, timeout = 2.seconds)(using appCtx)
  logger.info(s"  Found ${services.size} ping services:")

  services.foreach { service =>
    logger.info(s"    - ${service.serviceName} @ ${service.mailboxAddress}")
    logger.info(s"      PID: ${service.unixPid}")
    logger.info(s"      Location: ${service.capabilities.location.user}@${service.capabilities.location.server}")
  }
}
```

### Step 8: Create Discovery Test Client

**File**: `examples/hermes-test/src/main/scala/a8/examples/hermes/DiscoveryTest.scala`

```scala
package a8.examples.hermes

import a8.hermes.bootstrap.HermesBootstrap
import a8.hermes.discovery.ServiceDiscovery
import a8.shared.app.{BootstrappedIOApp, AppCtx}
import a8.shared.zreplace.Resource

import scala.concurrent.duration.*

/**
 * Test client for service discovery.
 *
 * Usage:
 *   sbt "hermesTest/runMain a8.examples.hermes.DiscoveryTest"
 */
object DiscoveryTest extends BootstrappedIOApp {

  override def run()(using appCtx: AppCtx): Unit = {
    Thread.currentThread().setName("discovery-test-main")
    logger.info("Starting Discovery Test...")

    val hermes = Resource.free.run(HermesBootstrap.resource())(using appCtx)

    hermes.dynamicServiceDiscovery match {
      case Some(discovery) =>
        logger.info("✓ Dynamic service discovery enabled")
        logger.info("")

        // Test 1: Find all ping services
        logger.info("Test 1: Query for ping.v1 services")
        val pingServices = discovery.query(
          ServiceDiscovery.DiscoveryQuery(implementsRpc = Seq("ping.v1")),
          timeout = 3.seconds
        )(using appCtx)

        logger.info(s"Found ${pingServices.size} ping services:")
        pingServices.foreach(printService)

        logger.info("")

        // Test 2: Find all services (empty query)
        logger.info("Test 2: Query for all services")
        val allServices = discovery.query(
          ServiceDiscovery.DiscoveryQuery(),
          timeout = 3.seconds
        )(using appCtx)

        logger.info(s"Found ${allServices.size} total services:")
        allServices.foreach(printService)

      case None =>
        logger.error("Dynamic service discovery not enabled!")
        logger.error("Enable in config: enableDynamicDiscovery = true")
    }
  }

  private def printService(service: ServiceDiscovery.DiscoveryResponse): Unit = {
    logger.info(s"  - ${service.serviceName} (${service.appName})")
    logger.info(s"    Mailbox: ${service.mailboxAddress}")
    logger.info(s"    PID: ${service.unixPid}")
    logger.info(s"    Location: ${service.capabilities.location.user}@${service.capabilities.location.server}")
    logger.info(s"    RPCs: ${service.capabilities.implementsRpc.mkString(", ")}")
    if (service.capabilities.rpcSchemas.nonEmpty) {
      logger.info(s"    Schemas:")
      service.capabilities.rpcSchemas.foreach { case (schema, methods) =>
        logger.info(s"      - $schema: ${methods.mkString(", ")}")
      }
    }
    logger.info("")
  }
}
```

## Testing Plan

### Prerequisites
1. Enable discovery in config: `~/.config/hermes/bootstrap.conf`
   ```hocon
   env = "dev"
   environments {
     dev {
       enableDynamicDiscovery = true
     }
   }
   ```

2. Start NATS server: `nats-server -js`

### Test Sequence

**Terminal 1 - Start PingService:**
```bash
sbt "hermesTest/runMain a8.examples.hermes.PingService"
```

Expected output:
```
✓ Hermes initialized
✓ Ping service registered: ping.v1.Ping
✓ Dynamic service discovery enabled
  Querying for ping.v1 services...
  Found 1 ping services:
    - PingService @ mb_abc123
      PID: 12345
      Location: glen@macbook
```

**Terminal 2 - Run Discovery Test:**
```bash
sbt "hermesTest/runMain a8.examples.hermes.DiscoveryTest"
```

Expected output:
```
✓ Dynamic service discovery enabled

Test 1: Query for ping.v1 services
Found 1 ping services:
  - PingService (scala-hermes)
    Mailbox: mb_abc123
    PID: 12345
    Location: glen@macbook
    RPCs: ping.v1
    Schemas:
      - ping.v1: Ping
```

### Validation Checklist

- [ ] JSON format matches godev exactly (compare with Go service)
- [ ] Request includes `request_id`, `reply_suffix`, `timestamp`
- [ ] Response includes `unixPid`, `processUid`, `capabilities.rpc_schemas`
- [ ] Reply subject uses random suffix: `nefario.discovery.response.{random}`
- [ ] `implements_rpc` filter requires ALL RPCs to match (not just one)
- [ ] Location includes server, user, IP addresses
- [ ] Metadata and extended_metadata populated correctly
- [ ] Service discovery mapping included in response
- [ ] Multiple services can discover each other
- [ ] Wildcard patterns work (e.g., `app_name: "hermes-*"`)

## Files Modified

1. ✏️ `hermes/src/main/scala/a8/hermes/discovery/ServiceDiscovery.scala` - Complete rewrite
2. ➕ `hermes/src/main/scala/a8/hermes/discovery/DiscoveryJson.scala` - NEW: JSON codec
3. ✏️ `hermes/src/main/scala/a8/hermes/bootstrap/HermesBootstrap.scala` - Updated discovery init
4. ✏️ `hermes/src/main/scala/a8/hermes/bootstrap/HermesBootstrapConfig.scala` - Add `enableDynamicDiscovery`
5. ✏️ `examples/hermes-test/src/main/scala/a8/examples/hermes/PingService.scala` - Add discovery test
6. ➕ `examples/hermes-test/src/main/scala/a8/examples/hermes/DiscoveryTest.scala` - NEW: Test client

## Interoperability Notes

Once implemented, Scala services should be discoverable by Go services and vice versa:

**From Go service:**
```go
// Discover Scala ping service
discovery := bootstrap.Discovery()
services, _ := discovery.Query(ctx, discovery.DiscoveryQuery{
    ImplementsRpc: []string{"ping.v1"},
}, 5*time.Second)

for _, svc := range services {
    fmt.Printf("Found: %s at %s (PID %d)\n",
        svc.ServiceName, svc.MailboxAddress, svc.UnixPid)
}
```

**From Scala service:**
```scala
// Discover Go scheduler service
val services = discovery.query(
  DiscoveryQuery(implementsRpc = Seq("scheduler.v1")),
  timeout = 5.seconds
)
```

## Estimated Effort

- **Implementation**: ~2 hours
- **Testing**: ~30 minutes
- **Documentation**: ~15 minutes
- **Total**: ~2.5-3 hours
