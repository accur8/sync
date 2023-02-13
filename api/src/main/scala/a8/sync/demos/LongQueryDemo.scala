package a8.sync.demos

import a8.shared.jdbcf.{Conn, SqlString}
import a8.sync.DataSet
import a8.sync.Imports.{sharedImportsIntOps => _, _}
import a8.sync.impl.queryService
import a8.shared.jdbcf.Conn
import com.ibm.as400.access.AS400JDBCDriver

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import a8.shared.jdbcf.SqlString._
import zio._
import zio.stream.ZSink

object LongQueryDemo extends ZIOAppDefault {

  lazy val connR: Resource[Conn] =
    Conn.fromNewConnection(
      "jdbc:postgresql://localhost/glen".toUri, // connect URL (driver-specific)
      "glen", // user
      "", // password
    )


  def valuesQuery(count: Int): SqlString = {
    val sql = q"select clock_timestamp(),  pg_sleep(1) FROM (VALUES ${(1 to count).map(v => q"${v}").mkSqlString(q"(",q"),(",q")")} ) as aa"
    println(s"value query is -- \n${sql}")
    sql
  }

  val program: Task[List[LocalDateTime]] =
    ZIO.scoped {
      connR
        .use { conn =>
          conn
            .streamingQuery[LocalDateTime](valuesQuery(55))
            .run
            .tap { row =>
              ZIO.succeed(println(s"row ${row}"))
            }
            .run(ZSink.collectAll)
            .map(_.toList)
        }
    }


  //  override def run: IO[Unit] = {
//    for {
//      _ <- program
//    } yield ()
//  }


  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    for {
      fiber <- program.fork
      _ <-
        (
          ZIO.sleep(10.seconds)
            *> ZIO.succeed(println("cancelling"))
            *> fiber.interrupt
            *> ZIO.sleep(10.seconds)
        )
    } yield ()
  }


}
