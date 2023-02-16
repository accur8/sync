package playground

import a8.shared.json.JsonCodec
import a8.shared.json.JsonReader.JsonWarningLogLevel
import a8.shared.{CascadingHocon, CompanionGen, ConfigMojo, HoconOps}
import com.typesafe.config.Config
import playground.MxHoconDemo.MxBigFoo

import scala.reflect.ClassTag
import com.typesafe.config.{ ConfigObject, ConfigValue }
import a8.shared.SharedImports.canEqual.given

object HoconDemo extends App {

  implicit val ll: JsonWarningLogLevel = JsonWarningLogLevel.Debug

  object BigFoo extends MxBigFoo
  @CompanionGen
  case class BigFoo(a: Int, b: Option[String])

  import HoconOps._

  def demo[A : JsonCodec : ClassTag](hoconStr: String): Unit = {
    val hoconConfig = parseHocon(hoconStr)
//    val hoconConfig = ConfigMojo().hoconValue
    val descr = hoconConfig.origin().description()
    println("descr = " + descr)
    val a = hoconConfig.read[A]
    println(a)
  }

  val empty = CascadingHocon.emptyHocon
  val root: ConfigObject = empty.root()
  val boo: ConfigValue = empty.root().get("boo")
  val atKey: ConfigValue = root.get("boo")

//  val value = atKey.root().unwrapped()
//  value.toString

  println(atKey)
//  val valueAtKey = atKey.root().unwrapped()
//  valueAtKey.toString
  println(empty == parseHocon(""))

//  demo[BigFoo]("""{a: 123}""")

  demo[Option[BigFoo]]("")

}
