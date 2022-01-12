package a8.sync.auditlog

import a8.shared.CompanionGen
import a8.sync.{FileUtil, Utils}
import a8.sync.auditlog.AuditLogSync.TableSync

import java.io.File
import sttp.model.Uri
import a8.shared.SharedImports._
import a8.shared.jdbcf.DatabaseConfig
import a8.sync.auditlog.MxConfig.MxConfig

object Config extends MxConfig {

  def load(rootDir: File): Config =
    try {
      val configDir = new File(rootDir.getCanonicalPath, "config")
      if ( configDir.isDirectory && !configDir.exists() ) {
        configDir.mkdir()
      }
      val configFile = new File( configDir, "config.json")

      FileUtil.loadJsonFile[Config](configFile)
    } catch {
      case e: Exception =>
        throw new RuntimeException("unable to load config.json", e)
    }

}

@CompanionGen(jsonCodec = true)
case class Config(
  sourceDatabase: DatabaseConfig,
  targetDatabase: DatabaseConfig,
  tableSyncs: List[TableSync]
)

