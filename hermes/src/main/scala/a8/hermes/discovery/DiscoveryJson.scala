package a8.hermes.discovery

import a8.shared.json.ast.{JsObj, JsStr, JsArr, JsVal, JsNum, JsBool}
import java.time.Instant

/**
 * JSON serialization/deserialization for discovery messages.
 * Matches godev's JSON format exactly for interoperability.
 */
object DiscoveryJson {
  import ServiceDiscovery.*

  // ===== Request Serialization =====

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

  // ===== Response Serialization =====

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

  // ===== Request Parsing =====

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

  // ===== Response Parsing =====

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
