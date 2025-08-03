# Database Configuration Guide

## Overview

Accur8 Sync supports multiple database systems through JDBC. This guide covers configuration requirements for each supported database.

## Supported Databases

### PostgreSQL

**Driver**: `org.postgresql.Driver`  
**Dependency**: Already included in build.sbt

```hocon
postgresql {
  driver = "org.postgresql.Driver"
  url = "jdbc:postgresql://hostname:5432/database_name"
  user = "username"
  password = "password"
  
  # Optional settings
  pool {
    minSize = 5
    maxSize = 20
    connectionTimeout = 30s
    idleTimeout = 10m
    maxLifetime = 30m
  }
  
  # PostgreSQL specific
  properties {
    ssl = true
    sslmode = "require"
    reWriteBatchedInserts = true  # Improves batch performance
  }
}
```

**Required Permissions**:
- SELECT, INSERT, UPDATE, DELETE on target tables
- CREATE TEMPORARY TABLE (for some sync operations)
- USAGE on schemas

### MySQL

**Driver**: `com.mysql.cj.jdbc.Driver`  
**Dependency**: Already included in build.sbt

```hocon
mysql {
  driver = "com.mysql.cj.jdbc.Driver"
  url = "jdbc:mysql://hostname:3306/database_name"
  user = "username"
  password = "password"
  
  # MySQL specific
  properties {
    useSSL = true
    serverTimezone = "UTC"
    useUnicode = true
    characterEncoding = "UTF-8"
    rewriteBatchedStatements = true  # Critical for performance
  }
}
```

**Required Permissions**:
- SELECT, INSERT, UPDATE, DELETE on target tables
- CREATE TEMPORARY TABLE
- LOCK TABLES (for consistent reads)

### IBM DB2/AS400

**Driver**: `com.ibm.as400.access.AS400JDBCDriver`  
**Dependency**: Already included in build.sbt

```hocon
db2 {
  driver = "com.ibm.as400.access.AS400JDBCDriver"
  url = "jdbc:as400://hostname/library_name"
  user = "username"
  password = "password"
  
  # AS400 specific
  properties {
    naming = "sql"  # Use SQL naming convention
    libraries = "LIBRARY1,LIBRARY2"  # Library list
    dateFormat = "iso"
    timeFormat = "iso"
    prompt = false
  }
}
```

**Required Permissions**:
- *USE authority on libraries
- *CHANGE authority on tables
- *EXECUTE authority on programs

### HSQLDB (Testing)

**Driver**: `org.hsqldb.jdbc.JDBCDriver`  
**Dependency**: Already included in build.sbt

```hocon
hsqldb {
  driver = "org.hsqldb.jdbc.JDBCDriver"
  url = "jdbc:hsqldb:mem:testdb"  # In-memory
  # or
  url = "jdbc:hsqldb:file:/path/to/database"  # File-based
  user = "SA"
  password = ""
}
```

## Connection Pool Configuration

All databases use HikariCP for connection pooling. Common pool settings:

```hocon
pool {
  # Minimum number of idle connections
  minSize = 5
  
  # Maximum pool size
  maxSize = 20
  
  # Maximum time to wait for a connection
  connectionTimeout = 30s
  
  # Maximum idle time before connection is closed
  idleTimeout = 10m
  
  # Maximum lifetime of a connection
  maxLifetime = 30m
  
  # Test query (database specific)
  connectionTestQuery = "SELECT 1"  # PostgreSQL/MySQL
  # connectionTestQuery = "VALUES 1"  # DB2
}
```

## Environment Variables

Sensitive configuration should use environment variables:

```hocon
database {
  url = "jdbc:postgresql://localhost:5432/myapp"
  user = ${DB_USER}
  password = ${DB_PASSWORD}
  
  # Optional: Override entire URL
  url = ${?DATABASE_URL}
}
```

## SSL/TLS Configuration

### PostgreSQL SSL

```hocon
postgresql {
  properties {
    ssl = true
    sslmode = "require"  # or "verify-ca", "verify-full"
    sslcert = "/path/to/client-cert.pem"
    sslkey = "/path/to/client-key.pem"
    sslrootcert = "/path/to/ca-cert.pem"
  }
}
```

### MySQL SSL

```hocon
mysql {
  properties {
    useSSL = true
    requireSSL = true
    verifyServerCertificate = true
    trustCertificateKeyStoreUrl = "file:///path/to/truststore"
    trustCertificateKeyStorePassword = ${TRUSTSTORE_PASSWORD}
  }
}
```

## Performance Tuning

### PostgreSQL
```hocon
postgresql {
  properties {
    # Batch operations
    reWriteBatchedInserts = true
    
    # Fetch size for large queries
    defaultRowFetchSize = 1000
    
    # Statement caching
    preparedStatementCacheQueries = 256
    preparedStatementCacheSizeMiB = 5
  }
}
```

### MySQL
```hocon
mysql {
  properties {
    # Critical for batch performance
    rewriteBatchedStatements = true
    
    # Connection performance
    cachePrepStmts = true
    prepStmtCacheSize = 250
    prepStmtCacheSqlLimit = 2048
    
    # Network performance
    useCompression = true
    
    # Large result sets
    useCursorFetch = true
    defaultFetchSize = 1000
  }
}
```

### DB2/AS400
```hocon
db2 {
  properties {
    # Block fetch for performance
    blockCriteria = 2
    blockSize = 512
    
    # LOB handling
    lobThreshold = 1048576
    
    # Connection pooling
    pooling = true
    maxPoolSize = 20
  }
}
```

## Multiple Database Configuration

Configure multiple databases in the same application:

```hocon
accur8.sync {
  databases {
    # Primary PostgreSQL database
    primary {
      driver = "org.postgresql.Driver"
      url = ${PRIMARY_DB_URL}
      user = ${PRIMARY_DB_USER}
      password = ${PRIMARY_DB_PASSWORD}
    }
    
    # Analytics MySQL database
    analytics {
      driver = "com.mysql.cj.jdbc.Driver"
      url = ${ANALYTICS_DB_URL}
      user = ${ANALYTICS_DB_USER}
      password = ${ANALYTICS_DB_PASSWORD}
    }
    
    # Legacy AS400 system
    legacy {
      driver = "com.ibm.as400.access.AS400JDBCDriver"
      url = ${AS400_URL}
      user = ${AS400_USER}
      password = ${AS400_PASSWORD}
    }
  }
}
```

Access in code:
```scala
val primaryConn = ConnFactory.fromConfig(config.getConfig("databases.primary"))
val analyticsConn = ConnFactory.fromConfig(config.getConfig("databases.analytics"))
```

## Testing Configuration

For unit tests, use HSQLDB with test-specific settings:

```hocon
test {
  database {
    driver = "org.hsqldb.jdbc.JDBCDriver"
    url = "jdbc:hsqldb:mem:test;sql.syntax_pgs=true"  # PostgreSQL compatibility
    user = "SA"
    password = ""
    
    pool {
      minSize = 1
      maxSize = 2  # Small pool for tests
    }
  }
}
```

## Troubleshooting Common Issues

### Connection Timeouts
- Increase `connectionTimeout` in pool settings
- Check network connectivity
- Verify firewall rules

### Pool Exhaustion
- Increase `maxSize` in pool configuration
- Ensure connections are properly closed
- Check for connection leaks with pool metrics

### Slow Queries
- Enable query logging to identify bottlenecks
- Check database indexes
- Adjust `defaultFetchSize` for large result sets

### Character Encoding Issues
- Set proper encoding in MySQL: `characterEncoding=UTF-8`
- For PostgreSQL, ensure database encoding matches

### Time Zone Issues
- MySQL: Set `serverTimezone` property
- PostgreSQL: Usually handles automatically
- DB2: Set `dateFormat` and `timeFormat` to "iso"

## Monitoring

Enable HikariCP metrics:

```hocon
pool {
  metricsTrackerFactory = "com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory"
  # or
  metricsTrackerFactory = "com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory"
}
```

## Security Best Practices

1. **Never hardcode credentials** - Use environment variables or secure vaults
2. **Use SSL/TLS** for all production connections
3. **Principle of least privilege** - Grant minimal required permissions
4. **Rotate credentials regularly**
5. **Use connection pooling** to limit concurrent connections
6. **Enable query logging** in development only
7. **Validate and sanitize** all user inputs before queries