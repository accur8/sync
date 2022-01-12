package a8.sync.auditlog

import a8.shared.CompanionGen
import a8.shared.jdbcf.{Conn, Dialect}
import a8.sync.auditlog.AuditLog.{AuditRowApplyResult, Version}
import a8.shared.jdbcf.{ColumnName, TableName}
import cats.effect.{Async, IO, IOApp}
import org.typelevel.ci.CIString
import a8.sync.Imports._
import a8.sync.auditlog.MxAuditLogSync.MxTableSync
import cats.effect.kernel.Resource

import java.io.File
import java.sql.DriverManager
import scala.io.Source
import scala.language.implicitConversions

object AuditLogSync extends IOApp.Simple {

  implicit def toCiString(s: String) = CIString(s)

//  DriverManager.registerDriver(new org.postgresql.Driver)
//  case class VersionCache(tableName: String, version: Long)

  object TableSync extends MxTableSync {
  }
  @CompanionGen
  case class TableSync(
    start: Version,
    sourceTable: TableName,
    targetTable: TableName,
    primaryKey: ColumnName,
  ) {
    def run(
      source: Conn[IO],
      targetConn: Conn[IO]
    )(
      implicit
        dialect: Dialect,
    ): fs2.Stream[IO, (AuditRowApplyResult,Version)] = {
      val sourceStream = AuditLog.readAuditLog(sourceTable, start, source)
      AuditLog.applyAuditLogOrdered(sourceStream, primaryKey, targetTable, targetConn)
    }
  }

  implicit val dialect: Dialect = Dialect.Default

  val config: Config = Config.load(new File("."))

  implicit val data: Data = {
    val f = new File("./data")
    if ( !f.exists() ) f.mkdir()
    Data(f)
  }

  val sourceDatabaseIO: Resource[IO,Conn[IO]] =
    Conn.fromNewConnection[IO](config.sourceDatabase.url, config.sourceDatabase.user, config.sourceDatabase.password)

  val targetDatabaseIO: Resource[IO,Conn[IO]] =
    Conn.fromNewConnection[IO](config.targetDatabase.url, config.targetDatabase.user, config.targetDatabase.password)

  val tableSyncs: Vector[TableSync] =
    config
      .tableSyncs
      .toVector.map { sync =>
        val cachedVersion: Version = data.readTableSyncData(sync.targetTable)
        sync.copy(start = cachedVersion)
      }

  implicit class StreamOps[F[_] : Async,A](source: fs2.Stream[F,A]) {
    def drainAndPrintRates(reportEvery: Int): F[Int] =
      source
        .compile
        .fold(0 -> System.currentTimeMillis) { (t, _) =>
          val i = t._1 + 1
          val j =
            if ( i % reportEvery == 0 ) {
              val now = System.currentTimeMillis()
              val delta = (now - t._2).toDouble
              println(s"$i total processed   ${(1000 * reportEvery / delta).toInt} rows/s for this batch")
              now
            } else {
              t._2
            }
          i -> j
        }
        .map(_._1)
//        .compile
//        .drain

  }

  def writeVersion(table: TableName, version: Version): IO[Unit] =
    data
      .writeTableSyncData(table, version)

  def writeVersionEvery(
    table: TableName,
    every: Int,
    resultsStream: fs2.Stream[IO,(AuditRowApplyResult,Version)]
  ): IO[Int] =
    resultsStream
      .zipWithIndex
      .evalMap { case (t, i) =>
        IO
          .delay {
            if ( i % every == 0 )
              writeVersion(table, t._2)
            else
              IO.unit
          }
          .map(_ => t)
      }
      .onLastF(t => writeVersion(table, t._2))
      .compile
      .fold(0)((acc,v) => acc + 1)

  // TODO jeremy add batching - every 1000 records write table and version to disk
  // TODO jeremy parallelize this so multiple table are updated at the same time
  override def run: IO[Unit] = {
    (sourceDatabaseIO, targetDatabaseIO).tupled.use { case (sourceDatabase, targetDatabase) =>
      val start = System.currentTimeMillis()

      for {
        resultCounts <-
            tableSyncs
              .traverse { tableSync =>
                val resultsStream = tableSync.run(sourceDatabase, targetDatabase)
                writeVersionEvery(tableSync.targetTable, 1000, resultsStream)
              }
      } yield {
        val delta = System.currentTimeMillis() - start
        val count = resultCounts.sum
        println(s"$delta  $count   ${((1000*count.toDouble) / delta).toInt} rows/s" )
        ()
      }

    }
  }
}
