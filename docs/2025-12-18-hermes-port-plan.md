# Hermes Mailbox Infrastructure - Implementation Plan

## Progress Summary

### ✅ COMPLETED PHASES:
- **Phase 1: Foundation** - Module setup, protobuf generation
- **Phase 2: Core Mailbox** - Mailbox model, transport abstraction, NATS implementation
- **Phase 3: RPC System** - Schema registry, handlers, server/client with correlation tracking
- **Phase 5: Service Discovery** - Dynamic discovery implementation (static discovery to be added)

### 🚧 CURRENT STATUS:
- Config loader working with environment-based HOCON
- NATS connection successful to dev environment
- All implemented code compiles cleanly

### 📋 NEXT STEPS (Updated from godev/docs specs):
1. **Add Static Service Discovery** - `{environment}.{serviceName}` pattern with serviceRouting
2. **Update HermesBootstrap** - Integrate RPC, discovery, and exact bootstrap order from specs
3. **Implement SSH Auth** - LoginBegin/LoginComplete with simple UUID tokens (NOT signed)
4. **Implement Minion Cache** - InitMinion and ResolveServiceUid with file caching
5. **Add RPC Error Model** - ErrorNode/ErrorFrame structure with categories/kinds
6. **Implement Mailbox Identity Binding** - Store auth tokens in mailbox PrivateMetadata
7. **Add Authorization Checking** - HasRole calls using mailbox addresses as credentials

## Overview

Port godev's Hermes mailbox infrastructure to Scala, implementing a message-oriented RPC system with NATS backend. This follows the **EXACT** godev architecture from /Users/glen/code/accur8/godev/docs specifications.

**Key Architectural Decision**: This is a **client library**, not a full reimplementation. The Scala code will:
- Use the existing godev mailbox service (via RPC/HTTP endpoint) for mailbox creation and storage
- Use the existing godev auth service for SSH authentication and authorization
- Use the existing godev nefario service for process lifecycle and minion management
- NOT reimplement NATS KV storage, triple-indexed caching, or mailbox persistence
- Call godev service APIs for all server-side operations
- Focus on client-side RPC, message passing, and service integration

## Architecture Decisions (from godev/docs)

- **Effect System**: Use existing Ctx/Resource/XStream abstractions (Ox-based)
- **Authentication**: SSH-based auth with simple UUID tokens (NO Ed25519 signing)
- **Authorization**: Mailbox-based - mailbox address IS the credential after binding
- **Service Discovery**: Static `{env}.{service}` pattern PRIMARY, dynamic pub/sub secondary
- **Error Model**: Recursive ErrorNode structure with string categories/kinds (NOT numeric codes)
- **Protobuf**: ScalaPB for code generation
- **Transport**: Design abstraction upfront for NATS and future websocket/grpc support
- **Bootstrap Order**: Follow EXACT sequence: Minion → Service → NATS → Discovery → Auth → Mailbox → RPC

## Module Structure

### New `hermes` Module
Location: `/Users/glen/code/accur8/sync/hermes`

```
hermes/
├── src/main/
│   ├── scala/a8/hermes/
│   │   ├── core/          # Transport abstraction, Mailbox model
│   │   ├── nats/          # NATS implementation
│   │   ├── rpc/           # RPC server/client
│   │   ├── auth/          # SSH authentication
│   │   ├── discovery/     # Service discovery
│   │   └── bootstrap/     # Bootstrap system
│   └── protobuf/
│       └── nefario_rpc.proto  # Copy from godev
└── src/test/scala/a8/hermes/
```

### Enhanced `nats` Module
Uncomment and enhance existing NATS module with improved abstractions.

## Critical Files to Create

### 1. Transport Abstraction
**File**: `hermes/src/main/scala/a8/hermes/core/MailboxTransport.scala`

Define transport-agnostic interface:
- `publish()` - Send messages
- `subscribe()` - Receive message streams
- `createConsumer()` - Durable/ephemeral consumers
- `keyValueStore()` - Metadata storage
- `createStream()` - Stream management

Enables future websocket/grpc implementations alongside NATS.

### 2. NATS Transport Implementation
**File**: `hermes/src/main/scala/a8/hermes/nats/NatsTransport.scala`

Implement MailboxTransport using jnats library:
- Wrap io.nats.client.Connection
- Integrate with JetStream for streams (NOT KV - that's in godev service)
- Resource-based lifecycle management
- Publish/subscribe to mailbox channels

### 3. Mailbox Model
**File**: `hermes/src/main/scala/a8/hermes/core/Mailbox.scala`

Core data structures:
- **Triple-key system**: AdminKey (zzz...), ReaderKey (rrr...), MailboxAddress (aaa...)
- **Lifecycle types**: Ephemeral (24h), NonDurable (15m), Named (90d)
- **Channels**: RpcInbox, RpcSent
- **Message format**: Envelope with correlation ID, endpoint, payload

Type-safe wrappers using StringValue pattern with prefix validation.

### 4. Mailbox Client (NOT Store)
**File**: `hermes/src/main/scala/a8/hermes/core/MailboxClient.scala`

**Key Change**: We use the existing godev mailbox service via RPC/HTTP endpoint, not reimplementing storage.

Mailbox acquisition:
- Call existing mailbox creation RPC endpoint (from godev service)
- OR use pre-configured named mailbox from config
- Use service discovery to find mailbox and auth services
- No NATS KV storage implementation needed
- No triple-indexed cache needed
- Mailbox just holds metadata (address, keys) received from service

### 5. RPC System
**Files**:
- `hermes/src/main/scala/a8/hermes/rpc/RpcSchema.scala` - Schema registry
- `hermes/src/main/scala/a8/hermes/rpc/RpcServer.scala` - Handler registry and processing
- `hermes/src/main/scala/a8/hermes/rpc/RpcClient.scala` - Call method with correlation tracking
- `hermes/src/main/scala/a8/hermes/rpc/RpcHandler.scala` - Handler trait

Key features:
- Schema-based organization: `{name}.{version}.{method}` (e.g., "process.v1.Shutdown")
- Case-insensitive handler lookup
- Correlation tracking via TrieMap[correlationId → Promise[Message]]
- Protobuf and JSON support

### 6. SSH Authentication
**File**: `hermes/src/main/scala/a8/hermes/auth/SshAuth.scala`

**CRITICAL: Tokens are simple UUIDs, NOT Ed25519 signed tokens**

SSH authentication flow:
1. **LoginBegin**: Send SSH public key → receive session_id + nonce (32 bytes)
2. **Sign nonce**: Use ssh-keygen subprocess: `echo -n <nonce> | ssh-keygen -Y sign -f ~/.ssh/id_ed25519 -n nefario`
3. **LoginComplete**: Send session_id + signature → receive auth_token (simple UUID string)
4. **Bind to mailbox**: Store token in mailbox PrivateMetadata: `{"authToken": "uuid..."}`
5. **Save token**: Write to `~/.a8/auth_token` for reuse

**Token Format:**
- Just a UUID string (e.g., `"f8a3b2c1-4d5e-6f7g-8h9i-0j1k2l3m4n5o"`)
- Stored in `qubes.auth` table
- NO signing, NO expiration metadata (just opaque token)

**Database Lookup:**
- SSH fingerprint: `SELECT * FROM qubes.usercredentials WHERE provider='ssh' AND rawdata=$fingerprint`
- Fingerprint is SHA256 hash of public key
- If not found → Error: "SSH key not authorized"

### 7. Service Discovery
**File**: `hermes/src/main/scala/a8/hermes/discovery/ServiceDiscovery.scala`

**PRIMARY METHOD - Static Discovery:**
- Pattern: `{environment}.{serviceName}` → mailbox address
- Resolution priority: 1) `serviceRouting` config overrides, 2) Default pattern
- Example: `local.auth` → auth service mailbox
- Config override for single-process dev: `serviceRouting: {"auth": "hermes-local"}`

**SECONDARY - Dynamic Discovery (implemented but not yet used by CLI tools):**
- Subject: `nefario.discovery`
- Query filters: ImplementsRpc, AppName, ServiceName, Location
- Auto-populate RPC schemas from registered handlers
- ServiceInfo includes: ProcessUid, MailboxAddress, Capabilities

### 8. Bootstrap System
**Files**:
- `hermes/src/main/scala/a8/hermes/bootstrap/HermesBootstrapConfig.scala`
- `hermes/src/main/scala/a8/hermes/bootstrap/HermesBootstrap.scala`
- `hermes/src/main/scala/a8/hermes/bootstrap/HermesBootstrappedApp.scala`

**EXACT Bootstrap Order (from godev specs):**

**Step 1: Minion Initialization**
1. Read `~/.config/minion/minion-cache.json`
2. If missing → Call `InitMinion` RPC (hostname, user) → get minionUid, serverUid
3. Cache results to file

**Step 2: Service Resolution**
1. Read `~/.config/minion/service-cache/{serviceName}.txt`
2. If missing → Call `ResolveServiceUid(serviceName, minionUid)` → get serviceUid
3. Cache to file

**Step 3: Connect to NATS**
1. Load config from `~/.config/hermes/bootstrap.conf`
2. Parse environment and natsUrl
3. Create NATS connection with credentials

**Step 4: Service Discovery (Static)**
1. Resolve service mailboxes using `{environment}.{serviceName}` pattern
2. Apply `serviceRouting` overrides from config
3. Cache resolved mailbox addresses

**Step 5: Authenticate (if SSH key configured)**
1. LoginBegin → receive session_id + nonce
2. Sign nonce with ssh-keygen
3. LoginComplete → receive auth token (UUID)
4. Save token to `~/.a8/auth_token`

**Step 6: Acquire/Create Mailbox**
1. Option A: Use pre-configured named mailbox from config
2. Option B: Call mailbox service to create ephemeral mailbox
3. Bind auth token to mailbox: Store in PrivateMetadata

**Step 7: Start RPC Services**
1. Create RPC server with mailbox
2. Register RPC handlers
3. Subscribe to RPC inbox subject
4. Optionally register with dynamic service discovery

**Step 8: Process Lifecycle (for process-based apps)**
1. Publish ProcessStartedRequest to nefario.central
2. Start ping scheduler (every 30s)
3. On exit → Publish ProcessCompletedRequest

Components created:
- NATS connection
- Transport
- Mailbox client (calls godev service, NOT local storage)
- Mailbox (from godev service response or config)
- RPC server/client
- Auth client
- Service discovery

Integration with existing Bootstrapper via trait extension.

### 9. Protobuf Definitions
**Copy**: `/Users/glen/code/accur8/godev/nefario/rpc/nefario_rpc.proto` → `hermes/src/main/protobuf/nefario_rpc.proto`

Modify package declaration:
```protobuf
package nefario_rpc;
option java_package = "a8.hermes.proto.nefario";
option java_multiple_files = true;
```

Message types to port:
- Process lifecycle: ProcessStartedRequest, ProcessPingRequest, ProcessCompletedRequest
- RPC pairs: ResolveServiceUid, InitMinion, GetHealthchecksConfig, NixFlake CRUD, PushMetrics
- Service management: ListServicesRequest/Response, ServiceRecord
- MessageFromLaunchy wrapper (oneof pattern)

### 10. Build Configuration
**File**: `build.sbt`

Add hermes module:
```scala
lazy val hermes =
  Common
    .jvmProject("a8-hermes", file("hermes"), "hermes")
    .dependsOn(shared, nats)
    .enablePlugins(Fs2Grpc)
    .settings(
      Compile / PB.targets := Seq(
        scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
      ),
      libraryDependencies ++= Seq(
        "io.nats" % "jnats" % "2.20.4",
        "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
        "com.github.luben" % "zstd-jni" % "1.5.6-8",
        "org.bouncycastle" % "bcprov-jdk18on" % "1.77",
        "com.hierynomus" % "sshj" % "0.38.0",
      )
    )
```

Uncomment nats module and add dependencies.

Add to `project/plugins.sbt`:
```scala
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.15"
```

## Key Patterns & Integration

### Ctx/Resource/XStream Integration

**Resource composition** (following RequestProcessor pattern):
```scala
val hermesStack = for {
  natsClient <- NatsClient.resource(config.nats)
  transport = NatsTransport(natsClient)
  // Use service discovery to find mailbox service
  mailboxServiceAddr <- discoverService("mailbox")
  // Call godev mailbox service to create/get mailbox
  mailbox <- MailboxClient.acquire(mailboxServiceAddr, transport)
  rpcServer <- RpcServer.resource(RpcServer.Config(mailbox))
} yield Components(...)
```

**XStream for message consumption** (following JDBC streaming pattern):
```scala
def subscribe(channel: Channel)(using Ctx): XStream[Message] = {
  XStream.acquireRelease {
    val subscription = transport.subscribe(subject)
    (subscription, subscription.iterator)
  } { sub => sub.unsubscribe() }
}
```

### Type Safety Improvements

**Sealed trait enums** (vs Go string constants):
```scala
sealed trait LifecycleType { def ttl: FiniteDuration }
object LifecycleType {
  case object Ephemeral extends LifecycleType { val ttl = 24.hours }
  case object NonDurable extends LifecycleType { val ttl = 15.minutes }
  case class Named(name: String) extends LifecycleType { val ttl = 90.days }
}
```

**Validated newtypes** (vs Go type aliases):
```scala
case class AdminKey(value: String) extends StringValue {
  require(value.startsWith("zzz"), "AdminKey must start with zzz")
}
```

**Context functions** (vs explicit context passing):
```scala
// Go: func Send(ctx context.Context, to Address, msg Message) error
// Scala:
def send(to: MailboxAddress, message: Message)(using Ctx): Unit
```

### Configuration

Use existing @CompanionGen pattern:
```scala
@CompanionGen
case class HermesBootstrapConfig(
  nats: NatsClient.Config,
  sshKeyPath: Option[String],
  authServiceMailbox: Option[String],
  namedMailboxes: Map[String, String],
  autoRenewAuth: Boolean = true,
  authRenewalInterval: FiniteDuration = 12.hours,
)
```

**ACTUAL Config Format (from godev specs):**
```hocon
# ~/.config/hermes/bootstrap.conf
{
  env: dev  # or: local, prod
  # env: prod

  environments {
    dev {
      natsUrl: "nats://dev:dev@localhost:4222"
      namedMailboxes {
        nefario: nefario-rpc
        scheduler: nefario-rpc
        auth: nefario-rpc
        mailbox: nefario-rpc
        db: nefario-rpc
      }
    }

    local {
      natsUrl: "nats://localhost:4222"
      # Single-process dev mode - all services route to one mailbox
      serviceRouting {
        auth: "hermes-local"
        nefario: "hermes-local"
        scheduler: "hermes-local"
      }
    }

    prod {
      natsUrl: "nats://nefario:password@nats1.vpn:4222,nats://nefario:password@nats2.vpn:4222"
      namedMailboxes {
        nefario: nefario-prod
        scheduler: nefario-prod
        auth: nefario-prod
        mailbox: nefario-prod
      }
    }
  }
}
```

**Key Config Fields:**
- `env`: Environment selector (dev, local, prod)
- `environments.{env}.natsUrl`: NATS connection string (can have multiple comma-separated)
- `environments.{env}.namedMailboxes`: Map service name → mailbox address
- `environments.{env}.serviceRouting`: Override static discovery (for single-process dev mode)
- `environments.{env}.sshKeyPath`: Optional path to SSH private key

## Implementation Phases

### Phase 1: Foundation
**Goal**: Module setup and protobuf generation

1. Add hermes module to build.sbt
2. Configure ScalaPB plugin
3. Copy nefario_rpc.proto from godev
4. Generate Scala code from protobuf
5. Enhance nats module with basic wrappers

**Deliverable**: Compiled module with generated protobuf code

### Phase 2: Core Mailbox
**Goal**: Working mailbox infrastructure

1. Implement Mailbox model (keys, metadata, lifecycle)
2. Implement MailboxTransport trait
3. Implement NatsTransport
4. Implement MailboxClient (calls godev mailbox service RPC endpoint)
5. Implement NatsMailbox (send, subscribe to channels)

**Deliverable**: Mailbox acquisition from godev service, message passing working

### Phase 3: RPC System
**Goal**: Request/response pattern

1. Implement RpcSchema registry
2. Implement RpcContext and RpcHandler
3. Implement RpcServer (registration, processing)
4. Implement RpcClient (correlation tracking)
5. Integration tests for RPC round-trips

**Deliverable**: Working RPC with handler registration and calls

### Phase 4: Authentication
**Goal**: SSH auth flow

1. SSH signature generation via ssh-keygen subprocess
2. SSH wire format parsing
3. LoginBegin/LoginComplete handlers
4. Token caching and auto-renewal
5. Bind identity to mailbox

**Deliverable**: End-to-end SSH authentication working

### Phase 5: Service Discovery
**Goal**: Service registration and queries

1. ServiceDiscovery pub/sub implementation
2. Query filtering logic
3. Service registration on startup
4. Auto-populate RPC schemas
5. Discovery integration tests

**Deliverable**: Service discovery with filtering

### Phase 6: Bootstrap Integration
**Goal**: Complete initialization system

1. HermesBootstrapConfig implementation
2. HermesBootstrap resource composition
3. HermesBootstrappedApp trait
4. Integration with existing Bootstrapper
5. Example application

**Deliverable**: Full bootstrap system, working example app

### Phase 7: Nefario RPC Handlers
**Goal**: Port all RPC endpoints

1. Process lifecycle handlers (ProcessStarted, Ping, Completed)
2. Service management (ResolveServiceUid, InitMinion)
3. Healthchecks integration (GetHealthchecksConfig)
4. NixFlake CRUD (Get, Create, Update, List)
5. Metrics (PushMetrics)

**Deliverable**: All Nefario RPCs ported and tested

### Phase 8: Polish
**Goal**: Production readiness

1. Performance testing and optimization
2. Comprehensive error handling
3. Logging integration
4. Documentation (ScalaDoc, user guide)
5. Migration guide from godev

**Deliverable**: Production-ready implementation

## Testing Strategy

### Unit Tests
- Mailbox key validation (prefix requirements)
- Lifecycle type TTL calculations
- RPC schema normalization (case-insensitive)
- Message serialization/deserialization

### Integration Tests
- NATS connection and reconnection
- Mailbox creation and KV persistence
- RPC round-trips with correlation
- SSH authentication flow
- Service discovery queries

### Mock Transport
Create MockTransport for testing RPC handlers without NATS:
```scala
class MockTransport extends MailboxTransport {
  private val publishedMessages = mutable.Buffer.empty[Envelope]

  override def publish(...)(using Ctx): Unit = {
    publishedMessages += Envelope(...)
  }

  def getPublishedMessages: Seq[Envelope] = publishedMessages.toSeq
}
```

## Error Handling - RPC Error Model

**USE RECURSIVE ErrorNode STRUCTURE, NOT numeric codes**

### Protobuf Structure (from nefario_rpc.proto):
```protobuf
message ErrorFrame {
  string category = 1;  // "RPC", "AUTH", "CLIENT", "DEPENDENCY", "INTERNAL"
  string kind = 2;      // "TIMEOUT", "NOT_FOUND", "DB_UNAVAILABLE", etc.
  string message = 3;   // Human-readable
  bool retryable = 4;   // Retry hint
  google.protobuf.Struct details = 5;  // Structured metadata
  string component = 6; // Service name
  ErrorLocation location = 7;  // Debug info (file, line, function)
  google.protobuf.Timestamp timestamp = 8;
}

message ErrorNode {
  ErrorFrame frame = 1;
  repeated ErrorNode causes = 2;  // Recursive - preserves error chain
}

message RpcErrorInfo {
  ErrorNode error_node = 1;  // NEW model (preferred)
  uint32 errorCode = 2;      // LEGACY (deprecated)
  string message = 3;        // LEGACY
}
```

### Error Categories and Kinds:

**Category.CLIENT:**
- `INVALID_ARGUMENT` - Invalid input parameter
- `NOT_FOUND` - Requested resource doesn't exist
- `CONFLICT` - Operation conflicts with current state

**Category.AUTH:**
- `UNAUTHENTICATED` - Missing or invalid credentials
- `PERMISSION_DENIED` - Valid credentials but insufficient permissions

**Category.RPC:**
- `BAD_REQUEST` - Malformed RPC request
- `TIMEOUT` - Request exceeded time limit
- `CANCELLED` - Request cancelled by caller

**Category.DEPENDENCY:**
- `DB_UNAVAILABLE` - Database connection/query failed
- `NATS_UNAVAILABLE` - NATS messaging unavailable
- `UPSTREAM_UNAVAILABLE` - External service unavailable

**Category.RESOURCE:**
- `RATE_LIMITED` - Too many requests
- `QUOTA_EXCEEDED` - Resource quota exhausted
- `OVERLOADED` - System at capacity

**Category.INTERNAL:**
- `UNEXPECTED` - Unexpected error condition
- `INVARIANT_VIOLATION` - System invariant violated

### Scala Implementation:
```scala
// Define Scala ADT matching protobuf
sealed trait ErrorCategory
object ErrorCategory {
  case object CLIENT extends ErrorCategory
  case object AUTH extends ErrorCategory
  case object RPC extends ErrorCategory
  case object DEPENDENCY extends ErrorCategory
  case object RESOURCE extends ErrorCategory
  case object INTERNAL extends ErrorCategory
}

case class ErrorFrame(
  category: String,
  kind: String,
  message: String,
  retryable: Boolean,
  details: Map[String, String] = Map.empty,
  component: String,
  location: Option[ErrorLocation] = None,
  timestamp: java.time.Instant = java.time.Instant.now()
)

case class ErrorNode(
  frame: ErrorFrame,
  causes: Seq[ErrorNode] = Seq.empty
)

// Error wrapping utility
object RPCError {
  def wrap(
    category: String,
    kind: String,
    message: String,
    retryable: Boolean,
    details: Map[String, String] = Map.empty,
    component: String,
    cause: Option[ErrorNode] = None
  ): ErrorNode = {
    ErrorNode(
      frame = ErrorFrame(category, kind, message, retryable, details, component),
      causes = cause.toSeq
    )
  }
}
```

### Best Practices:
1. **Choose appropriate categories** - Use CLIENT for validation, DEPENDENCY for downstream failures
2. **Set retryable correctly** - true for transient errors (timeout, overload), false for invalid input
3. **Include structured details** - Entity IDs, query params (NO passwords, secrets, or large data)
4. **Wrap, don't discard** - Preserve error context by wrapping causes
5. **Use string categories/kinds** - NOT numeric error codes

## Dependencies Summary

### New Dependencies
- `io.nats:jnats:2.20.4` - NATS client library
- `com.thesamet.scalapb:scalapb-runtime` - ScalaPB runtime
- `com.github.luben:zstd-jni:1.5.6-8` - Compression
- `org.bouncycastle:bcprov-jdk18on:1.77` - SSH crypto
- `com.hierynomus:sshj:0.38.0` - SSH operations

### SBT Plugins
- `com.thesamet:sbt-protoc:1.0.6` - Protobuf compiler
- `scalapb-compiler-plugin:0.11.15` - ScalaPB code generator

### Already Available (from shared)
- `com.softwaremill.ox:core:0.7.0` - Structured concurrency
- JSON codecs via @CompanionGen
- RequestProcessor retry patterns

## Critical Implementation Details

### NATS Naming Conventions
- **Subjects**: Use dots for hierarchy - `hermes.{adminKey}.{channel}`
- **Streams**: Use dashes, no dots - `hermes-{adminKey}-{channel}`
- **Consumers**: Avoid dots (triggers ErrInvalidConsumerName)

### Mailbox Key Prefixes
Consistent prefixes for debugging and indexing:
- AdminKey: `zzz...` (full control)
- ReaderKey: `rrr...` (read-only)
- Address: `aaa...` (public identifier)

### Correlation Tracking
Critical for RPC:
- Register correlation callback BEFORE sending request
- Use TrieMap for thread-safe concurrent access
- Clean up after response or timeout
- Prevents memory leaks from abandoned requests

### Message Flow
1. Client generates correlationId, registers Promise
2. Publish SendMessageRequest to recipient's RpcInbox
3. Recipient's RunRpcInboxReader receives message
4. ProcessRpcCall: lookup handler, check auth, execute
5. Send response to caller's RpcInbox with correlationId
6. Caller's RunRpcInboxReader matches correlationId, completes Promise

### Authorization - Mailbox-Based Model

**CRITICAL: After binding, mailbox address BECOMES the credential**

**Authorization Flow (every RPC call):**
1. Extract `msg.Header.Sender` (mailbox address) from incoming RPC
2. Call auth service: `HasRole(mailboxAddress, role)` (NOT auth token!)
3. Auth service:
   - Query MailboxStore: `GetMailboxIdentity(mailboxAddress)`
   - Extract auth token from mailbox PrivateMetadata
   - Database query: `is_authorized_to(token, role)`
   - Return allowed/denied
4. Process or reject request based on response

**Role Format:**
- Simple: `"admin"` (string)
- Structured: `{"group": "admin", "resource": "/api/*"}` (JSON object)

**Database Infrastructure:**
- `qubes.usergroup` - group definitions
- `qubes.groupmembership` - user → group mappings
- `is_authorized_to(token, group)` - PostgreSQL function

**Client-Side:**
- NO auth token in RPC request headers
- Mailbox address is sufficient (token bound to mailbox)

## Comparison to godev

### Similarities
- Two-phase bootstrap (New → Start)
- Triple-key mailbox system
- Schema-based RPC organization
- SSH authentication flow
- Service discovery protocol
- NATS JetStream for persistence

### Improvements
- Type-safe enums (sealed traits vs string constants)
- Validated newtypes (compile-time key prefix validation)
- Resource composition (automatic cleanup)
- Context functions (cleaner API)
- ScalaPB type-safe protobuf (vs runtime reflection)
- Immutable data structures by default
- Pattern matching for error handling

### Compatibility
Messages on wire are 100% compatible:
- Same protobuf definitions
- Same NATS subject/stream naming
- Same authentication protocol
- Go and Scala services can interoperate seamlessly
