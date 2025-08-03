package examples

import a8.shared.SharedImports._
import a8.shared.jdbcf._
import a8.shared.jdbcf.SqlQuery._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Example of processing large datasets using streaming to avoid memory issues
 */
object StreamingLargeDataset extends App {

  val dbConfig = DatabaseConfig(
    driver = "org.postgresql.Driver",
    url = "jdbc:postgresql://localhost:5432/analytics",
    user = "analytics_user",
    password = "analytics_pass",
    poolConfig = PoolConfig(
      minSize = 2,
      maxSize = 10,
      connectionTimeout = 30.seconds
    )
  )

  val connFactory = ConnFactory.fromConfig(dbConfig)

  case class Transaction(
    id: Long,
    accountId: Long,
    amount: BigDecimal,
    transactionDate: java.time.LocalDate,
    description: String
  )

  // Process large transaction dataset
  connFactory.use { conn =>
    val query = sql"""
      SELECT id, account_id, amount, transaction_date, description
      FROM transactions
      WHERE transaction_date >= '2024-01-01'
      ORDER BY transaction_date
    """

    var totalAmount = BigDecimal(0)
    var recordCount = 0
    val batchSize = 1000

    println("Starting to process transactions...")

    // Stream rows instead of loading all into memory
    query.streamRows[Transaction](conn) { stream =>
      stream
        .grouped(batchSize)
        .zipWithIndex
        .foreach { case (batch, batchIndex) =>
          // Process each batch
          val batchTotal = batch.map(_.amount).sum
          totalAmount += batchTotal
          recordCount += batch.size

          println(s"Processed batch ${batchIndex + 1}: ${batch.size} records, batch total: $$${batchTotal}")

          // Example: Write batch to another system
          writeBatchToWarehouse(batch)
          
          // Optional: Add delay to avoid overwhelming downstream systems
          Thread.sleep(100)
        }
    }

    println(s"\nProcessing complete!")
    println(s"Total records processed: $recordCount")
    println(s"Total transaction amount: $$${totalAmount}")
  }

  def writeBatchToWarehouse(transactions: Seq[Transaction]): Unit = {
    // Simulate writing to data warehouse
    // In real implementation, this would write to another database or file system
    println(s"  Writing ${transactions.size} records to warehouse...")
  }
}