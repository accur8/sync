package a8.hermes.discovery

import a8.hermes.core.{Mailbox, MailboxTransport}
import a8.hermes.core.MailboxTransport.Envelope
import a8.hermes.rpc.RpcSchema
import a8.shared.app.Ctx
import a8.common.logging.Logging
import a8.shared.zreplace.Resource
import a8.shared.json
import a8.shared.json.ast.{JsObj, JsStr, JsArr, JsVal}

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.{FiniteDuration, *}

/**
 * Service discovery for Hermes/NATS.
 * Uses pub/sub on nefario.discovery subject.
 */
object ServiceDiscovery extends Logging {

  case class Config(
    mailbox: Mailbox,
    transport: MailboxTransport,
    discoverySubject: String = "nefario.discovery",
    appName: String = "scala-client",
    serviceName: Option[String] = None,
    location: Option[String] = None,
  )

  /**
   * Service information returned from discovery
   */
  case class ServiceInfo(
    processUid: String,
    mailboxAddress: Mailbox.MailboxAddress,
    appName: String,
    serviceName: Option[String],
    location: Option[String],
    implementedRpcs: Seq[String],
    capabilities: Map[String, String],
  )

  /**
   * Query filters for service discovery
   */
  case class Query(
    implementsRpc: Option[String] = None,
    appName: Option[String] = None,
    serviceName: Option[String] = None,
    location: Option[String] = None,
  ) {
    def toJson: JsObj = {
      JsObj(
        List(
          implementsRpc.map("implementsRpc" -> JsStr(_)),
          appName.map("appName" -> JsStr(_)),
          serviceName.map("serviceName" -> JsStr(_)),
          location.map("location" -> JsStr(_)),
        ).flatten.toMap
      )
    }
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

}

class ServiceDiscovery(config: ServiceDiscovery.Config) extends Logging {
  import ServiceDiscovery.*

  // Cache of discovered services
  private val serviceCache = TrieMap.empty[String, ServiceInfo]

  @volatile private var running = false

  /**
   * Start service discovery (subscribe to discovery subject)
   */
  def start()(using ctx: Ctx): Unit = {
    if (running) {
      logger.warn("Service discovery already running")
    } else {
      running = true
      logger.info(s"Starting service discovery on subject: ${config.discoverySubject}")

      // Subscribe to discovery responses
      val discoveryResponseSubject = s"${config.discoverySubject}.response.${config.mailbox.address.value}"

      config.transport.subscribe(discoveryResponseSubject)(using ctx).runForeach { envelope =>
        if (running) {
          processDiscoveryResponse(envelope)
        }
      }
    }
  }

  /**
   * Query for services matching criteria
   *
   * @param query The query filters
   * @param timeout How long to wait for responses
   * @return List of discovered services
   */
  def query(query: Query, timeout: FiniteDuration = 5.seconds)(using ctx: Ctx): Seq[ServiceInfo] = {
    logger.debug(s"Querying for services: $query")

    // Build query message
    val queryJson = query.toJson
    val payload = queryJson.toString.getBytes("UTF-8")

    // Send query to discovery subject
    val headers = Map(
      "sender-mailbox" -> config.mailbox.address.value,
      "reply-to" -> s"${config.discoverySubject}.response.${config.mailbox.address.value}",
    )

    config.transport.publish(
      subject = config.discoverySubject,
      headers = headers,
      payload = payload,
    )(using ctx)

    // Wait for responses to accumulate
    Thread.sleep(timeout.toMillis)

    // Return matching services from cache
    serviceCache.values
      .filter(matchesQuery(_, query))
      .toSeq
  }

  /**
   * Find first service implementing a specific RPC
   */
  def findService(rpcEndpoint: String, timeout: FiniteDuration = 5.seconds)(using ctx: Ctx): Option[ServiceInfo] = {
    query(Query(implementsRpc = Some(rpcEndpoint)), timeout).headOption
  }

  /**
   * Find services by app name
   */
  def findByAppName(appName: String, timeout: FiniteDuration = 5.seconds)(using ctx: Ctx): Seq[ServiceInfo] = {
    query(Query(appName = Some(appName)), timeout)
  }

  /**
   * Register this service for discovery
   *
   * @param implementedRpcs List of RPC endpoints this service implements
   * @param capabilities Additional capabilities map
   */
  def register(implementedRpcs: Seq[String], capabilities: Map[String, String] = Map.empty)(using ctx: Ctx): Unit = {
    logger.info(s"Registering service: ${config.appName}")
    logger.info(s"  Implemented RPCs: ${implementedRpcs.size}")
    logger.info(s"  Capabilities: ${capabilities.size}")

    // Subscribe to discovery queries and respond
    config.transport.subscribe(config.discoverySubject)(using ctx).runForeach { envelope =>
      if (running) {
        respondToQuery(envelope, implementedRpcs, capabilities)(using ctx)
      }
    }
  }

  /**
   * Auto-register from RPC schemas
   */
  def autoRegister(capabilities: Map[String, String] = Map.empty)(using ctx: Ctx): Unit = {
    val implementedRpcs = RpcSchema.all.map(_.name.value)
    register(implementedRpcs, capabilities)(using ctx)
  }

  /**
   * Process an incoming discovery response
   */
  private def processDiscoveryResponse(envelope: Envelope): Unit = {
    try {
      val payload = new String(envelope.payload, "UTF-8")
      val jsonResult = json.parse(payload)
      val jsObj = jsonResult.toOption.get.asInstanceOf[JsObj]

      val processUid = jsObj.values("processUid").asInstanceOf[JsStr].value
      val mailboxAddress = Mailbox.MailboxAddress(jsObj.values("mailboxAddress").asInstanceOf[JsStr].value)
      val appName = jsObj.values("appName").asInstanceOf[JsStr].value
      val serviceName = jsObj.values.get("serviceName").map(_.asInstanceOf[JsStr].value)
      val location = jsObj.values.get("location").map(_.asInstanceOf[JsStr].value)

      val implementedRpcs = jsObj.values.get("implementedRpcs")
        .map(_.asInstanceOf[JsArr].values.map(_.asInstanceOf[JsStr].value))
        .getOrElse(Seq.empty)

      val capabilities = jsObj.values.get("capabilities")
        .map(_.asInstanceOf[JsObj].values.map { case (k, v) => k -> v.asInstanceOf[JsStr].value })
        .getOrElse(Map.empty)

      val serviceInfo = ServiceInfo(
        processUid = processUid,
        mailboxAddress = mailboxAddress,
        appName = appName,
        serviceName = serviceName,
        location = location,
        implementedRpcs = implementedRpcs,
        capabilities = capabilities,
      )

      serviceCache.put(processUid, serviceInfo)
      logger.debug(s"Discovered service: $appName (${processUid})")

    } catch {
      case e: Exception =>
        logger.error("Error processing discovery response", e)
    }
  }

  /**
   * Respond to a discovery query
   */
  private def respondToQuery(
    envelope: Envelope,
    implementedRpcs: Seq[String],
    capabilities: Map[String, String],
  )(using ctx: Ctx): Unit = {
    try {
      // Parse query
      val payload = new String(envelope.payload, "UTF-8")
      val queryJson = if (payload.isEmpty) {
        JsObj(Map.empty)
      } else {
        json.parse(payload).toOption.get.asInstanceOf[JsObj]
      }

      val query = Query(
        implementsRpc = queryJson.values.get("implementsRpc").map(_.asInstanceOf[JsStr].value),
        appName = queryJson.values.get("appName").map(_.asInstanceOf[JsStr].value),
        serviceName = queryJson.values.get("serviceName").map(_.asInstanceOf[JsStr].value),
        location = queryJson.values.get("location").map(_.asInstanceOf[JsStr].value),
      )

      // Check if we match the query
      val ourInfo = ServiceInfo(
        processUid = config.mailbox.address.value, // Use mailbox address as process UID
        mailboxAddress = config.mailbox.address,
        appName = config.appName,
        serviceName = config.serviceName,
        location = config.location,
        implementedRpcs = implementedRpcs,
        capabilities = capabilities,
      )

      if (matchesQuery(ourInfo, query)) {
        // Send response
        val responseJson = JsObj(Map(
          "processUid" -> JsStr(ourInfo.processUid),
          "mailboxAddress" -> JsStr(ourInfo.mailboxAddress.value),
          "appName" -> JsStr(ourInfo.appName),
          "serviceName" -> ourInfo.serviceName.map(JsStr(_)).getOrElse(JsStr("")),
          "location" -> ourInfo.location.map(JsStr(_)).getOrElse(JsStr("")),
          "implementedRpcs" -> JsArr(ourInfo.implementedRpcs.map(JsStr(_)).toList),
          "capabilities" -> JsObj(ourInfo.capabilities.map { case (k, v) => k -> JsStr(v) }),
        ))

        val responsePayload = responseJson.toString.getBytes("UTF-8")
        envelope.headers.get("reply-to") match {
          case Some(replyTo) =>
            config.transport.publish(
              subject = replyTo,
              headers = Map.empty,
              payload = responsePayload,
            )(using ctx)

            logger.debug(s"Sent discovery response to $replyTo")

          case None =>
            logger.warn("Discovery query missing reply-to header")
        }
      }

    } catch {
      case e: Exception =>
        logger.error("Error responding to discovery query", e)
    }
  }

  /**
   * Check if a service matches a query
   */
  private def matchesQuery(service: ServiceInfo, query: Query): Boolean = {
    query.implementsRpc.forall(rpc => service.implementedRpcs.exists(_.equalsIgnoreCase(rpc))) &&
    query.appName.forall(_.equalsIgnoreCase(service.appName)) &&
    query.serviceName.forall(sn => service.serviceName.exists(_.equalsIgnoreCase(sn))) &&
    query.location.forall(loc => service.location.exists(_.equalsIgnoreCase(loc)))
  }

  /**
   * Stop service discovery
   */
  def stop(): Unit = {
    if (running) {
      logger.info("Stopping service discovery")
      running = false
      serviceCache.clear()
    }
  }

  /**
   * Get all cached services
   */
  def cachedServices: Seq[ServiceInfo] = serviceCache.values.toSeq

}
