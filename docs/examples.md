---
layout: page
title: Code Examples
---

# Code Examples

This page provides practical examples of common Accur8 Sync use cases.

## Database Operations

### Basic Query Execution

```scala mdoc:compile-only
import a8.shared.SharedImports._
import a8.shared.jdbcf._
import a8.shared.jdbcf.SqlQuery._

val connFactory = ConnFactory.fromConfig(dbConfig)

// Simple query
val userCount = connFactory.use { conn =>
  sql"SELECT COUNT(*) FROM users".fetchOne[Long](conn)
}

// Query with parameters
val activeUsers = connFactory.use { conn =>
  val status = "active"
  val minAge = 18
  
  sql"""
    SELECT id, name, email, age
    FROM users
    WHERE status = $status
      AND age >= $minAge
    ORDER BY name
  """.fetchRows[(Long, String, String, Int)](conn)
}
```

### Batch Operations

```scala mdoc:compile-only
case class User(id: Long, name: String, email: String)

val users = List(
  User(1, "Alice", "alice@example.com"),
  User(2, "Bob", "bob@example.com"),
  User(3, "Charlie", "charlie@example.com")
)

connFactory.useTransaction { conn =>
  // Batch insert
  val insertSql = sql"""
    INSERT INTO users (id, name, email)
    VALUES (?, ?, ?)
  """
  
  conn.batchUpdate(insertSql, users) { (stmt, user) =>
    stmt.setLong(1, user.id)
    stmt.setString(2, user.name)
    stmt.setString(3, user.email)
  }
  
  // Batch update
  val updateSql = sql"""
    UPDATE users 
    SET last_seen = CURRENT_TIMESTAMP
    WHERE id = ?
  """
  
  conn.batchUpdate(updateSql, users.map(_.id)) { (stmt, id) =>
    stmt.setLong(1, id)
  }
}
```

## Data Synchronization

### Simple Table Sync

```scala mdoc:compile-only
import a8.sync._

// Sync entire table
val tableSync = TableSync(
  sourceConn = sourceConnFactory,
  targetConn = targetConnFactory,
  tableName = "products",
  primaryKey = "product_id"
)

val result = tableSync.sync()
println(s"Synced ${result.totalProcessed} products")
```

### Conditional Sync with Transformation

```scala mdoc:compile-only
import a8.sync._
import java.time.LocalDateTime

case class Order(
  orderId: Long,
  customerId: Long,
  amount: BigDecimal,
  status: String,
  createdAt: LocalDateTime
)

val orderSync = RowSync[Order](
  sourceConn = sourceConnFactory,
  targetConn = targetConnFactory,
  sourceQuery = sql"""
    SELECT order_id, customer_id, amount, status, created_at
    FROM orders
    WHERE created_at >= CURRENT_DATE - INTERVAL '7 days'
      AND status IN ('completed', 'shipped')
  """,
  targetTable = "order_history",
  primaryKey = "order_id",
  transform = { order =>
    // Transform data before inserting
    order.copy(
      amount = order.amount * 1.1, // Add 10% markup
      status = if (order.status == "completed") "archived" else order.status
    )
  }
)

orderSync.syncWithRetry(maxRetries = 3)
```

## Streaming Large Datasets

### Memory-Efficient Processing

```scala mdoc:compile-only
import a8.shared.jdbcf.SqlQuery._

connFactory.use { conn =>
  val query = sql"""
    SELECT transaction_id, account_id, amount, transaction_date
    FROM transactions
    WHERE transaction_date >= '2024-01-01'
  """
  
  case class Transaction(id: Long, accountId: Long, amount: BigDecimal, date: LocalDate)
  
  // Process in batches to avoid memory issues
  query.streamRows[Transaction](conn) { stream =>
    stream
      .grouped(1000) // Process 1000 records at a time
      .foreach { batch =>
        processBatch(batch)
        
        // Optional: Report progress
        println(s"Processed ${batch.size} transactions")
      }
  }
}

def processBatch(transactions: Seq[Transaction]): Unit = {
  // Calculate aggregates
  val totalByAccount = transactions.groupBy(_.accountId).map {
    case (accountId, txns) => (accountId, txns.map(_.amount).sum)
  }
  
  // Write to target system
  targetConnFactory.use { conn =>
    totalByAccount.foreach { case (accountId, total) =>
      sql"""
        INSERT INTO account_summaries (account_id, total_amount, summary_date)
        VALUES ($accountId, $total, CURRENT_DATE)
        ON CONFLICT (account_id, summary_date) 
        DO UPDATE SET total_amount = EXCLUDED.total_amount
      """.executeUpdate(conn)
    }
  }
}
```

## JSON API Integration

### External API Client

```scala mdoc:compile-only
import a8.shared.json._
import a8.sync.qubes.QubesApiClient
import scala.concurrent.ExecutionContext.Implicits.global

// Define your data models
case class Customer(
  id: String,
  name: String,
  email: String,
  tier: String
)

case class ApiResponse[T](
  success: Boolean,
  data: Option[T],
  error: Option[String]
)

// Automatic codec generation
implicit val customerCodec: JsonCodec[Customer] = JsonCodec.caseCodec[Customer]
implicit def apiResponseCodec[T: JsonCodec]: JsonCodec[ApiResponse[T]] = 
  JsonCodec.caseCodec[ApiResponse[T]]

// Create API client
val apiClient = QubesApiClient(
  baseUrl = "https://api.example.com",
  authToken = "your-api-token"
)

// Fetch data from API
val customersFuture = apiClient.get[ApiResponse[List[Customer]]]("/v1/customers")

customersFuture.map {
  case ApiResponse(true, Some(customers), _) =>
    // Sync to database
    syncCustomersToDb(customers)
  case ApiResponse(false, _, Some(error)) =>
    logger.error(s"API error: $error")
  case _ =>
    logger.error("Unexpected API response")
}

def syncCustomersToDb(customers: List[Customer]): Unit = {
  connFactory.useTransaction { conn =>
    customers.foreach { customer =>
      sql"""
        INSERT INTO customers (id, name, email, tier)
        VALUES (${customer.id}, ${customer.name}, ${customer.email}, ${customer.tier})
        ON CONFLICT (id) DO UPDATE SET
          name = EXCLUDED.name,
          email = EXCLUDED.email,
          tier = EXCLUDED.tier
      """.executeUpdate(conn)
    }
  }
}
```

## Data Validation

### Custom Validation Rules

```scala mdoc:compile-only
import a8.sync._

case class Product(
  sku: String,
  name: String,
  price: BigDecimal,
  category: String
)

val validator = DataValidator[Product](
  rules = List(
    // SKU format validation
    ValidationRule.regex("sku", "^[A-Z]{3}-\\d{4}$", "SKU must be format XXX-9999"),
    
    // Name length validation
    ValidationRule.maxLength("name", 100),
    ValidationRule.notEmpty("name"),
    
    // Price validation
    ValidationRule.custom("price", (p: BigDecimal) => p > 0, "Price must be positive"),
    ValidationRule.custom("price", (p: BigDecimal) => p < 10000, "Price too high"),
    
    // Category validation
    ValidationRule.oneOf("category", Set("electronics", "clothing", "food", "other"))
  )
)

// Validate data before sync
val products = loadProductsFromSource()
val validationResults = products.map(validator.validate)

val (valid, invalid) = validationResults.partition(_.isValid)

println(s"Valid products: ${valid.size}")
println(s"Invalid products: ${invalid.size}")

// Only sync valid products
valid.foreach { result =>
  result.value.foreach(syncProduct)
}

// Log validation errors
invalid.foreach { result =>
  result.errors.foreach { error =>
    logger.warn(s"Validation error: ${error.field} - ${error.message}")
  }
}
```

## Error Handling

### Comprehensive Error Management

```scala mdoc:compile-only
import scala.util.{Try, Success, Failure}
import java.sql.SQLException

class ResilientSync extends Logging {
  
  def syncWithRecovery(): Unit = {
    val result = Try {
      performSync()
    }.recover {
      case e: SQLException if e.getSQLState == "08001" =>
        logger.warn("Database connection failed, retrying with backup server")
        performSyncWithBackup()
      case e: SQLException =>
        logger.error(s"SQL error: ${e.getMessage}", e)
        SyncResult.failed(e.getMessage)
    }
    
    result match {
      case Success(syncResult) =>
        logger.info(s"Sync completed: $syncResult")
        notifySuccess(syncResult)
      case Failure(e) =>
        logger.error("Sync failed after all retries", e)
        notifyFailure(e)
    }
  }
  
  def performSync(): SyncResult = {
    // Main sync logic
    ???
  }
  
  def performSyncWithBackup(): SyncResult = {
    // Backup sync logic
    ???
  }
  
  def notifySuccess(result: SyncResult): Unit = {
    // Send success notification
  }
  
  def notifyFailure(error: Throwable): Unit = {
    // Send failure alert
  }
}
```

## Testing

### Unit Testing with HSQLDB

```scala mdoc:compile-only
import org.scalatest.funsuite.AnyFunSuite
import a8.shared.jdbcf.hsqldb.HsqldbTestDb

class DataSyncTest extends AnyFunSuite {
  
  test("sync should insert new records") {
    HsqldbTestDb.withTestDb { testDb =>
      // Setup test schema
      testDb.executeSql("""
        CREATE TABLE users (
          id BIGINT PRIMARY KEY,
          name VARCHAR(100),
          email VARCHAR(100)
        )
      """)
      
      // Insert test data
      testDb.executeSql("""
        INSERT INTO users (id, name, email) VALUES
        (1, 'Alice', 'alice@test.com'),
        (2, 'Bob', 'bob@test.com')
      """)
      
      // Run sync
      val sync = UserSync(testDb.connFactory)
      val result = sync.sync()
      
      // Verify results
      assert(result.inserted == 2)
      assert(result.errors.isEmpty)
      
      // Verify data
      val users = testDb.query("SELECT * FROM users ORDER BY id")
      assert(users.size == 2)
    }
  }
}
```

## Performance Optimization

### Parallel Processing

```scala mdoc:compile-only
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._
import java.util.concurrent.Executors

// Create a dedicated thread pool for database operations
implicit val dbExecutionContext: ExecutionContext = 
  ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

def parallelSync(tables: List[String]): Future[List[SyncResult]] = {
  // Process multiple tables in parallel
  val syncFutures = tables.map { table =>
    Future {
      val sync = TableSync(
        sourceConn = sourceConnFactory,
        targetConn = targetConnFactory,
        tableName = table
      )
      sync.sync()
    }
  }
  
  Future.sequence(syncFutures)
}

// Execute parallel sync
val tables = List("customers", "orders", "products", "inventory")
val resultsFuture = parallelSync(tables)

resultsFuture.foreach { results =>
  results.zip(tables).foreach { case (result, table) =>
    println(s"$table: ${result.totalProcessed} records synced")
  }
}
```

These examples demonstrate the key features and patterns of Accur8 Sync. For more detailed information, refer to the [API Documentation](../api/index.html).