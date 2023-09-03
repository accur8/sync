package a8.shared


import java.nio.file.{Path, Paths}
import HoconOps._
import com.typesafe.config.{Config, ConfigMergeable, ConfigObject, ConfigOrigin, ConfigRenderOptions, ConfigValue, ConfigValueType}
import SharedImports._
import a8.shared.app.Logging

import java.net.URL
import java.util

object CascadingHocon {

  lazy val emptyConfigObject: ConfigObject = emptyHocon.root()

  lazy val emptyHocon: Config = parseHocon("")

  private lazy val empty =
    CascadingHocon(
      parseHocon(""),
      sources = Vector.empty[Path],
      directoriesChecked = Vector.empty[Path],
      parent = None,
    )


  def loadConfigsInDirectory(dir: Path, recurse: Boolean = true, resolve: Boolean = true): CascadingHocon = {

    val normalizedPath = dir.toAbsolutePath.normalize()

    val parentConfig =
      normalizedPath.parentOpt() match {
        case Some(parentDir) if recurse =>
          loadConfigsInDirectory(parentDir, true, false)
        case None if recurse =>
          loadConfigsInDirectory(FileSystem.userHome.subdir(".config/a8").asNioPath, false, false)
        case _ =>
          empty
      }

    val filesToTry = List("config.hocon").map(dir.resolve)
    val config =
      filesToTry
        .filter(_.isFile())
        .map { path =>
//          logger.info(s"loading config from ${path.toFile.getCanonicalPath}")
          HoconOps.impl.loadConfig(path) -> path
        }
        .foldLeft(parentConfig) { case (acc, c) =>
          acc.appendConfig(c._1, c._2)
        }

    val resolvedConfig =
      if ( resolve )
        config.resolve
      else
        config

    resolvedConfig
      .appendCheckedDir(normalizedPath)

  }

}

case class CascadingHocon(config: Config, sources: Vector[Path], directoriesChecked: Vector[Path], parent: Option[CascadingHocon]) {

  def resolve: CascadingHocon =
    copy(
      config = config.resolve()
    )

  def appendCheckedDir(path: Path): CascadingHocon =
    copy(
      directoriesChecked = directoriesChecked :+ path
    )

  def appendConfig(config: Config, path: Path): CascadingHocon =
    copy(
      config = config.withFallback(this.config),
      sources = sources :+ path
    )

}
