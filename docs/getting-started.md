---
layout: page
title: Getting Started
---

# Getting Started with Accur8 Sync

This guide will help you get up and running with Accur8 Sync quickly.

## Prerequisites

- Scala @SCALA_VERSION@ or compatible version
- SBT 1.x
- JDK 11 or higher
- A supported database (PostgreSQL, MySQL, DB2, or HSQLDB)

## Installation

Add the following dependencies to your `build.sbt`:

```scala mdoc:silent
libraryDependencies ++= Seq(
  "io.accur8" %% "a8-sync-shared" % "@VERSION@",
  "io.accur8" %% "a8-sync-api" % "@VERSION@",
  "io.accur8" %% "a8-logging" % "@VERSION@",
  "io.accur8" %% "a8-logging-logback" % "@VERSION@" // Optional: for Logback support
)
```

## Basic Database Connection

Here's how to create a basic database connection:

```scala mdoc
import a8.shared.SharedImports._
import a8.shared.jdbcf._

// Using configuration
val config = DatabaseConfig(
  driver = "org.postgresql.Driver",
  url = "jdbc:postgresql://localhost:5432/testdb",
  user = "testuser",
  password = "testpass",
  poolConfig = PoolConfig(
    minSize = 5,
    maxSize = 20
  )
)

val connFactory = ConnFactory.fromConfig(config)
```

## Your First Query

Execute a simple query:

```scala mdoc:compile-only
import a8.shared.jdbcf.SqlQuery._

case class User(id: Long, name: String, email: String)

val users = connFactory.use { conn =>
  sql"""
    SELECT id, name, email 
    FROM users 
    WHERE active = true
  """.fetchRows[User](conn)
}

// Process results
users.foreach { user =>
  println(s"User: ${user.name} (${user.email})")
}
```

## Data Synchronization

Synchronize data between two databases:

```scala mdoc:compile-only
import a8.sync._

// Configure source and target
val sourceConn = ConnFactory.fromConfig(sourceConfig)
val targetConn = ConnFactory.fromConfig(targetConfig)

// Set up synchronization
val sync = RowSync[User](
  sourceConn = sourceConn,
  targetConn = targetConn,
  sourceQuery = sql"SELECT * FROM users WHERE updated > current_date - 1",
  targetTable = "users",
  primaryKey = "id"
)

// Execute sync
val result = sync.sync()
println(s"Synced ${result.totalProcessed} records")
```

## JSON Handling

Work with JSON data:

```scala mdoc
import a8.shared.json._
import a8.shared.json.ast._

// Define a model with automatic codec
case class Product(id: Long, name: String, price: BigDecimal)
implicit val productCodec: JsonCodec[Product] = JsonCodec.caseCodec[Product]

// Serialize to JSON
val product = Product(1, "Widget", 19.99)
val json = product.toJson
println(json.prettyString)

// Parse from JSON
val jsonStr = """{"id": 2, "name": "Gadget", "price": 29.99}"""
val parsed = Json.parse(jsonStr).unsafeAs[Product]
```

## Error Handling

Proper error handling patterns:

```scala mdoc:compile-only
import scala.util.{Try, Success, Failure}

val result = Try {
  connFactory.use { conn =>
    // Your database operations
    sql"SELECT count(*) FROM users".fetchOne[Long](conn)
  }
}

result match {
  case Success(count) => 
    println(s"User count: $count")
  case Failure(e) =>
    println(s"Database error: ${e.getMessage}")
    // Handle error appropriately
}
```

## Configuration with Typesafe Config

Load configuration from `application.conf`:

```scala mdoc:compile-only
import com.typesafe.config.ConfigFactory

val config = ConfigFactory.load()
val dbConfig = DatabaseConfig.fromConfig(
  config.getConfig("myapp.database")
)
```

Example `application.conf`:

```hocon
myapp {
  database {
    driver = "org.postgresql.Driver"
    url = "jdbc:postgresql://localhost:5432/myapp"
    url = ${?DATABASE_URL}  # Override with environment variable
    user = ${DB_USER}
    password = ${DB_PASSWORD}
    
    pool {
      minSize = 5
      maxSize = 20
      connectionTimeout = 30s
    }
  }
}
```

## Next Steps

- Review [Database Configuration](database-configuration.md) for database-specific setup
- Explore [Code Examples](examples.md) for common patterns
- Check the [API Documentation](../api/index.html) for detailed reference
- Learn about [Logging Best Practices](logging-guide.md) for production use