package a8.hermes.bootstrap

import a8.shared.{CompanionGen, FileSystem}
import a8.shared.json.ast
import com.typesafe.config.{Config, ConfigFactory}

import java.nio.file.{Path, Paths}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*

object HermesBootstrapConfig {

  /**
   * Load bootstrap config from ~/.config/hermes/bootstrap.conf
   * This follows the same pattern as godev's bootstrap.go
   */
  def load(): HermesBootstrapConfig = {
    val configPath = sys.env.get("BOOTSTRAP_CONFIG_FILE")
      .map(Paths.get(_))
      .getOrElse {
        val home = Paths.get(System.getProperty("user.home"))
        home.resolve(".config/hermes/bootstrap.conf")
      }

    if (!configPath.toFile.exists()) {
      throw new RuntimeException(s"Bootstrap config file not found: $configPath")
    }

    val config = ConfigFactory.parseFile(configPath.toFile).resolve()
    fromConfig(config)
  }

  private def fromConfig(config: Config): HermesBootstrapConfig = {
    // Read the environment selector (dev, prod, etc.)
    val env = config.getString("env")

    // Navigate to the environment-specific config
    val envConfig = config.getConfig(s"environments.$env")

    val natsUrl = envConfig.getString("natsUrl")

    val sshKeyPath = if (envConfig.hasPath("sshKeyPath")) {
      Some(expandHome(envConfig.getString("sshKeyPath")))
    } else None

    val authServiceMailbox = if (envConfig.hasPath("authServiceMailbox")) {
      Some(envConfig.getString("authServiceMailbox"))
    } else None

    val namedMailboxes = if (envConfig.hasPath("namedMailboxes")) {
      val mailboxConfig = envConfig.getConfig("namedMailboxes")
      mailboxConfig.entrySet().asScala.map { entry =>
        entry.getKey -> mailboxConfig.getString(entry.getKey)
      }.toMap
    } else Map.empty[String, String]

    val discoverySubject = if (envConfig.hasPath("discoverySubject")) {
      envConfig.getString("discoverySubject")
    } else "nefario.discovery"

    val appName = if (envConfig.hasPath("appName")) {
      Some(envConfig.getString("appName"))
    } else None

    HermesBootstrapConfig(
      natsUrl = natsUrl,
      sshKeyPath = sshKeyPath,
      authServiceMailbox = authServiceMailbox,
      namedMailboxes = namedMailboxes,
      discoverySubject = discoverySubject,
      appName = appName,
    )
  }

  private def expandHome(path: String): String = {
    if (path.startsWith("~/")) {
      System.getProperty("user.home") + path.substring(1)
    } else {
      path
    }
  }

}

@CompanionGen
case class HermesBootstrapConfig(
  natsUrl: String,
  sshKeyPath: Option[String] = None,
  authServiceMailbox: Option[String] = None,
  namedMailboxes: Map[String, String] = Map.empty,
  discoverySubject: String = "nefario.discovery",
  appName: Option[String] = None,
  autoRenewAuth: Boolean = true,
)
