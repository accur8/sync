# Accur8 Sync - How to Use This Library

## Overview

Accur8 Sync is a Scala-based data synchronization framework for enterprise data pipelines. It provides database abstraction, row-level synchronization, and integration capabilities for building robust ETL/ELT processes.

## Quick Start

### Adding Dependencies

Add the following to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "io.accur8" %% "a8-sync-shared" % "latest-version",
  "io.accur8" %% "a8-sync-api" % "latest-version",
  "io.accur8" %% "a8-logging" % "latest-version"
)
```

### Basic Setup

```scala
import a8.shared.SharedImports._
import a8.shared.jdbcf._
import a8.sync._

// Configure database connection
val dbConfig = DatabaseConfig(
  driver = "org.postgresql.Driver",
  url = "jdbc:postgresql://localhost:5432/mydb",
  user = "username",
  password = "password"
)

// Create connection factory
val connFactory = ConnFactory.fromConfig(dbConfig)
```

## Core Features

### 1. Database Abstraction Layer

The library supports multiple database systems with a unified API:

- PostgreSQL
- MySQL
- IBM DB2/AS400
- HSQLDB (for testing)

#### Example: Cross-Database Query

```scala
import a8.shared.jdbcf.SqlQuery._

// Works across all supported databases
val query = sql"""
  SELECT id, name, email 
  FROM users 
  WHERE active = ${true}
"""

val users = connFactory.use { conn =>
  query.fetchRows(conn)
}
```

### 2. Row-Level Synchronization

Synchronize data between systems with automatic change detection:

```scala
import a8.sync.RowSync

// Define your data model
case class User(id: Long, name: String, email: String)

// Configure row sync
val rowSync = RowSync[User](
  sourceConn = sourceConnFactory,
  targetConn = targetConnFactory,
  sourceQuery = sql"SELECT * FROM source_users",
  targetTable = "target_users",
  primaryKey = "id"
)

// Execute synchronization
val syncResult = rowSync.sync()
println(s"Inserted: ${syncResult.inserted}, Updated: ${syncResult.updated}")
```

### 3. Data Validation and Transformation

Built-in validation with configurable truncation policies:

```scala
import a8.sync.DataSet

val dataset = DataSet(
  rows = sourceData,
  validationRules = Seq(
    ValidationRule.maxLength("name", 50),
    ValidationRule.notNull("email"),
    ValidationRule.unique("email")
  ),
  truncationPolicy = TruncationPolicy.Warn // or Error, Truncate
)

val validatedData = dataset.validate()
```

### 4. JSON Processing

Type-safe JSON serialization with custom codecs:

```scala
import a8.shared.json.JsonCodec
import a8.shared.json.ast._

// Automatic codec derivation
case class Product(id: Long, name: String, price: BigDecimal)
implicit val productCodec: JsonCodec[Product] = JsonCodec.caseCodec[Product]

// Serialize/deserialize
val product = Product(1, "Widget", 19.99)
val json = product.toJson
val decoded = json.unsafeAs[Product]
```

### 5. HTTP API Integration

Connect to external systems using the built-in HTTP client:

```scala
import a8.sync.qubes.QubesApiClient

val apiClient = QubesApiClient(
  baseUrl = "https://api.example.com",
  authToken = "your-token"
)

// Make API calls
val response = apiClient.get[List[Product]]("/products")
```

## Common Use Cases

### ETL Pipeline Example

```scala
import a8.sync._
import a8.shared.jdbcf._

object CustomerETL extends App {
  
  // 1. Extract from source
  val sourceData = sourceConn.use { conn =>
    sql"""
      SELECT customer_id, name, email, last_purchase
      FROM customers
      WHERE last_purchase > current_date - interval '30 days'
    """.fetchRows[Customer](conn)
  }
  
  // 2. Transform data
  val transformedData = sourceData.map { customer =>
    customer.copy(
      email = customer.email.toLowerCase,
      segment = calculateSegment(customer.lastPurchase)
    )
  }
  
  // 3. Load to target
  targetConn.use { conn =>
    conn.transaction { implicit tx =>
      transformedData.foreach { customer =>
        sql"""
          INSERT INTO customer_analytics (id, name, email, segment)
          VALUES (${customer.id}, ${customer.name}, ${customer.email}, ${customer.segment})
          ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name,
            email = EXCLUDED.email,
            segment = EXCLUDED.segment
        """.executeUpdate()
      }
    }
  }
}
```

### Multi-Database Sync

```scala
// Sync between different database types
val postgresConn = ConnFactory.postgres(postgresConfig)
val mysqlConn = ConnFactory.mysql(mysqlConfig)

val crossDbSync = RowSync(
  source = postgresConn,
  target = mysqlConn,
  mapping = TableMapping(
    sourceTable = "pg_orders",
    targetTable = "mysql_orders",
    columnMappings = Map(
      "order_id" -> "id",
      "customer_name" -> "customer",
      "order_date" -> "created_at"
    )
  )
)

crossDbSync.syncWithRetry(maxRetries = 3)
```

### Streaming Large Datasets

```scala
// Handle large datasets with streaming
connFactory.use { conn =>
  val query = sql"SELECT * FROM large_table"
  
  query.streamRows[LargeRecord](conn) { stream =>
    stream
      .grouped(1000)  // Process in batches
      .foreach { batch =>
        processBatch(batch)
      }
  }
}
```

## Configuration

### Database Configuration

Create `application.conf`:

```hocon
accur8.sync {
  databases {
    primary {
      driver = "org.postgresql.Driver"
      url = "jdbc:postgresql://localhost:5432/prod"
      user = ${DB_USER}
      password = ${DB_PASSWORD}
      
      # Connection pool settings
      pool {
        minSize = 5
        maxSize = 20
        connectionTimeout = 30s
      }
    }
    
    analytics {
      driver = "com.mysql.cj.jdbc.Driver"
      url = "jdbc:mysql://analytics-db:3306/warehouse"
      user = ${ANALYTICS_USER}
      password = ${ANALYTICS_PASSWORD}
    }
  }
}
```

### Logging Configuration

Configure Logback in `logback.xml`:

```xml
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>
  
  <logger name="a8.sync" level="INFO"/>
  <logger name="a8.shared.jdbcf" level="DEBUG"/>
  
  <root level="INFO">
    <appender-ref ref="STDOUT"/>
  </root>
</configuration>
```

## Testing

### Using HSQLDB for Tests

```scala
import a8.shared.jdbcf.hsqldb.HsqldbTestDb

class MyDataTest extends FunSuite {
  
  test("data synchronization") {
    HsqldbTestDb.withTestDb { testDb =>
      // Setup test data
      testDb.executeSql("""
        CREATE TABLE users (
          id INT PRIMARY KEY,
          name VARCHAR(50)
        )
      """)
      
      // Run your sync logic
      val sync = RowSync(testDb.connFactory, ...)
      val result = sync.sync()
      
      // Assert results
      assert(result.inserted == 5)
    }
  }
}
```

## Performance Tips

1. **Use Connection Pooling**: Always configure HikariCP pool settings
2. **Batch Operations**: Process data in chunks for better performance
3. **Streaming**: Use `streamRows` for large datasets to avoid memory issues
4. **Parallel Processing**: Leverage Scala's parallel collections when appropriate
5. **Index Optimization**: Ensure proper database indexes for sync queries

## Error Handling

```scala
import a8.shared.CompanionGen
import scala.util.{Try, Success, Failure}

// Comprehensive error handling
val result = Try {
  connFactory.use { conn =>
    // Your database operations
  }
} match {
  case Success(data) => 
    logger.info(s"Successfully processed ${data.size} records")
    data
  case Failure(e: SQLException) =>
    logger.error(s"Database error: ${e.getMessage}", e)
    // Handle or rethrow
  case Failure(e) =>
    logger.error(s"Unexpected error: ${e.getMessage}", e)
    throw e
}
```

## Advanced Features

### Custom Database Dialects

```scala
import a8.shared.jdbcf.dialect._

object MyCustomDialect extends DatabaseDialect {
  override def quoteIdentifier(name: String): String = s"`$name`"
  override def limitClause(limit: Int): String = s"FETCH FIRST $limit ROWS ONLY"
  // ... implement other dialect-specific SQL
}

// Register dialect
DatabaseDialect.register("mydb", MyCustomDialect)
```

### Custom JSON Codecs

```scala
import a8.shared.json._

// Custom codec for special types
implicit val dateCodec: JsonCodec[LocalDate] = JsonCodec.string.dimap(
  LocalDate.parse,
  _.toString
)

// Codec with validation
implicit val emailCodec: JsonCodec[Email] = JsonCodec.string.validate { str =>
  if (str.contains("@")) Right(Email(str))
  else Left("Invalid email format")
}
```

## Troubleshooting

### Common Issues

1. **Connection Pool Exhaustion**
   - Increase pool size in configuration
   - Ensure connections are properly closed (use `connFactory.use`)

2. **Memory Issues with Large Datasets**
   - Use streaming APIs instead of loading all data
   - Increase JVM heap size if necessary

3. **Slow Synchronization**
   - Check database indexes
   - Use batch operations
   - Enable query logging to identify bottlenecks

### Debug Logging

Enable detailed logging:

```scala
// In your code
import a8.shared.jdbcf.LoggingSql._

// This will log all SQL statements
connFactory.withSqlLogging.use { conn =>
  // Your queries will be logged
}
```

## Support and Resources

- **Issue Tracking**: Report bugs and feature requests on GitHub
- **API Documentation**: Generate with `sbt doc`
- **Examples**: See `/examples` directory for complete applications
- **Tests**: Review test cases for usage patterns

## Migration Guide

If migrating from an older version:

1. Update dependencies to latest versions
2. Review breaking changes in CHANGELOG.md
3. Update deprecated API calls
4. Run test suite to verify compatibility

## License

This project is licensed under the terms specified in the LICENSE file.