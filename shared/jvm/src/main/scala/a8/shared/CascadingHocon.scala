package a8.shared


import java.nio.file.{Path, Paths}
import HoconOps.*
import com.typesafe.config.{Config, ConfigMergeable, ConfigObject, ConfigOrigin, ConfigRenderOptions, ConfigValue, ConfigValueType}
import SharedImports.*
import a8.shared.ops.PathOps

import java.net.URL
import java.util
import SharedImports.{given, *}

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

    def tree(d: Path): Vector[Path] = {
      d.parentOpt() match {
        case None =>
          Vector(d)
        case Some(p) =>
          d +: tree(p)
      }
    }

    val normalizedPath = dir.toAbsolutePath.normalize()

    val chain: Vector[Path] =
      if ( recurse ) {
        tree(normalizedPath) ++ impl.userConfigs
      } else {
        Vector(normalizedPath)
      }

    impl.loadConfigsInDirectory(chain, resolve)

  }

  object impl {

    lazy val userConfigs = {
      val config0 = PathOps.userHome.resolve(".a8")
      val config1 = PathOps.userHome.resolve("config/a8")
      Vector(config0, config1)
        .filter(_.exists())
        .map(_.toRealPath())
        .distinct
    }

    def loadConfigsInDirectory(chain: Vector[Path], resolve: Boolean = true): CascadingHocon = {

      val dir = chain.head
      val normalizedPath = dir.toAbsolutePath.normalize()

      val parentConfig =
        chain.tail match {
          case v if v.nonEmpty =>
            loadConfigsInDirectory(v, false)
          case _ =>
            empty
        }

      val filesToTry =
        List("generated.properties", "config.hocon")
          .map(dir.resolve)
          .filter(_.exists())
          .map(_.toRealPath())
          .distinct

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
        if (resolve)
          config.resolve
        else
          config

      resolvedConfig
        .appendCheckedDir(normalizedPath)

    }
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
