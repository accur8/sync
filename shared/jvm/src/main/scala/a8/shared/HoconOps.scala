package a8.shared


import java.nio.file.Path
import scala.reflect.ClassTag
import com.typesafe.config.*

import scala.language.implicitConversions
import SharedImports.*
import a8.shared.json.{JsonCodec, JsonReader}
import a8.shared.json.JsonReader.{JsonReaderOptions, JsonSource, ReadResult}
import a8.shared.json.ast.*
import SharedImports.canEqual.given
import a8.shared.json.ast.JsDoc.JsDocRoot

object HoconOps extends HoconOps

trait HoconOps {

  def parseHocon(hocon: String, syntax: ConfigSyntax = ConfigSyntax.CONF, origin: Option[String] = None): Config = {

    def baseParseOptions =
      ConfigParseOptions.
        defaults.
        setAllowMissing(false).
        setSyntax(syntax)

    val parseOptions =
      origin match {
        case None =>
          baseParseOptions
        case Some(d) =>
          baseParseOptions.setOriginDescription(d)
      }

    ConfigFactory.parseString(hocon, parseOptions)

  }

  object impl {

    def internalRead[A: JsonCodec](configValue: ConfigValue, prefixPath: Iterable[String] = Iterable.empty)(implicit jsonReaderOptions: JsonReaderOptions): A = {
      internalReadResult[A](configValue, prefixPath) match {
        case ReadResult.Success(a, _, _, _) =>
          a
        case error: ReadResult.Error[A] =>
          throw error.readError.asException
      }
    }

    def internalReadResult[A: JsonCodec](configValue: ConfigValue, prefixPath: Iterable[String] = Iterable.empty)(implicit jsonReaderOptions: JsonReaderOptions): ReadResult[A] = {
      val jsv = HoconOps.impl.toJsVal(configValue)
      val rootJsv: JsVal =
        prefixPath.foldRight(jsv) { (n,v) =>
          JsObj(Map(n -> v))
        }
      val rootDoc = JsDocRoot(rootJsv)
      JsonReader[A].readResult(rootDoc) match {
        case s: ReadResult.Success[A] =>
          s
        case error: ReadResult.Error[A] =>
          if ( configValue == CascadingHocon.emptyConfigObject ) {
            JsonReader[A].readResult(JsNothing) match {
              case s: ReadResult.Success[A] =>
                s
              case _ =>
                error
            }
          } else {
            error
          }
      }
    }

    def loadConfig(file: Path): Config = {
      val config = ConfigFactory.parseFile(file.toFile)
      config
    }

    def toJsVal(conf: Config): JsVal =
      toJsObj(conf.root())

    def toJsObj(co: ConfigObject): JsObj = {
      JsObj(
        co.unwrapped.keySet.asScala
          .map { fieldName =>
            val jsv = impl.toJsVal(co.get(fieldName))
            fieldName -> jsv
          }
          .toMap
      )
    }

    def toJsVal(cv: ConfigValue): JsVal = {
      (cv, cv.valueType()) match {
        case (cl: ConfigList, _) =>
          JsArr(
            cl.iterator().asScala.map(toJsVal).toList
          )
        case (co: ConfigObject, _) =>
          impl.toJsObj(co)
        case (_, ConfigValueType.BOOLEAN) =>
          JsBool(
            cv.unwrapped.asInstanceOf[java.lang.Boolean]
          )
        case (_, ConfigValueType.NUMBER) =>
          JsNum(
            BigDecimal(cv.unwrapped.asInstanceOf[java.lang.Number].toString)
          )
        case (_, ConfigValueType.NULL) =>
          JsNull
        case (_, ConfigValueType.STRING) =>
          JsStr(
            cv.unwrapped.asInstanceOf[java.lang.String]
          )
        case (x, _) =>
          sys.error("should never have gotten here " + x)
      }
    }

  }

  implicit def implicitConfigOps(config: Config): ConfigOps = new ConfigOps(config)
  class ConfigOps(private val config: Config) {

    def read[A : JsonCodec](implicit jsonReaderOptions: JsonReaderOptions): A = {
      HoconOps.impl.internalRead[A](config.root)
    }

    def readPath[A : JsonCodec](path: String)(implicit jsonReaderOptions: JsonReaderOptions): A = {
      HoconOps.impl.internalRead[A](config.getValue(path))
    }

  }

  implicit def implicitConfigValueOps(configValue: ConfigValue): ConfigValueOps = new ConfigValueOps(configValue)
  class ConfigValueOps(private val configValue: ConfigValue) {

    def read[A : JsonCodec : ClassTag](implicit jsonReaderOptions: JsonReaderOptions): A =
      HoconOps.impl.internalRead[A](configValue)

    def readPath[A : JsonCodec : ClassTag](path: String)(implicit jsonReaderOptions: JsonReaderOptions): A = {
      configValue match {
        case co: ConfigObject =>
          HoconOps.impl.internalRead[A](configValue)
      }
    }

  }


}
