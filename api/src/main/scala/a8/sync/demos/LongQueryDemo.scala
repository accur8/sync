package a8.sync.demos

import a8.shared.jdbcf.{Conn, SqlString}
import a8.sync.DataSet
import a8.sync.Imports._
import a8.sync.impl.queryService
import a8.shared.jdbcf.Conn
import cats.effect.{IO, IOApp}
import com.ibm.as400.access.AS400JDBCDriver

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import a8.shared.jdbcf.SqlString._

object LongQueryDemo extends IOApp.Simple {

  lazy val connR =
    Conn.fromNewConnection[IO](
      "jdbc:postgresql://localhost/glen".toUri, // connect URL (driver-specific)
      "glen", // user
      "", // password
    )


  def valuesQuery(count: Int): SqlString = {
    val sql = q"select clock_timestamp(),  pg_sleep(1) FROM (VALUES ${(1 to count).map(v => q"${v}").mkSqlString(q"(",q"),(",q")")} ) as aa"
    println(s"value query is -- \n${sql}")
    sql
  }

  val program =
    connR
      .use { conn =>
        conn
          .streamingQuery[LocalDateTime](valuesQuery(55))
          .run
          .evalMap { row =>
            IO.println(s"row ${row}")
          }
          .compile
          .toList
      }


//  override def run: IO[Unit] = {
//    for {
//      _ <- program
//    } yield ()
//  }

  override def run: IO[Unit] = {
    for {
      fiber <- program.start
      _ <-
        (
          IO.sleep(FiniteDuration(10, TimeUnit.SECONDS))
            *> IO.println("cancelling")
            *> fiber.cancel
            *> IO.sleep(FiniteDuration(10, TimeUnit.SECONDS))
        )
    } yield ()
  }


}
