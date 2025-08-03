package examples

import a8.shared.SharedImports._
import a8.shared.jdbcf._
import a8.sync._
import scala.concurrent.duration._

/**
 * Basic example of synchronizing data between two databases
 */
object BasicDatabaseSync extends App {

  // Configure source database
  val sourceConfig = DatabaseConfig(
    driver = "org.postgresql.Driver",
    url = "jdbc:postgresql://localhost:5432/source_db",
    user = "source_user",
    password = "source_pass"
  )

  // Configure target database  
  val targetConfig = DatabaseConfig(
    driver = "com.mysql.cj.jdbc.Driver",
    url = "jdbc:mysql://localhost:3306/target_db",
    user = "target_user", 
    password = "target_pass"
  )

  // Create connection factories
  val sourceConn = ConnFactory.fromConfig(sourceConfig)
  val targetConn = ConnFactory.fromConfig(targetConfig)

  // Define a simple data model
  case class Customer(
    id: Long,
    name: String,
    email: String,
    createdAt: java.time.LocalDateTime
  )

  // Synchronize customers table
  val syncJob = RowSync[Customer](
    sourceConn = sourceConn,
    targetConn = targetConn,
    sourceQuery = sql"SELECT id, name, email, created_at FROM customers WHERE updated_at > current_date - interval '7 days'",
    targetTable = "customers",
    primaryKey = "id",
    updateColumns = Seq("name", "email") // Only update these columns
  )

  // Execute the sync
  try {
    val result = syncJob.sync()
    println(s"Sync completed successfully!")
    println(s"  Records inserted: ${result.inserted}")
    println(s"  Records updated: ${result.updated}")
    println(s"  Records deleted: ${result.deleted}")
    println(s"  Total processed: ${result.totalProcessed}")
  } catch {
    case e: Exception =>
      println(s"Sync failed: ${e.getMessage}")
      e.printStackTrace()
  }
}