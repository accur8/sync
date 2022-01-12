package a8.sync.auditlog

import a8.sync.auditlog.AuditLog.Version
import a8.shared.jdbcf.TableName
import cats.effect.{IO, IOApp}

import java.io.File

object CacheDemo extends IOApp.Simple {


  val cache = Data(new File("./cache"))

  val tableName = "usergroupplay"

  override def run: IO[Unit] = {
    val initial = cache.readTableSyncData(TableName(tableName))
    for {
      write <- cache.writeTableSyncData(TableName(tableName), Version(3))
      postWrite = cache.readTableSyncData(TableName(tableName))
      write2 <- cache.writeTableSyncData(TableName(tableName), Version(5))
      postWrite2 = cache.readTableSyncData(TableName(tableName))
    } yield {
      println("start")
      println(s"cacheDir=${cache.dataDir.getCanonicalPath}")
      println(s"initial=$initial")
      println(s"postWrite=$postWrite")
      println(s"postWrite2=$postWrite2")
      println("end")
    }
  }
}
