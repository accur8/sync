package a8.sync.auditlog

import a8.sync.auditlog.AuditLog.Version
import cats.effect.IO
import a8.sync.FileUtil._
import a8.shared.jdbcf.TableName

import java.io.{BufferedWriter, File, FileWriter}


case class Data(
  dataDir: File
) {

  def dataFile(tableName: TableName): File =
    new File(s"${dataDir.getCanonicalPath}/${tableName.value.toString}")

  def readTableSyncData(tableName: TableName): Version =
      try{
        Version(
          readFile(
            dataFile(tableName)
          ).toLong
        )
      }catch {
        case e: Exception =>
          println(s"unable to read data file ${tableName}: ${e.getMessage}. Starting at version 1")
          Version(1)
      }

  def writeTableSyncData(tableName: TableName, version: Version): IO[Unit] =
    IO{
      val file = dataFile(tableName)
      if ( !file.exists() ) {
        val r = file.createNewFile()
        r
      }
      val writer: BufferedWriter = new BufferedWriter(new FileWriter(file.getCanonicalPath, false))
      writer.write(version.value.toString)
      writer.close()
    }

}
