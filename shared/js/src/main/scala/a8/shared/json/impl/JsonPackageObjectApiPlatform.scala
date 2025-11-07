package a8.shared.json.impl

import a8.shared.FileSystem
import a8.shared.json.JsonCodec
import a8.shared.json.JsonReader.JsonReaderOptions

trait JsonPackageObjectApiPlatform { self: JsonPackageObjectApi =>

  def fromHoconFile[A: JsonCodec](file: FileSystem.File)(using JsonReaderOptions): A =
    throw new UnsupportedOperationException("fromHoconFile is not supported on JS platform")

}
