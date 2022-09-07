package a8.shared



import a8.shared.ConfigMojoOps.{ReadResult, impl}
import com.typesafe.{config => hocon}
import a8.shared.ConfigMojoOps.ReadResult.NoValue

import scala.language.dynamics
import SharedImports._
import a8.shared.ConfigMojoOps.impl.{ConfigMojoEmpty, ConfigMojoRoot, ConfigMojoValue}
import a8.shared.json.JsonCodec
import a8.shared.json.ast.JsVal
import com.typesafe.config.{ConfigMergeable, ConfigValue}
import zio.{Task, ZIO}

import java.nio.file.Paths

object ConfigMojo  {

  private lazy val root: ConfigMojo = {
    val ch = CascadingHocon.loadConfigsInDirectory(Paths.get("./config/"), true, true)
    impl.ConfigMojoRoot(
      hoconValue = ch.config.root(),
      root = ch,
    )
  }

  def apply(): ConfigMojo = root
//  def apply(value: hocon.ConfigValue): ConfigMojo = new ConfigMojo(name = None, parent = None, hoconValueOpt = Some(value))

}


object ConfigMojoOps {

  sealed trait ReadResult[A] {
    def valueOpt: Option[A] = None
  }
  object ReadResult {
    case class Value[A](v: A) extends ReadResult[A] {
      override def valueOpt: Option[A] = Some(v)
    }
    case class Error[A](msg: String) extends ReadResult[A]
    case class NoValue[A]() extends ReadResult[A]
  }

  trait Reads[A] {
    def read(value: Option[hocon.ConfigValue]): ReadResult[A]
  }

  object Reads {

    def apply[A](fn: hocon.ConfigValue => ReadResult[A]): Reads[A] =
      new Reads[A] {
        def read(value: Option[hocon.ConfigValue]): ReadResult[A] =
          value match {
            case None =>
              ReadResult.NoValue()
            case Some(cv) =>
              fn(cv)
          }
      }

    implicit def reads[A : JsonCodec]: Reads[A] =
      apply[A] { value =>
        val jsv = HoconOps.impl.toJsVal(value)
        JsonCodec[A].read(jsv.toDoc) match {
          case Right(value) =>
            ReadResult.Value(value)
          case Left(re) =>
            ReadResult.Error(re.prettyMessage)
        }
      }

    implicit val readsJsVal: Reads[JsVal] =
      apply[JsVal] { value =>
        val jsv = HoconOps.impl.toJsVal(value)
        ReadResult.Value(jsv)
      }

    implicit def optionReads[A](implicit readsA: Reads[A]): Reads[Option[A]] =
      new Reads[Option[A]] {
        def read(value: Option[hocon.ConfigValue]): ReadResult[Option[A]] = {
          def impl(rr: ReadResult[Option[A]]): ReadResult[Option[A]] = rr
          value
            .map { v =>
              readsA.read(value) match {
                case ReadResult.Value(v) =>
                  ReadResult.Value(some(v))
                case ReadResult.NoValue() =>
                  ReadResult.Value(none[A])
                case ReadResult.Error(msg) =>
                  ReadResult.Error[Option[A]](msg)
              }
            }
            .getOrElse(ReadResult.Value(none[A]))
        }
      }

  }

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

  def hoconValue: ConfigValue

  object __internal__ {
    def name = outer.name
    def ancestry: IndexedSeq[ConfigMojo] = parent.toIndexedSeq.flatMap(_.__internal__.ancestry) :+ outer
    def path: String = ancestry.flatMap(_.__internal__.name).mkString(".")
  }
  import __internal__._

  def as[A : ConfigMojoOps.Reads]: A = {
    outer.asReadResult[A] match {
      case ReadResult.Value(v) =>
        v
      case ReadResult.Error(msg) =>
        sys.error(s"error at ${path} -- ${msg}")
      case ReadResult.NoValue() =>
        sys.error(s"no value at ${path}")
    }
  }

  def asF[A: ConfigMojoOps.Reads]: Task[A] =
    asReadResult[A] match {
      case ReadResult.Value(v) =>
        zsucceed(v)
      case ReadResult.Error(msg) =>
        zfail(new RuntimeException(s"error at ${path} -- ${msg}"))
      case ReadResult.NoValue() =>
        zfail(new RuntimeException(s"no value at ${path}"))
    }

  def asReadResult[A : ConfigMojoOps.Reads]: ReadResult[A] = {
    val reads = implicitly[ConfigMojoOps.Reads[A]]
    reads.read(hoconValueOpt)
  }

  def selectDynamic(name: String) = apply(name)

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
    s"${path} => ${as[JsVal].prettyJson}"

}


