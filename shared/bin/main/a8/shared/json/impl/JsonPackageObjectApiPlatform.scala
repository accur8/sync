package a8.shared.json.impl

import a8.shared.{FileSystem, HoconOps}
import a8.shared.json.{JsonCodec, JsonReader}
import a8.shared.json.JsonReader.JsonReaderOptions
import com.typesafe.config.ConfigFactory

trait JsonPackageObjectApiPlatform { self: JsonPackageObjectApi =>

  def fromHoconFile[A: JsonCodec](file: FileSystem.File)(using JsonReaderOptions): A = {
    val config = ConfigFactory.parseFile(file.asNioPath.toFile)
    val configValue = config.root()
    HoconOps.impl.internalRead[A](configValue)
  }

}
