package playground

import a8.shared.ConfigMojo

object ConfigMojoDemo extends App {

  val cmd = ConfigMojo().apply("glen.database")

  cmd.toString

  println(ConfigMojo().apply("glen.database"))
  println(ConfigMojo().apply("glen")("database"))

}
