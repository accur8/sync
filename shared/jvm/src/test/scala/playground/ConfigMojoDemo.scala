package playground

import a8.shared.ConfigMojo

@scala.annotation.nowarn("cat=deprecation")
object ConfigMojoDemo extends App {

  val cmd: ConfigMojo = ConfigMojo.root.apply("glen.database")

//  cmd.toString

  println(ConfigMojo.root.apply("glen.database"))
  println(ConfigMojo.root.apply("glen")("database"))

}
