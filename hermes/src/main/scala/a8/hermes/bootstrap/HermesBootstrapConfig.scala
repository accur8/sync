package a8.hermes.bootstrap

import a8.hermes.bootstrap.MxHermesBootstrapConfig.MxHermesAppConfig
import a8.common.logging.Logging
import a8.shared.{CompanionGen, FileSystem}
import a8.shared.json.ast
import com.typesafe.config.{Config, ConfigFactory}

import java.nio.file.{Path, Paths}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*

/**
 * Application-specific Hermes configuration
 * This is passed by the application and contains app-specific settings
 */
@CompanionGen
case class HermesAppConfig(
  namedMailbox: Option[String] = None,
  appName: Option[String] = None,
)

object HermesAppConfig extends MxHermesAppConfig

object HermesBootstrapConfig extends Logging {

  /**
   * Load bootstrap config, matching godev's canonical loader (a8-mod/bootstrap.go +
   * ConfigSearchPaths). Source of truth is `~/.config/a8/bootstrap.conf`.
   *
   * Search order (first existing wins): $BOOTSTRAP_CONFIG_FILE, ~/.config/a8/bootstrap.conf,
   * /config/a8/bootstrap.conf. Environment selection: $BOOTSTRAP_ENV overrides the file's `env`.
   * Service mappings come from the env block's `nameMappings` (legacy `namedMailboxes` accepted);
   * if the block sets `namingEnvironment`, mappings are resolved dynamically at bootstrap time by
   * querying the naming service over NATS (see HermesBootstrap), with `nameMappings` as fallback.
   */
  def load(env: Option[String] = None): HermesBootstrapConfig = {
    val configPath =
      searchPaths
        .find(_.toFile.exists())
        .getOrElse(throw new RuntimeException(s"Bootstrap config file not found in any of: ${searchPaths.mkString(", ")}"))

    val config = ConfigFactory.parseFile(configPath.toFile).resolve()
    fromConfig(config, env)
  }

  private def searchPaths: Seq[Path] = {
    val home = Paths.get(System.getProperty("user.home"))
    val envOverride = sys.env.get("BOOTSTRAP_CONFIG_FILE").map(Paths.get(_))
    envOverride.toSeq ++ Seq(
      home.resolve(".config/a8/bootstrap.conf"),
      Paths.get("/config/a8/bootstrap.conf"),
    )
  }

  private def fromConfig(config: Config, envOverride: Option[String] = None): HermesBootstrapConfig = {
    // Environment selection priority: caller-supplied env > $BOOTSTRAP_ENV > file's `env` field.
    // Supports both a multi-env file (env + environments{}) and a single-env file (fields at root).
    val hasEnvironments = config.hasPath("environments")
    val hasEnv = config.hasPath("env")

    val envConfig: Config =
      if (hasEnvironments || hasEnv) {
        val envName =
          envOverride
            .orElse(sys.env.get("BOOTSTRAP_ENV"))
            .getOrElse(if (hasEnv) config.getString("env") else "")
        if (envName.isEmpty)
          throw new RuntimeException("no bootstrap environment selected (env field empty and $BOOTSTRAP_ENV unset)")
        if (!config.hasPath(s"environments.$envName"))
          throw new RuntimeException(s"bootstrap environment '$envName' not found in config")
        config.getConfig(s"environments.$envName")
      } else {
        config // single-env file: fields at the root
      }

    // NATS transport: the structured `nats { servers, user, password }` block is the
    // canonical form (matches godev's a8-mod/bootstrap.go resolveNats); the flat
    // `natsUrl` string is the deprecated form, still accepted. Both set is ambiguous.
    val natsUrl = resolveNatsUrl(envConfig)

    // godev key is `sshPrivateKeyPath`; accept legacy `sshKeyPath` too
    val sshKeyPath =
      if (envConfig.hasPath("sshPrivateKeyPath")) Some(expandHome(envConfig.getString("sshPrivateKeyPath")))
      else if (envConfig.hasPath("sshKeyPath")) Some(expandHome(envConfig.getString("sshKeyPath")))
      else None

    val authServiceMailbox =
      if (envConfig.hasPath("authServiceMailbox")) Some(envConfig.getString("authServiceMailbox")) else None

    // canonical key is `nameMappings`; accept legacy `namedMailboxes`
    def readMap(key: String): Map[String, String] =
      if (envConfig.hasPath(key)) {
        val c = envConfig.getConfig(key)
        c.entrySet().asScala.map(e => e.getKey -> c.getString(e.getKey)).toMap
      } else Map.empty[String, String]

    val nameMappings = {
      val primary = readMap("nameMappings")
      if (primary.nonEmpty) primary else readMap("namedMailboxes")
    }

    val namingEnvironment =
      if (envConfig.hasPath("namingEnvironment")) Some(envConfig.getString("namingEnvironment")) else None

    val discoverySubject =
      if (envConfig.hasPath("discoverySubject")) envConfig.getString("discoverySubject") else "continuum.discovery"

    HermesBootstrapConfig(
      natsUrl = natsUrl,
      sshKeyPath = sshKeyPath,
      authServiceMailbox = authServiceMailbox,
      namedMailboxes = nameMappings,
      namingEnvironment = namingEnvironment,
      discoverySubject = discoverySubject,
    )
  }

  private def expandHome(path: String): String = {
    if (path.startsWith("~/")) {
      System.getProperty("user.home") + path.substring(1)
    } else {
      path
    }
  }

  /**
   * Reconcile the structured `nats { servers, user, password }` block and the
   * deprecated flat `natsUrl` string into a single connection URL, matching
   * godev's a8-mod/bootstrap.go resolveNats:
   *   - both set       -> error (ambiguous transport)
   *   - nats block set  -> compose into a comma-joined nats://[user[:password]@]host:port URL
   *   - natsUrl only    -> keep it (deprecated)
   * At least one must be present (this loader is the NATS-transport path).
   */
  private def resolveNatsUrl(envConfig: Config): String = {
    val hasBlock =
      envConfig.hasPath("nats.servers") && !envConfig.getStringList("nats.servers").isEmpty
    val hasFlat = envConfig.hasPath("natsUrl")
    (hasBlock, hasFlat) match {
      case (true, true) =>
        throw new RuntimeException("set either the `nats { ... }` block or the deprecated `natsUrl`, not both")
      case (true, _) =>
        composeNatsUrl(envConfig.getConfig("nats"))
      case (_, true) =>
        logger.warn("`natsUrl` is deprecated; migrate to the `nats { servers, user, password }` block")
        envConfig.getString("natsUrl")
      case _ =>
        throw new RuntimeException("no NATS transport configured: set a `nats { servers, user, password }` block (or the deprecated `natsUrl`)")
    }
  }

  /**
   * Compose a comma-joined nats://[user[:password]@]host:port URL from a `nats`
   * block. Servers without an explicit port get the NATS default (4222); any
   * nats:// / tls:// scheme prefix is stripped so it is not doubled. Mirrors
   * godev's NatsBlock.composeURL.
   */
  private def composeNatsUrl(nats: Config): String = {
    val servers = nats.getStringList("servers").asScala.toList
    val user = if (nats.hasPath("user")) nats.getString("user") else ""
    val password = if (nats.hasPath("password")) nats.getString("password") else ""
    val userinfo =
      if (user.isEmpty) ""
      else if (password.isEmpty) s"$user@"
      else s"$user:$password@"
    val parts =
      servers
        .map(_.trim)
        .filter(_.nonEmpty)
        .map { raw =>
          val noScheme = raw.stripPrefix("nats://").stripPrefix("tls://")
          val hostPort = if (noScheme.contains(":")) noScheme else s"$noScheme:4222"
          s"nats://$userinfo$hostPort"
        }
    if (parts.isEmpty) throw new RuntimeException("nats.servers has no usable entries")
    parts.mkString(",")
  }

}

@CompanionGen
case class HermesBootstrapConfig(
  natsUrl: String,
  sshKeyPath: Option[String] = None,
  authServiceMailbox: Option[String] = None,
  namedMailboxes: Map[String, String] = Map.empty,
  // The naming service (naming.v1.GetEnvironment over NATS) is always queried for
  // service->mailbox mappings; static `namedMailboxes` above are merged as overrides.
  // When None (the common case), an empty environment name is sent and the server
  // returns its default name set, so clients don't have to get an environment name
  // right. Set this only to request a specific (non-default) environment's set.
  namingEnvironment: Option[String] = None,
  discoverySubject: String = "continuum.discovery",
  autoRenewAuth: Boolean = true,
)
