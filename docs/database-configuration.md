---
layout: page
title: Database Configuration
---

# Database Configuration

This page provides detailed configuration examples for each supported database system.

## PostgreSQL

### Basic Configuration

```scala mdoc:silent
import a8.shared.jdbcf._

val postgresConfig = DatabaseConfig(
  driver = "org.postgresql.Driver",
  url = "jdbc:postgresql://localhost:5432/mydb",
  user = "postgres",
  password = "password"
)

val connFactory = ConnFactory.fromConfig(postgresConfig)
```

### Advanced Configuration with SSL

```hocon
postgres {
  driver = "org.postgresql.Driver"
  url = "jdbc:postgresql://prod-server:5432/production_db"
  user = ${POSTGRES_USER}
  password = ${POSTGRES_PASSWORD}
  
  pool {
    minSize = 10
    maxSize = 50
    connectionTimeout = 30s
    idleTimeout = 10m
    maxLifetime = 30m
  }
  
  properties {
    ssl = true
    sslmode = "require"
    sslfactory = "org.postgresql.ssl.NonValidatingFactory"
    
    # Performance optimizations
    reWriteBatchedInserts = true
    preparedStatementCacheQueries = 256
    preparedStatementCacheSizeMiB = 5
    defaultRowFetchSize = 1000
  }
}
```

### PostgreSQL-Specific Features

```scala mdoc:compile-only
// Using PostgreSQL arrays
val tags = Array("scala", "database", "sync")
sql"""
  INSERT INTO posts (title, tags)
  VALUES ($title, $tags::text[])
""".executeUpdate(conn)

// Using JSONB
import a8.shared.json.ast._
val metadata = Json.obj("version" -> 1, "author" -> "system")
sql"""
  INSERT INTO documents (name, metadata)
  VALUES ($name, ${metadata.toString}::jsonb)
""".executeUpdate(conn)

// Using COPY for bulk insert
conn.getCopyAPI.copyIn(
  "COPY users (id, name, email) FROM STDIN WITH CSV",
  csvData
)
```

## MySQL

### Basic Configuration

```scala mdoc:silent
val mysqlConfig = DatabaseConfig(
  driver = "com.mysql.cj.jdbc.Driver",
  url = "jdbc:mysql://localhost:3306/mydb",
  user = "root",
  password = "password"
)
```

### Production Configuration

```hocon
mysql {
  driver = "com.mysql.cj.jdbc.Driver"
  url = "jdbc:mysql://mysql-cluster:3306/production"
  user = ${MYSQL_USER}
  password = ${MYSQL_PASSWORD}
  
  properties {
    # SSL configuration
    useSSL = true
    requireSSL = true
    verifyServerCertificate = false
    
    # Character encoding
    useUnicode = true
    characterEncoding = "UTF-8"
    connectionCollation = "utf8mb4_unicode_ci"
    
    # Performance
    rewriteBatchedStatements = true  # Critical for batch performance
    cachePrepStmts = true
    prepStmtCacheSize = 250
    prepStmtCacheSqlLimit = 2048
    useServerPrepStmts = true
    
    # Timeouts
    connectTimeout = 30000
    socketTimeout = 60000
    
    # Large result sets
    useCursorFetch = true
    defaultFetchSize = 1000
  }
}
```

### MySQL-Specific Features

```scala mdoc:compile-only
// Using ON DUPLICATE KEY UPDATE
sql"""
  INSERT INTO user_stats (user_id, login_count, last_login)
  VALUES ($userId, 1, NOW())
  ON DUPLICATE KEY UPDATE
    login_count = login_count + 1,
    last_login = NOW()
""".executeUpdate(conn)

// Using GROUP_CONCAT
val taggedUsers = sql"""
  SELECT user_id, GROUP_CONCAT(tag_name SEPARATOR ',') as tags
  FROM user_tags
  GROUP BY user_id
""".fetchRows[(Long, String)](conn)
```

## IBM DB2/AS400

### Basic Configuration

```scala mdoc:silent
val db2Config = DatabaseConfig(
  driver = "com.ibm.as400.access.AS400JDBCDriver",
  url = "jdbc:as400://as400server/LIBRARY",
  user = "AS400USER",
  password = "AS400PASS"
)
```

### Advanced AS400 Configuration

```hocon
as400 {
  driver = "com.ibm.as400.access.AS400JDBCDriver"
  url = "jdbc:as400://production-as400/PRODLIB"
  user = ${AS400_USER}
  password = ${AS400_PASSWORD}
  
  properties {
    # Naming convention
    naming = "sql"  # Use SQL naming (schema.table)
    # naming = "system"  # Use system naming (library/file)
    
    # Library list
    libraries = "PRODLIB,COMMONLIB,QGPL"
    
    # Date/time formats
    dateFormat = "iso"
    timeFormat = "iso"
    dateSeparator = "-"
    timeSeparator = ":"
    
    # Performance
    blockCriteria = 2  # 0=no block, 1=if FOR FETCH ONLY, 2=always
    blockSize = 512
    prefetch = true
    lazyClose = true
    
    # LOB handling
    lobThreshold = 1048576  # 1MB
    
    # Connection pooling
    pooling = true
    maxPoolSize = 50
    minPoolSize = 5
    
    # Error handling
    errors = "full"
    extendedMetadata = true
  }
}
```

### DB2-Specific Features

```scala mdoc:compile-only
// Working with RPG programs
val callStatement = conn.prepareCall("{CALL LIBRARY.PROGRAM(?, ?, ?)}")
callStatement.setString(1, "INPUT1")
callStatement.setInt(2, 123)
callStatement.registerOutParameter(3, java.sql.Types.VARCHAR)
callStatement.execute()
val result = callStatement.getString(3)

// Using MERGE (UPSERT)
sql"""
  MERGE INTO customers AS target
  USING (VALUES ($id, $name, $email)) AS source (id, name, email)
  ON target.id = source.id
  WHEN MATCHED THEN
    UPDATE SET name = source.name, email = source.email
  WHEN NOT MATCHED THEN
    INSERT (id, name, email) VALUES (source.id, source.name, source.email)
""".executeUpdate(conn)
```

## HSQLDB (Testing)

### In-Memory Configuration

```scala mdoc:silent
val hsqldbConfig = DatabaseConfig(
  driver = "org.hsqldb.jdbc.JDBCDriver",
  url = "jdbc:hsqldb:mem:testdb",
  user = "SA",
  password = ""
)
```

### File-Based Configuration

```hocon
hsqldb {
  driver = "org.hsqldb.jdbc.JDBCDriver"
  url = "jdbc:hsqldb:file:data/testdb;shutdown=true"
  user = "SA"
  password = ""
  
  properties {
    # SQL compatibility mode
    sql.syntax_pgs = true  # PostgreSQL compatibility
    # sql.syntax_mys = true  # MySQL compatibility
    # sql.syntax_db2 = true  # DB2 compatibility
    
    # Performance for testing
    hsqldb.write_delay = false
    hsqldb.log_size = 0
  }
}
```

### Testing Utilities

```scala mdoc:compile-only
import a8.shared.jdbcf.hsqldb.HsqldbTestDb

class DatabaseTest extends AnyFunSuite {
  
  test("database operations") {
    HsqldbTestDb.withTestDb { testDb =>
      // Create schema
      testDb.executeSql("""
        CREATE TABLE users (
          id BIGINT IDENTITY PRIMARY KEY,
          name VARCHAR(100) NOT NULL,
          email VARCHAR(100) UNIQUE,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
      """)
      
      // Test with connection factory
      val result = testDb.connFactory.use { conn =>
        sql"""
          INSERT INTO users (name, email)
          VALUES ('Test User', 'test@example.com')
        """.executeUpdate(conn)
      }
      
      assert(result == 1)
    }
  }
}
```

## Connection Pool Tuning

### Performance-Oriented Configuration

```hocon
performance {
  pool {
    # Core pool size
    minSize = 20
    
    # Maximum pool size
    maxSize = 100
    
    # How long to wait for a connection
    connectionTimeout = 5s
    
    # Remove idle connections after this time
    idleTimeout = 5m
    
    # Maximum connection lifetime
    maxLifetime = 30m
    
    # Detect connection leaks
    leakDetectionThreshold = 30s
    
    # Initialization SQL
    connectionInitSql = "SET time_zone = '+00:00'"
    
    # Test query
    connectionTestQuery = "SELECT 1"
  }
}
```

### Resource-Constrained Configuration

```hocon
resource-limited {
  pool {
    minSize = 2
    maxSize = 10
    connectionTimeout = 30s
    idleTimeout = 2m
    maxLifetime = 10m
    
    # Allow pool to shrink
    minimumIdle = 2
    
    # Register MBeans for monitoring
    registerMbeans = true
  }
}
```

## Multi-Database Setup

```scala mdoc:compile-only
import com.typesafe.config.ConfigFactory
import a8.shared.jdbcf._

object DatabaseManager {
  private val config = ConfigFactory.load()
  
  // Primary transactional database
  val primaryDb = ConnFactory.fromConfig(
    config.getConfig("databases.primary")
  )
  
  // Analytics read replica
  val analyticsDb = ConnFactory.fromConfig(
    config.getConfig("databases.analytics")
  )
  
  // Legacy system
  val legacyDb = ConnFactory.fromConfig(
    config.getConfig("databases.legacy")
  )
  
  // Route queries to appropriate database
  def queryRouter[T](query: Query[T]): ConnFactory = {
    query match {
      case _: AnalyticsQuery => analyticsDb
      case _: LegacyQuery => legacyDb
      case _ => primaryDb
    }
  }
}
```

## Monitoring and Metrics

### Enable HikariCP Metrics

```hocon
monitoring {
  pool {
    # Metrics
    metricsTrackerFactory = "com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory"
    
    # Health check
    healthCheckRegistry = "com.codahale.metrics.health.HealthCheckRegistry"
    
    # JMX
    registerMbeans = true
  }
}
```

### Custom Metrics Collection

```scala mdoc:compile-only
import io.micrometer.core.instrument.MeterRegistry

class MonitoredConnFactory(
  config: DatabaseConfig,
  meterRegistry: MeterRegistry
) extends ConnFactory {
  
  private val connectionTimer = meterRegistry.timer("db.connection.time")
  private val activeConnections = meterRegistry.gauge("db.connections.active", new AtomicInteger(0))
  
  override def use[A](f: Connection => A): A = {
    connectionTimer.record { () =>
      activeConnections.incrementAndGet()
      try {
        super.use(f)
      } finally {
        activeConnections.decrementAndGet()
      }
    }
  }
}
```

## Troubleshooting

### Connection Issues

```scala mdoc:compile-only
// Enable detailed logging
System.setProperty("com.zaxxer.hikari.log.level", "DEBUG")

// Test connection
val testResult = Try {
  connFactory.use { conn =>
    conn.isValid(5) // 5 second timeout
  }
}

testResult match {
  case Success(true) => println("Connection successful")
  case Success(false) => println("Connection invalid")
  case Failure(e) => println(s"Connection failed: ${e.getMessage}")
}
```

### Performance Diagnostics

```xml
<!-- Add to logback.xml for connection pool debugging -->
<logger name="com.zaxxer.hikari" level="DEBUG"/>
<logger name="com.zaxxer.hikari.pool.HikariPool" level="DEBUG"/>
<logger name="a8.shared.jdbcf" level="DEBUG"/>
```

For more details, see the [Database Setup Guide](../database-setup.html).