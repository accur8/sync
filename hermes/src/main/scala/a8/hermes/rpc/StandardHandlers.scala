package a8.hermes.rpc

import a8.shared.json.ast.{JsObj, JsStr, JsArr}
import a8.shared.json
import a8.shared.SharedImports.jsonCodecOps

/**
 * Standard RPC handlers that should be registered by all Hermes services.
 *
 * These handlers implement the standard protocol expected by the service discovery
 * system and match the godev standard handlers.
 */
object StandardHandlers {

  /**
   * process.v1.Ping - Standard ping endpoint
   *
   * Echoes back a simple response with timestamp and process info.
   * This is the most basic health check endpoint.
   */
  def processPing(mailboxAddress: String): RpcHandler = {
    RpcHandler(
      name = "process",
      version = "v1",
      method = "Ping",
      description = Some("Process ping endpoint - echoes payload with timestamp"),
      requiresAuth = false,
    ) { (requestBytes, ctx) =>
      val payload = if (requestBytes.isEmpty) "pong" else new String(requestBytes, "UTF-8")
      val sender = ctx.senderMailbox.map(_.value).getOrElse("unknown")

      // Simple JSON response: payload + timestamp + process info
      val response = JsObj.from(
        "payload" -> JsStr(payload),
        "timestamp" -> JsStr(java.time.Instant.now().toString),
        "processId" -> JsStr(mailboxAddress),
      )

      response.compactJson.getBytes("UTF-8")
    }
  }

  /**
   * discovery.v1.ListSchemas - List all registered RPC schemas
   *
   * Returns a list of all schema names registered in this process.
   */
  def discoveryListSchemas(): RpcHandler = {
    RpcHandler(
      name = "discovery",
      version = "v1",
      method = "ListSchemas",
      description = Some("List all registered RPC schemas"),
      requiresAuth = false,
    ) { (requestBytes, ctx) =>
      // Get all registered schemas from the global registry
      val schemas = RpcSchema.all

      // Group by name.version to get schema names
      val schemaNames = schemas
        .map(schema => s"${schema.name.name}.${schema.name.version}")
        .distinct
        .sorted

      // Return as JSON array
      val response = JsObj.from(
        "schemas" -> JsArr(schemaNames.map(JsStr(_)).toList)
      )

      response.compactJson.getBytes("UTF-8")
    }
  }

  /**
   * discovery.v1.GetSchema - Get details for a specific schema
   *
   * Returns method names and details for a given schema (e.g., "process.v1").
   */
  def discoveryGetSchema(): RpcHandler = {
    RpcHandler(
      name = "discovery",
      version = "v1",
      method = "GetSchema",
      description = Some("Get details for a specific RPC schema"),
      requiresAuth = false,
    ) { (requestBytes, ctx) =>
      // Parse request to get schema name
      val requestStr = new String(requestBytes, "UTF-8")
      val request = json.parse(requestStr).toOption.get.asInstanceOf[JsObj]
      val schemaName = request.values.get("schema").map(_.asInstanceOf[JsStr].value).getOrElse("")

      // Split schema name into parts (e.g., "process.v1" -> name="process", version="v1")
      val parts = schemaName.split('.')
      val response = if (parts.length != 2) {
        JsObj.from(
          "error" -> JsStr(s"Invalid schema name format: $schemaName (expected {name}.{version})")
        )
      } else {
        val name = parts(0)
        val version = parts(1)

        // Find all methods for this schema
        val schemas = RpcSchema.all
          .filter(s => s.name.name.equalsIgnoreCase(name) && s.name.version.equalsIgnoreCase(version))

        if (schemas.isEmpty) {
          JsObj.from(
            "error" -> JsStr(s"Schema not found: $schemaName")
          )
        } else {
          // Build method list
          val methods = schemas.map { schema =>
            JsObj.from(
              "name" -> JsStr(schema.name.method),
              "requiresAuth" -> json.ast.JsBool(schema.requiresAuth),
              "description" -> JsStr(schema.description.getOrElse("")),
            )
          }

          JsObj.from(
            "schema" -> JsStr(schemaName),
            "methods" -> JsArr(methods.toList),
          )
        }
      }

      response.compactJson.getBytes("UTF-8")
    }
  }

  /**
   * Register all standard handlers on an RPC server.
   *
   * Call this during service bootstrap to ensure your service supports
   * the standard protocol.
   */
  def registerAll(rpcServer: RpcServer, mailboxAddress: String): Unit = {
    rpcServer.register(processPing(mailboxAddress))
    rpcServer.register(discoveryListSchemas())
    rpcServer.register(discoveryGetSchema())
  }

}
