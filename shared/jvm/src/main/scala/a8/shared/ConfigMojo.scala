package a8.shared



import a8.shared.ConfigMojoOps.impl
import com.typesafe.{config => hocon}

import scala.language.dynamics
import SharedImports._
import a8.shared
import a8.shared.ConfigMojoOps.impl.{ConfigMojoEmpty, ConfigMojoRoot, ConfigMojoValue}
import a8.shared.json.JsonReader.{JsonReaderOptions, ReadResult}
import a8.shared.json.ZJsonReader.ZJsonReaderOptions
import a8.shared.json.{JsonCodec, JsonReader, ReadError}
import a8.shared.json.ast.{JsDoc, JsNothing, JsVal}
import com.typesafe.config.{ConfigMergeable, ConfigValue}
import zio.{Task, ZIO}

import java.nio.file.Paths

object ConfigMojo  {

  lazy val root: ConfigMojo = {
    val ch = CascadingHocon.loadConfigsInDirectory(Paths.get("./config/"), true, true)
    impl.ConfigMojoRoot(
      hoconValue = ch.config.root(),
      root = ch,
    )
  }

//  def apply(): ConfigMojo = root
//  def apply(value: hocon.ConfigValue): ConfigMojo = new ConfigMojo(name = None, parent = None, hoconValueOpt = Some(value))

}


object ConfigMojoOps {

  object impl {

    case class ConfigMojoRoot(
      hoconValue: hocon.ConfigValue,
      root: CascadingHocon
    ) extends ConfigMojo(None, None, Some(hoconValue), Some(root))

    case class ConfigMojoValue(
      name: String,
      hoconValue: hocon.ConfigValue,
      parent: ConfigMojo,
    ) extends ConfigMojo(name.toSome, Some(parent), Some(hoconValue), None)

    case class ConfigMojoEmpty(
      name: String,
      parent: ConfigMojo,
    ) extends ConfigMojo(name.toSome, Some(parent), None, None) {
      override def hoconValue: ConfigValue = CascadingHocon.emptyHocon.root()
    }
  }

}


abstract class ConfigMojo(name: Option[String], parent: Option[ConfigMojo], hoconValueOpt: Option[hocon.ConfigValue], root: Option[CascadingHocon]) extends Dynamic { outer =>

//abstract class ConfigMojo(name: Option[String], parent: Option[ConfigMojo], hoconValueOpt: Option[hocon.ConfigValue], root: Option[CascadingHocon]) { outer =>

  def hoconValue: ConfigValue

  object __internal__ {
    def name = outer.name
    def ancestry: IndexedSeq[ConfigMojo] = parent.toIndexedSeq.flatMap(_.__internal__.ancestry) :+ outer
    def path: String = ancestry.flatMap(_.__internal__.name).mkString(".")
  }
  import __internal__._

  def as[A : JsonCodec](implicit jsonReaderOptions: JsonReaderOptions): A = {
    outer.asReadResult[A] match {
      case ReadResult.Success(v, _, _, _) =>
        v
      case ReadResult.Error(re, _, _) =>
        sys.error(re.prettyMessage)
    }
  }

  def asF[A: JsonCodec](implicit jsonReaderOptions: JsonReaderOptions): Task[A] =
    asReadResult[A] match {
      case ReadResult.Success(v, _, _, _) =>
        zsucceed(v)
      case ReadResult.Error(re, _, _) =>
        zfail(re.asException)
    }

  def asReadResult[A : JsonCodec](implicit jsonReaderOptions: JsonReaderOptions): ReadResult[A] =
    HoconOps.impl.internalReadResult[A](hoconValueOpt.getOrElse(CascadingHocon.emptyConfigObject))

  def selectDynamic(name: String): ConfigMojo = apply(name)

  def apply(name: String): ConfigMojo = {
    def notFoundValue = new impl.ConfigMojoEmpty(name, this)
    hoconValueOpt match {
      case Some(co) =>
        co match {
          case co: hocon.ConfigObject =>
            val path = name.split("\\.").toList
            val resolvedValue: Option[hocon.ConfigValue] =
              path
                .foldLeft(some[hocon.ConfigValue](co)) { case (cvo, p) =>
                  cvo
                    .flatMap {
                      case co: hocon.ConfigObject =>
                        Option(co.get(p))
                      case _ =>
                        None
                    }
                }
            resolvedValue match {
              case None =>
                notFoundValue
              case Some(v) =>
                new impl.ConfigMojoValue(name, v, this  )
            }
          case _ =>
            notFoundValue
        }
      case _ =>
        notFoundValue
    }
  }

  def mojoRoot: ConfigMojoRoot =
    this match {
      case cmr: ConfigMojoRoot =>
        cmr
      case ConfigMojoValue(_, _, parent) =>
        parent.mojoRoot
      case ConfigMojoEmpty(_, parent) =>
        parent.mojoRoot
    }

  override def toString: String =
    s"${path} => ${as[JsDoc].prettyJson}"

}


