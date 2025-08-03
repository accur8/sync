---
layout: home
title: Accur8 Sync
---

# Accur8 Sync

**Version**: @VERSION@  
**Scala Version**: @SCALA_VERSION@

Accur8 Sync is a powerful Scala-based data synchronization framework designed for enterprise data pipelines. It provides a type-safe, functional approach to ETL/ELT operations with support for multiple database systems.

## Key Features

- **Multi-Database Support**: PostgreSQL, MySQL, IBM DB2/AS400, and HSQLDB
- **Type-Safe Operations**: Leverages Scala's type system for compile-time safety
- **Streaming Support**: Handle large datasets without memory constraints
- **JSON Processing**: Built-in JSON serialization with custom codecs
- **Connection Pooling**: Efficient resource management with HikariCP
- **Validation Framework**: Comprehensive data validation and transformation

## Quick Navigation

- [Getting Started](getting-started.md) - Installation and basic setup
- [API Documentation](api/index.html) - Complete API reference
- [Database Configuration](database-configuration.md) - Database-specific setup
- [Examples](examples.md) - Code examples and patterns
- [Logging Guide](logging-guide.md) - Production logging setup
- [Architecture](architecture.md) - System design and concepts

## Installation

Add to your `build.sbt`:

```scala mdoc:silent
val accur8SyncVersion = "@VERSION@"

libraryDependencies ++= Seq(
  "io.accur8" %% "a8-sync-shared" % accur8SyncVersion,
  "io.accur8" %% "a8-sync-api" % accur8SyncVersion,
  "io.accur8" %% "a8-logging" % accur8SyncVersion
)
```

## Quick Example

```scala mdoc:silent
import a8.shared.SharedImports._
import a8.shared.jdbcf._

// Configure database connection
val dbConfig = DatabaseConfig(
  driver = "org.postgresql.Driver",
  url = "jdbc:postgresql://localhost:5432/mydb",
  user = "dbuser",
  password = "dbpass"
)

// Create connection factory
val connFactory = ConnFactory.fromConfig(dbConfig)

// Execute a query
val results = connFactory.use { conn =>
  sql"SELECT id, name FROM users WHERE active = true"
    .fetchRows[(Long, String)](conn)
}
```

## Support

- [GitHub Issues](https://github.com/accur8/sync/issues) - Bug reports and feature requests
- [API Documentation](api/index.html) - Detailed API reference
- [Examples](https://github.com/accur8/sync/tree/master/examples) - Working code examples