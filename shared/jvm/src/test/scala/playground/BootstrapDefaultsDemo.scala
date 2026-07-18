package playground

import a8.shared.{CascadingHocon, ConfigMojo, HoconOps}
import a8.shared.ConfigMojoOps.impl.ConfigMojoRoot
import a8.shared.app.BootstrapConfig.*
import a8.shared.json.JsonReader.JsonReaderOptions

import java.nio.file.{Files, Path}

@scala.annotation.nowarn("cat=deprecation")
object BootstrapDefaultsDemo extends App {

  implicit val jsonReaderOptions: JsonReaderOptions = JsonReaderOptions.NoLogWarnings

  val tempDir = Files.createTempDirectory("bootstrap-defaults-test")

  val configDir = tempDir.resolve("config")
  Files.createDirectories(configDir)

  val hoconContent =
    """
      |global_bootstrap {
      |  logsDir = "/var/log/global"
      |  cacheDir = "/var/cache/global"
      |}
      |
      |testapp {
      |  bootstrap {
      |    logsDir = "/var/log/testapp"
      |  }
      |}
    """.stripMargin

  Files.writeString(configDir.resolve("config.hocon"), hoconContent)

  val cascading = CascadingHocon.loadConfigsInDirectory(configDir, recurse = false, resolve = true)
  val configMojoRoot = ConfigMojoRoot(
    hoconValue = cascading.config.root(),
    root = cascading,
  )

  val appName = AppName("testapp")
  val configMojo = configMojoRoot(appName.value)

  // layer 1 - hardcoded defaults
  val hardcodedDefaults = BootstrapConfigDto.default

  // layer 2 - programmatic app defaults (simulating bootstrapDefaults override in BootstrappedIOApp)
  val appDefaults = BootstrapConfigDto(
    dataDir = Some("/data/from-app-defaults"),
    tempDir = Some("/tmp/from-app-defaults"),
    cacheDir = Some("/cache/from-app-defaults"),
  ).copy(source = Some("programmatic appDefaults"))

  // layer 3 - global_bootstrap from config.hocon
  val globalBootstrapDto =
    BootstrapConfigDto.fromConfigMojo(configMojoRoot("global_bootstrap"))
      .copy(source = Some("config.hocon - global_bootstrap"))

  // layer 4 - <appName>.bootstrap from config.hocon
  val bootstrapDto =
    BootstrapConfigDto.fromConfigMojo(configMojo("bootstrap"))
      .copy(source = Some("config.hocon - testapp.bootstrap"))

  val dtoChain = List(hardcodedDefaults, globalBootstrapDto, appDefaults, bootstrapDto)
  val resolved = dtoChain.reduce(_ + _).copy(source = Some("resolved"))

  println("=== Bootstrap Defaults Resolution Chain ===")
  println()
  dtoChain.foreach { dto =>
    println(s"  ${dto.source.getOrElse("unknown")}:")
    println(s"    logsDir   = ${dto.logsDir}")
    println(s"    cacheDir  = ${dto.cacheDir}")
    println(s"    dataDir   = ${dto.dataDir}")
    println(s"    tempDir   = ${dto.tempDir}")
    println(s"    configDir = ${dto.configDir}")
    println()
  }

  println(s"=== Resolved ===")
  println(s"  logsDir   = ${resolved.logsDir}")
  println(s"  cacheDir  = ${resolved.cacheDir}")
  println(s"  dataDir   = ${resolved.dataDir}")
  println(s"  tempDir   = ${resolved.tempDir}")
  println(s"  configDir = ${resolved.configDir}")
  println()

  // verify the override priority
  var passed = true

  def check(name: String, actual: Option[String], expected: String): Unit = {
    if (actual.contains(expected)) {
      println(s"  PASS: $name = $expected")
    } else {
      println(s"  FAIL: $name expected $expected but got $actual")
      passed = false
    }
  }

  println("=== Assertions ===")

  // logsDir: hardcoded="logs", global="/var/log/global", app="/var/log/testapp" => app wins
  check("logsDir", resolved.logsDir, "/var/log/testapp")

  // cacheDir: hardcoded="cache", global="/var/cache/global", appDefaults="/cache/from-app-defaults" => appDefaults wins over global
  check("cacheDir", resolved.cacheDir, "/cache/from-app-defaults")

  // dataDir: hardcoded="data", appDefaults="/data/from-app-defaults" => appDefaults wins (no hocon override)
  check("dataDir", resolved.dataDir, "/data/from-app-defaults")

  // tempDir: hardcoded="temp", appDefaults="/tmp/from-app-defaults" => appDefaults wins (no hocon override)
  check("tempDir", resolved.tempDir, "/tmp/from-app-defaults")

  // configDir: only hardcoded="config" => hardcoded default wins
  check("configDir", resolved.configDir, "config")

  println()
  if (passed) {
    println("All assertions passed.")
  } else {
    println("SOME ASSERTIONS FAILED")
    sys.exit(1)
  }

  // cleanup
  Files.deleteIfExists(configDir.resolve("config.hocon"))
  Files.deleteIfExists(configDir)
  Files.deleteIfExists(tempDir)

}
