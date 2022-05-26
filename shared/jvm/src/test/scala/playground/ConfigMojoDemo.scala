package playground

import a8.shared.ConfigMojo

object ConfigMojoDemo extends App {

  val cmd = ConfigMojo.root("glen.database")

  cmd.toString

  println(ConfigMojo.root("glen.database"))
  println(ConfigMojo.root("glen")("database"))

}
