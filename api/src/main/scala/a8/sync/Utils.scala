package a8.sync


import a8.shared.json.JsonCodec
import a8.shared.json.JsonReader.JsonReaderOptions

import java.nio.file.{Path, Paths}

object Utils {

  object config {

    val defaultFilename = "config.json"
    val defaultLookupDirs: Vector[Path] = Vector(Paths.get("config"), Paths.get(""))

    def load[A : JsonCodec](filename: String = defaultFilename, lookupDirs: Vector[Path] = defaultLookupDirs)(implicit jsonReaderOptions: JsonReaderOptions): A = {
      val files = lookupDirs.map(_.resolve(filename).toFile)
      files.find(_.exists()) match {
        case Some(configFile) =>
          FileUtil.loadJsonFile[A](configFile.getCanonicalFile)
        case None =>
          sys.error("Config file not found. Looked up:\n  " + files.map(_.getCanonicalPath).mkString("\n  "))
      }
    }

    def save[A : JsonCodec](a: A, filename: String = defaultFilename, lookupDirs: Vector[Path] = defaultLookupDirs): Unit = {
      val files = lookupDirs.map(_.resolve(filename).toFile)
      val file = files.find(_.exists()) match {
        case Some(configFile) => configFile
        case None => lookupDirs.head.resolve(filename).toFile
      }
      FileUtil.saveJsonFile(file.getCanonicalFile, a)
    }

  }

}
