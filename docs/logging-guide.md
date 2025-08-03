---
layout: page
title: Logging Guide
---

# Production Logging Guide

This guide covers best practices for configuring and using logging in Accur8 Sync applications.

## Basic Setup

Accur8 Sync uses SLF4J with Logback. The dependencies are included automatically:

```scala mdoc:silent
libraryDependencies ++= Seq(
  "io.accur8" %% "a8-logging" % "@VERSION@",
  "io.accur8" %% "a8-logging-logback" % "@VERSION@"
)
```

## Using the Logger

Extend the `Logging` trait to add logging to your classes:

```scala mdoc:silent
import a8.shared.Logging

class DataProcessor extends Logging {
  
  def processData(data: List[String]): Unit = {
    logger.info(s"Processing ${data.size} records")
    
    data.foreach { record =>
      try {
        processRecord(record)
      } catch {
        case e: Exception =>
          logger.error(s"Failed to process record: $record", e)
      }
    }
    
    logger.info("Processing complete")
  }
  
  private def processRecord(record: String): Unit = {
    // Processing logic
  }
}
```

## Configuration Examples

### Development Configuration

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %highlight(%-5level) %cyan(%logger{36}) - %msg%n</pattern>
        </encoder>
    </appender>
    
    <logger name="a8.sync" level="DEBUG"/>
    <logger name="a8.shared.jdbcf" level="DEBUG"/>
    
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

### Production Configuration

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- JSON file appender for production -->
    <appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/accur8-sync.json</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/accur8-sync.%d{yyyy-MM-dd}.json.gz</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"app":"accur8-sync","version":"@VERSION@"}</customFields>
        </encoder>
    </appender>
    
    <!-- Async wrapper for performance -->
    <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="JSON_FILE"/>
        <queueSize>2048</queueSize>
        <neverBlock>true</neverBlock>
    </appender>
    
    <logger name="a8.sync" level="INFO"/>
    <logger name="a8.shared.jdbcf" level="WARN"/>
    
    <root level="INFO">
        <appender-ref ref="ASYNC"/>
    </root>
</configuration>
```

## MDC (Mapped Diagnostic Context)

Use MDC to add context to all log messages in a thread:

```scala mdoc:compile-only
import org.slf4j.MDC
import a8.shared.Logging

class SyncService extends Logging {
  
  def syncDatabase(jobId: String, source: String, target: String): Unit = {
    // Add context
    MDC.put("jobId", jobId)
    MDC.put("source", source)
    MDC.put("target", target)
    
    try {
      logger.info("Starting database sync")
      // All logs in this thread will include jobId, source, and target
      performSync()
      logger.info("Sync completed successfully")
    } finally {
      // Clean up MDC
      MDC.clear()
    }
  }
  
  private def performSync(): Unit = {
    // Implementation
  }
}
```

## SQL Query Logging

Enable SQL logging for debugging:

```scala mdoc:compile-only
import a8.shared.jdbcf.LoggingSql._

// Enable for specific operations
val result = connFactory.withSqlLogging.use { conn =>
  // All SQL will be logged at DEBUG level
  conn.query("SELECT * FROM users")
}

// Or configure in logback.xml:
// <logger name="a8.shared.jdbcf.SqlLogger" level="DEBUG"/>
```

## Performance Best Practices

### 1. Use Appropriate Log Levels

```scala mdoc:compile-only
// ERROR - System is broken, needs immediate attention
logger.error("Database connection failed", exception)

// WARN - Something unexpected but recoverable
logger.warn(s"Slow query detected: ${duration}ms")

// INFO - Important business events
logger.info(s"Sync completed: ${recordCount} records")

// DEBUG - Detailed diagnostic info
logger.debug(s"Query parameters: $params")

// TRACE - Very detailed tracing
logger.trace(s"Entering method with args: $args")
```

### 2. Avoid Expensive Operations

```scala mdoc:compile-only
// Bad - concatenation happens even if DEBUG is disabled
logger.debug("Records: " + records.mkString(","))

// Good - only evaluated if DEBUG is enabled
if (logger.isDebugEnabled) {
  logger.debug(s"Records: ${records.mkString(",")}")
}

// Better - use lazy evaluation with placeholders
logger.debug("Processing {} records", records.size)
```

### 3. Async Logging

Configure async appenders for high-throughput applications:

```xml
<appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="FILE"/>
    <queueSize>8192</queueSize>
    <discardingThreshold>20</discardingThreshold>
    <neverBlock>true</neverBlock>
</appender>
```

## Structured Logging

Use structured logging for better searchability:

```scala mdoc:compile-only
import net.logstash.logback.argument.StructuredArguments._
import a8.shared.Logging

class StructuredLoggingExample extends Logging {
  
  def processOrder(orderId: String, amount: BigDecimal, items: Int): Unit = {
    // Structured fields will be included in JSON output
    logger.info("Order processed",
      value("orderId", orderId),
      value("amount", amount),
      value("itemCount", items),
      keyValue("status", "completed")
    )
  }
}
```

## Monitoring Integration

### Error Alerting

```scala mdoc:compile-only
class AlertingLogger extends Logging {
  private val errorThreshold = 10
  private val errorCount = new java.util.concurrent.atomic.AtomicInteger(0)
  
  def logError(message: String, error: Throwable): Unit = {
    logger.error(message, error)
    
    val count = errorCount.incrementAndGet()
    if (count >= errorThreshold) {
      logger.error("ERROR THRESHOLD EXCEEDED - Alerting operations team")
      // Send alert via your monitoring system
      errorCount.set(0) // Reset counter
    }
  }
}
```

### Metrics Collection

```xml
<!-- Separate appender for metrics -->
<appender name="METRICS" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/metrics.log</file>
    <encoder>
        <pattern>%d{ISO8601} %msg%n</pattern>
    </encoder>
</appender>

<logger name="metrics" level="INFO" additivity="false">
    <appender-ref ref="METRICS"/>
</logger>
```

## Security Considerations

Never log sensitive information:

```scala mdoc:compile-only
class SecureLogger extends Logging {
  
  def logUserAction(userId: String, password: String, action: String): Unit = {
    // NEVER log passwords or sensitive data
    logger.info(s"User $userId performed action: $action")
    // NOT: logger.info(s"User $userId with password $password...")
  }
  
  def logPayment(cardNumber: String, amount: BigDecimal): Unit = {
    val maskedCard = "**** **** **** " + cardNumber.takeRight(4)
    logger.info(s"Payment processed: $maskedCard for $$amount")
  }
}
```

## Troubleshooting

Enable Logback debugging to diagnose configuration issues:

```xml
<configuration debug="true">
    <statusListener class="ch.qos.logback.core.status.OnConsoleStatusListener"/>
    <!-- Your configuration -->
</configuration>
```

Common issues:
- **No logs**: Check for `logback.xml` in classpath
- **Performance**: Use async appenders, reduce log levels
- **Disk space**: Configure rotation and retention policies
- **Missing logs**: Verify logger names match package structure

## Additional Resources

- [Full Logging Best Practices Document](../logging-best-practices.html)
- [Logback Documentation](http://logback.qos.ch/documentation.html)
- [SLF4J Manual](http://www.slf4j.org/manual.html)