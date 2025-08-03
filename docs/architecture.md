---
layout: page
title: Architecture
---

# Accur8 Sync Architecture

## Overview

Accur8 Sync follows a layered architecture designed for flexibility, testability, and performance:

```
┌─────────────────────────────────────────────────┐
│          Application Layer (Your Code)           │
├─────────────────────────────────────────────────┤
│              Sync API Layer                      │
│  (RowSync, DataSet, Validation, QubesClient)    │
├─────────────────────────────────────────────────┤
│            Shared Core Layer                     │
│  (JDBCF, JSON Codecs, Common Utilities)         │
├─────────────────────────────────────────────────┤
│             Database Drivers                     │
│   (PostgreSQL, MySQL, DB2, HSQLDB)              │
└─────────────────────────────────────────────────┘
```

## Core Components

### 1. JDBCF (JDBC Functional)

The foundation of database operations, providing:

- **Type-safe SQL DSL**: Compile-time checked SQL queries
- **Connection management**: Automatic resource cleanup
- **Database abstraction**: Unified API across database systems
- **Streaming support**: Memory-efficient data processing

```scala mdoc:compile-only
import a8.shared.jdbcf._
import a8.shared.jdbcf.SqlQuery._

// Type-safe query construction
val query = sql"""
  SELECT u.id, u.name, o.total
  FROM users u
  JOIN orders o ON u.id = o.user_id
  WHERE o.created_at > ${startDate}
    AND o.status = ${status}
"""

// Automatic resource management
connFactory.use { conn =>
  query.fetchRows[(Long, String, BigDecimal)](conn)
}
```

### 2. JSON Processing

Built on a functional JSON AST with automatic codec derivation:

```scala mdoc:compile-only
import a8.shared.json._

// AST representation
sealed trait Json
case class JsonObject(fields: Map[String, Json]) extends Json
case class JsonArray(elements: Vector[Json]) extends Json
case class JsonString(value: String) extends Json
case class JsonNumber(value: BigDecimal) extends Json
case class JsonBoolean(value: Boolean) extends Json
case object JsonNull extends Json

// Automatic codec derivation
case class Person(name: String, age: Int)
implicit val personCodec: JsonCodec[Person] = JsonCodec.caseCodec[Person]
```

### 3. Row Synchronization

The `RowSync` component handles data synchronization with:

- **Change detection**: Identifies inserts, updates, and deletes
- **Batch processing**: Efficient bulk operations
- **Error recovery**: Configurable retry policies
- **Progress tracking**: Real-time sync status

```scala mdoc:compile-only
trait RowSync[T] {
  def sync(): SyncResult
  def syncWithRetry(maxRetries: Int): SyncResult
  def dryRun(): SyncPreview
}

case class SyncResult(
  inserted: Long,
  updated: Long,
  deleted: Long,
  errors: List[SyncError],
  duration: Duration
)
```

### 4. Connection Pooling

HikariCP integration with smart defaults:

```scala mdoc:compile-only
case class PoolConfig(
  minSize: Int = 5,
  maxSize: Int = 20,
  connectionTimeout: Duration = 30.seconds,
  idleTimeout: Duration = 10.minutes,
  maxLifetime: Duration = 30.minutes,
  leakDetectionThreshold: Option[Duration] = Some(2.minutes)
)
```

## Design Patterns

### 1. Resource Safety

All database operations use the loan pattern:

```scala mdoc:compile-only
trait ConnFactory {
  def use[A](f: Connection => A): A
  def useTransaction[A](f: Connection => A): A
}
```

### 2. Effect Tracking

Operations that perform side effects are clearly marked:

```scala mdoc:compile-only
// Pure computation
def validateData(data: List[Row]): Either[ValidationError, List[Row]]

// Side effect - marked with IO or similar
def syncData(data: List[Row]): IO[SyncResult]
```

### 3. Error Handling

Comprehensive error handling with typed errors:

```scala mdoc:compile-only
sealed trait SyncError
case class ValidationError(field: String, message: String) extends SyncError
case class DatabaseError(sql: String, cause: SQLException) extends SyncError
case class NetworkError(endpoint: String, cause: Exception) extends SyncError
```

## Performance Considerations

### 1. Streaming

For large datasets, use streaming to avoid memory issues:

```scala mdoc:compile-only
query.streamRows[Record](conn) { stream =>
  stream
    .grouped(1000)  // Process in batches
    .foreach(processBatch)
}
```

### 2. Batch Operations

Batch inserts/updates for better performance:

```scala mdoc:compile-only
conn.batchInsert(
  table = "users",
  columns = List("name", "email"),
  rows = userData,
  batchSize = 1000
)
```

### 3. Query Optimization

The framework automatically:
- Uses prepared statements
- Enables statement caching
- Optimizes batch operations per database type

## Extension Points

### 1. Custom Database Dialects

```scala mdoc:compile-only
trait DatabaseDialect {
  def quoteIdentifier(name: String): String
  def limitClause(limit: Int): String
  def upsertStatement(table: String, columns: List[String], keys: List[String]): String
}
```

### 2. Custom Codecs

```scala mdoc:compile-only
implicit val instantCodec: JsonCodec[Instant] = JsonCodec.string.dimap(
  str => Instant.parse(str),
  instant => instant.toString
)
```

### 3. Validation Rules

```scala mdoc:compile-only
trait ValidationRule[T] {
  def validate(value: T): Either[String, T]
  def transform(value: T): T
}
```

## Module Structure

- **`logging`**: Cross-platform logging abstraction
- **`shared`**: Core utilities, JDBCF, JSON processing  
- **`api`**: High-level sync operations
- **`stager`**: Business-specific staging operations
- **`examples`**: Sample implementations

## Threading Model

- **Connection pool**: Managed by HikariCP
- **Streaming operations**: Single-threaded per stream
- **Batch operations**: Configurable parallelism
- **HTTP clients**: Async with configurable thread pool

## Security Considerations

- **SQL injection prevention**: Parameterized queries only
- **Connection encryption**: SSL/TLS support for all databases
- **Credential management**: Environment variables and secure vaults
- **Audit logging**: Built-in operation tracking