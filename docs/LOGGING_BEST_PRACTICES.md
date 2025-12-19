# Logging Best Practices Guide for Accur8 Sync

## Overview

Accur8 Sync uses SLF4J as the logging facade with Logback as the default implementation. This guide covers best practices for configuring logging in production environments.

## Quick Setup

### 1. Dependencies

The logging dependencies are already included when you use Accur8 Sync:

```scala
libraryDependencies ++= Seq(
  "io.accur8" %% "a8-logging" % "latest-version",
  "io.accur8" %% "a8-logging-logback" % "latest-version" // Optional but recommended
)
```

### 2. Basic Logback Configuration

Create `src/main/resources/logback.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Console appender for development -->
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- File appender for production -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/accur8-sync.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/accur8-sync.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>3GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level %logger - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- Async wrapper for better performance -->
    <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="FILE"/>
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
    </appender>
    
    <!-- Logger configurations -->
    <logger name="a8.sync" level="INFO"/>
    <logger name="a8.shared.jdbcf" level="WARN"/>
    <logger name="com.zaxxer.hikari" level="WARN"/>
    
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
        <appender-ref ref="ASYNC"/>
    </root>
</configuration>
```

## Production Configuration

### 1. Structured Logging with JSON

For production environments, use JSON formatting for easier parsing:

```xml
<configuration>
    <appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/accur8-sync.json</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/accur8-sync.%d{yyyy-MM-dd}.json.gz</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <!-- Add custom fields -->
            <customFields>{"app":"accur8-sync","env":"${ENV:-dev}"}</customFields>
            <!-- Include MDC fields -->
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>requestId</includeMdcKeyName>
            <includeMdcKeyName>syncJobId</includeMdcKeyName>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="JSON_FILE"/>
    </root>
</configuration>
```

### 2. Performance-Optimized Configuration

```xml
<configuration>
    <!-- High-performance async appender -->
    <appender name="ASYNC_PROD" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="FILE"/>
        <queueSize>2048</queueSize>
        <discardingThreshold>20</discardingThreshold>
        <neverBlock>true</neverBlock>
        <!-- Include caller data only for ERROR level -->
        <includeCallerData>false</includeCallerData>
    </appender>
    
    <!-- Conditional logging based on level -->
    <turboFilter class="ch.qos.logback.classic.turbo.DuplicateMessageFilter">
        <AllowedRepetitions>5</AllowedRepetitions>
        <CacheSize>100</CacheSize>
    </turboFilter>
</configuration>
```

### 3. Environment-Specific Configuration

```xml
<configuration>
    <!-- Load environment-specific config -->
    <include resource="logback-${ENV:-dev}.xml" optional="true"/>
    
    <!-- Default configuration -->
    <springProfile name="production">
        <appender name="PROD" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>/var/log/accur8-sync/app.log</file>
            <!-- Production-specific settings -->
        </appender>
    </springProfile>
    
    <springProfile name="development">
        <appender name="DEV" class="ch.qos.logback.core.ConsoleAppender">
            <!-- Development-specific settings -->
        </appender>
    </springProfile>
</configuration>
```

## Logging in Code

### 1. Basic Usage

```scala
import a8.shared.Logging

class DataSyncService extends Logging {
  
  def syncData(source: String, target: String): SyncResult = {
    logger.info(s"Starting sync from $source to $target")
    
    try {
      val result = performSync()
      logger.info(s"Sync completed: ${result.recordsProcessed} records in ${result.duration}")
      result
    } catch {
      case e: SQLException =>
        logger.error(s"Database error during sync", e)
        throw e
    }
  }
}
```

### 2. Using MDC for Context

```scala
import org.slf4j.MDC
import a8.shared.Logging

class SyncJobRunner extends Logging {
  
  def runJob(jobId: String, userId: String): Unit = {
    // Add context that will appear in all log messages
    MDC.put("syncJobId", jobId)
    MDC.put("userId", userId)
    
    try {
      logger.info("Starting sync job")
      // All log messages in this thread will include jobId and userId
      performSync()
      logger.info("Sync job completed")
    } finally {
      // Clean up MDC
      MDC.remove("syncJobId")
      MDC.remove("userId")
    }
  }
}
```

### 3. Structured Logging with Markers

```scala
import org.slf4j.{Marker, MarkerFactory}
import a8.shared.Logging

class DatabaseSync extends Logging {
  
  private val SQL_MARKER: Marker = MarkerFactory.getMarker("SQL")
  private val PERFORMANCE_MARKER: Marker = MarkerFactory.getMarker("PERFORMANCE")
  
  def executeQuery(sql: String): ResultSet = {
    val startTime = System.currentTimeMillis()
    
    logger.debug(SQL_MARKER, s"Executing query: $sql")
    
    val result = db.execute(sql)
    val duration = System.currentTimeMillis() - startTime
    
    if (duration > 1000) {
      logger.warn(PERFORMANCE_MARKER, s"Slow query detected: ${duration}ms - $sql")
    }
    
    result
  }
}
```

## SQL Query Logging

### 1. Enable SQL Logging Selectively

```scala
import a8.shared.jdbcf.LoggingSql._

// Enable for specific operations
val debugResult = connFactory.withSqlLogging.use { conn =>
  // All SQL in this block will be logged
  performDatabaseOperations(conn)
}

// Or configure via logback.xml
<logger name="a8.shared.jdbcf.SqlLogger" level="DEBUG"/>
```

### 2. Production SQL Logging Configuration

```xml
<!-- Log only slow queries in production -->
<logger name="a8.shared.jdbcf.SqlLogger" level="WARN">
    <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
        <level>WARN</level>
    </filter>
</logger>

<!-- Separate file for SQL queries -->
<appender name="SQL_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/sql.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/sql.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
        <maxHistory>7</maxHistory>
    </rollingPolicy>
    <encoder>
        <pattern>%d{ISO8601} [%thread] %msg%n</pattern>
    </encoder>
</appender>

<logger name="a8.shared.jdbcf.SqlLogger" additivity="false">
    <appender-ref ref="SQL_FILE"/>
</logger>
```

## Performance Considerations

### 1. Log Levels and Their Impact

| Level | Use Case | Performance Impact |
|-------|----------|-------------------|
| ERROR | Critical errors requiring immediate attention | Minimal |
| WARN | Issues that should be investigated | Minimal |
| INFO | Important business events | Low |
| DEBUG | Detailed diagnostic information | Medium |
| TRACE | Very detailed diagnostic information | High |

### 2. Conditional Logging

```scala
// Bad - string concatenation happens even if DEBUG is disabled
logger.debug("Processing " + records.size + " records: " + records.mkString(","))

// Good - string only built if DEBUG is enabled
if (logger.isDebugEnabled) {
  logger.debug(s"Processing ${records.size} records: ${records.mkString(",")}")
}

// Better - using lazy evaluation
logger.debug(s"Processing ${records.size} records")
logger.trace(s"Record details: ${records.mkString(",")}")
```

### 3. Async Logging Configuration

```xml
<!-- Optimize async appender for high throughput -->
<appender name="ASYNC_OPTIMIZED" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="FILE"/>
    
    <!-- Larger queue for high-volume logging -->
    <queueSize>8192</queueSize>
    
    <!-- Start discarding at 20% capacity -->
    <discardingThreshold>20</discardingThreshold>
    
    <!-- Never block the application -->
    <neverBlock>true</neverBlock>
    
    <!-- Don't include caller data (expensive) -->
    <includeCallerData>false</includeCallerData>
    
    <!-- Maximum time to wait on shutdown -->
    <maxFlushTime>30000</maxFlushTime>
</appender>
```

## Monitoring and Alerting

### 1. Error Rate Monitoring

```scala
class MonitoredSync extends Logging {
  private val errorCounter = new AtomicLong(0)
  
  def sync(): Unit = {
    try {
      performSync()
    } catch {
      case e: Exception =>
        val errorCount = errorCounter.incrementAndGet()
        logger.error(s"Sync failed (error #$errorCount)", e)
        
        // Alert if error rate is high
        if (errorCount > 10) {
          logger.error("HIGH ERROR RATE DETECTED - Manual intervention required")
        }
        throw e
    }
  }
}
```

### 2. Log Aggregation Pattern

```xml
<!-- Send critical errors to a separate file for monitoring -->
<appender name="ERRORS_ONLY" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/errors.log</file>
    <filter class="ch.qos.logback.classic.filter.LevelFilter">
        <level>ERROR</level>
        <onMatch>ACCEPT</onMatch>
        <onMismatch>DENY</onMismatch>
    </filter>
    <encoder>
        <pattern>%d{ISO8601} [%thread] %logger - %msg%n%ex{full}</pattern>
    </encoder>
</appender>
```

## Security Best Practices

### 1. Sanitize Sensitive Data

```scala
class SecureLogging extends Logging {
  
  def logUserAction(userId: String, action: String, creditCard: String): Unit = {
    // Never log sensitive data
    val maskedCard = maskCreditCard(creditCard)
    logger.info(s"User $userId performed $action with card ending in $maskedCard")
  }
  
  private def maskCreditCard(card: String): String = {
    if (card.length >= 4) "****" + card.takeRight(4)
    else "****"
  }
}
```

### 2. Configure Log File Permissions

```bash
# Set appropriate permissions on log directory
chmod 750 /var/log/accur8-sync
chown app-user:app-group /var/log/accur8-sync

# Logrotate configuration
/var/log/accur8-sync/*.log {
    daily
    rotate 30
    compress
    delaycompress
    notifempty
    create 0640 app-user app-group
    sharedscripts
    postrotate
        # Signal app to reopen log files if needed
    endscript
}
```

## Troubleshooting

### 1. Debug Logging Issues

```xml
<!-- Enable Logback debugging -->
<configuration debug="true">
    <statusListener class="ch.qos.logback.core.status.OnConsoleStatusListener"/>
    <!-- Your configuration -->
</configuration>
```

### 2. Common Issues and Solutions

| Issue | Solution |
|-------|----------|
| No logs appearing | Check classpath for logback.xml, verify logger names |
| Performance degradation | Use async appenders, increase queue size, reduce log level |
| Disk space issues | Configure rotation policy, reduce retention period |
| Missing stack traces | Check filters, ensure `%ex` in pattern |
| Duplicate log entries | Check for multiple appender references, set additivity="false" |

## Integration with Monitoring Systems

### 1. Prometheus Metrics

```scala
import io.prometheus.client.Counter

object LogMetrics {
  val errorCounter = Counter.build()
    .name("sync_errors_total")
    .help("Total number of sync errors")
    .labelNames("error_type")
    .register()
}

class MetricizedLogging extends Logging {
  def logError(errorType: String, message: String, cause: Throwable): Unit = {
    logger.error(message, cause)
    LogMetrics.errorCounter.labels(errorType).inc()
  }
}
```

### 2. ELK Stack Integration

Configure Logstash input:

```ruby
input {
  file {
    path => "/var/log/accur8-sync/*.json"
    start_position => "beginning"
    codec => "json"
  }
}

filter {
  if [level] == "ERROR" {
    mutate {
      add_tag => [ "alert" ]
    }
  }
}

output {
  elasticsearch {
    hosts => ["localhost:9200"]
    index => "accur8-sync-%{+YYYY.MM.dd}"
  }
}
```