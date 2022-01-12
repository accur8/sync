package a8.sync.auditlog

import java.io.File

object ConfigDemo extends App {

  val config = Config.load(new File("."))

  print(config)

}
