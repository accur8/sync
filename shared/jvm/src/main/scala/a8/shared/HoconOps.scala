package a8.shared


import java.nio.file.Path

import scala.reflect.ClassTag
import com.typesafe.config._

import scala.language.implicitConversions
import SharedImports._
import a8.shared.json.JsonCodec
import a8.shared.json.ast._


object HoconOps extends HoconOps

trait HoconOps {



  def parseHocon(hocon: String, syntax: ConfigSyntax = ConfigSyntax.CONF): Config = {

    val parseOptions =
      ConfigParseOptions.
        defaults.
        setAllowMissing(false).
        setSyntax(syntax)

    ConfigFactory.parseString(hocon, parseOptions)

  }

  object impl {

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

  implicit def implicitConfigOps(config: Config) = new ConfigOps(config)
  class ConfigOps(private val config: Config) {

    def read[A : JsonCodec : ClassTag]: A = {
      val jsv = impl.toJsVal(config.root)
      jsv.unsafeAs[A]
    }

    def readPath[A : JsonCodec : ClassTag](path: String): A = {
      val jsv = impl.toJsVal(config.getValue(path))
      jsv.unsafeAs[A]
    }



  }

  implicit def implicitConfigValueOps(configValue: ConfigValue) = new ConfigValueOps(configValue)
  class ConfigValueOps(private val configValue: ConfigValue) {

    def asJsValue = impl.toJsVal(configValue)

    def read[A : JsonCodec : ClassTag]: A =
      asJsValue.unsafeAs[A]

    def readPath[A : JsonCodec : ClassTag](path: String): A = {
      configValue match {
        case co: ConfigObject =>
          co.toConfig.readPath[A](path)
      }
    }

  }


}
