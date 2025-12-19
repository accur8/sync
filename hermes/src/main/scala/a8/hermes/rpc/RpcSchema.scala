package a8.hermes.rpc

import scala.collection.concurrent.TrieMap

/**
 * RPC schema registry for organizing endpoints.
 * Schemas follow the pattern: {name}.{version}.{method}
 * Example: "process.v1.Shutdown", "nefario.v1.InitMinion"
 *
 * Case-insensitive for handler lookup to match godev behavior.
 */
object RpcSchema {

  /**
   * Normalized schema name for case-insensitive lookups.
   * Example: "Process.V1.Shutdown" -> "process.v1.shutdown"
   */
  case class SchemaName(value: String) {
    val normalized: String = value.toLowerCase

    def name: String = parts(0)
    def version: String = parts(1)
    def method: String = parts(2)

    private lazy val parts: Array[String] = {
      val arr = value.split('.')
      require(arr.length == 3, s"Invalid schema name format: $value (expected {name}.{version}.{method})")
      arr
    }

    override def equals(obj: Any): Boolean = obj match {
      case other: SchemaName => this.normalized == other.normalized
      case _ => false
    }

    override def hashCode(): Int = normalized.hashCode
  }

  object SchemaName {
    def apply(name: String, version: String, method: String): SchemaName = {
      SchemaName(s"$name.$version.$method")
    }
  }

  /**
   * Schema metadata for an RPC endpoint
   */
  case class Schema(
    name: SchemaName,
    description: Option[String] = None,
    requiresAuth: Boolean = true,
  )

  /**
   * Global schema registry
   */
  private val schemas = TrieMap.empty[SchemaName, Schema]

  /**
   * Register a schema
   */
  def register(schema: Schema): Unit = {
    schemas.put(schema.name, schema)
  }

  /**
   * Register a schema by parts
   */
  def register(
    name: String,
    version: String,
    method: String,
    description: Option[String] = None,
    requiresAuth: Boolean = true,
  ): Schema = {
    val schema = Schema(
      name = SchemaName(name, version, method),
      description = description,
      requiresAuth = requiresAuth,
    )
    register(schema)
    schema
  }

  /**
   * Lookup a schema by name (case-insensitive)
   */
  def get(name: SchemaName): Option[Schema] = {
    schemas.get(name)
  }

  /**
   * Lookup a schema by string (case-insensitive)
   */
  def get(name: String): Option[Schema] = {
    get(SchemaName(name))
  }

  /**
   * List all registered schemas
   */
  def all: Seq[Schema] = schemas.values.toSeq

  /**
   * List schemas for a specific service
   */
  def forService(serviceName: String): Seq[Schema] = {
    val normalized = serviceName.toLowerCase
    schemas.values.filter(_.name.name.toLowerCase == normalized).toSeq
  }

  /**
   * Clear all schemas (useful for testing)
   */
  def clear(): Unit = {
    schemas.clear()
  }

}
