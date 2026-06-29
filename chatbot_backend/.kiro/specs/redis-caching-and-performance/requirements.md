# Requirements Document

## Introduction

This feature introduces Redis caching, connection pooling, and algorithmic optimizations to the Chatbot Workflow Engine. The application currently suffers from redundant database queries (workflow definitions loaded multiple times per request), an unbounded in-memory pending-session map, per-request RestClient instantiation, fixed-delay retry logic, and linear processor lookups. These changes improve throughput, reduce latency, eliminate a memory leak, and enable multi-instance deployments.

## Infrastructure Context

- **Redis**: Running locally via Docker on `localhost:6379` with no authentication
- **Serialization**: All cached entities use JSON serialization (human-readable, debuggable via Redis CLI)
- **Configuration**: All TTLs and pool sizes are externalized in `application.properties` for runtime tuning without redeployment
- **Graceful Degradation**: If Redis is unavailable (for both reads and writes), the system degrades to direct PostgreSQL access without raising errors to callers

## Glossary

- **Workflow_Cache**: A Redis-backed cache storing serialized Workflow entities keyed by workflow ID with a configurable TTL
- **ApiConfig_Cache**: A Redis-backed cache storing serialized ApiConfig entities (with headers, payload, and response mappings) keyed by ApiConfig ID with a configurable TTL
- **Pending_Session_Store**: A Redis-backed data structure that stores pending chat session IDs with an automatic expiration, replacing the in-memory ConcurrentHashMap
- **RestClient_Pool**: A pool or cache of pre-configured RestClient instances grouped by timeout configuration, avoiding per-request instantiation
- **Exponential_Backoff**: A retry delay strategy where wait time doubles after each failed attempt (e.g., 1s, 2s, 4s) up to a configurable maximum
- **Processor_Registry**: A Map-based lookup structure that provides O(1) access to NodeProcessor instances by node type, replacing linear iteration
- **TTL**: Time-To-Live; the duration after which a cached entry automatically expires
- **Cache_Eviction**: The act of explicitly removing a cached entry before its TTL expires, triggered by data mutations (create, update, delete)

## Requirements

### Requirement 1: Workflow Definition Caching

**User Story:** As the system, I want workflow definitions cached in Redis, so that repeated database queries during a single workflow execution are eliminated.

#### Acceptance Criteria

1. WHEN a Workflow is requested by ID, THE Workflow_Cache SHALL return the cached Workflow if present and not expired. IF Redis is unavailable for reads, THEN THE Workflow_Cache SHALL fall back to loading directly from PostgreSQL without raising an error
2. WHEN a Workflow is requested by ID and the cache does not contain the entry, THE Workflow_Cache SHALL load the Workflow from PostgreSQL, store it in Redis using JSON serialization, and return it. IF storing to Redis fails, THEN THE Workflow_Cache SHALL still return the loaded Workflow to the caller without raising an error
3. THE Workflow_Cache SHALL store entries with a configurable TTL (configured via `application.properties`) defaulting to 10 minutes
4. WHEN a Workflow is updated via the REST API, THE Workflow_Cache SHALL evict the cached entry for that workflow ID
5. WHEN a Workflow is deleted via the REST API, THE Workflow_Cache SHALL evict the cached entry for that workflow ID
6. WHEN the cache entry has expired, THE Workflow_Cache SHALL treat the next request as a cache miss and reload from PostgreSQL

### Requirement 2: ApiConfig Caching

**User Story:** As the system, I want API configurations cached in Redis, so that each API node execution does not require a database round-trip.

#### Acceptance Criteria

1. WHEN an ApiConfig is requested by ID, THE ApiConfig_Cache SHALL return the cached ApiConfig (including headers, payload template, and response mappings) if present and not expired. IF Redis is unavailable for reads, THEN THE ApiConfig_Cache SHALL fall back to loading directly from PostgreSQL without raising an error
2. WHEN an ApiConfig is requested by ID and the cache does not contain the entry, THE ApiConfig_Cache SHALL load the full ApiConfig entity graph from PostgreSQL, store it in Redis using JSON serialization, and return it. IF storing to Redis fails, THEN THE ApiConfig_Cache SHALL still return the loaded ApiConfig to the caller without raising an error
3. THE ApiConfig_Cache SHALL store entries with a configurable TTL (configured via `application.properties`) defaulting to 10 minutes
4. WHEN an ApiConfig is updated via the REST API, THE ApiConfig_Cache SHALL evict the cached entry for that ApiConfig ID
5. WHEN an ApiConfig is deleted via the REST API, THE ApiConfig_Cache SHALL evict the cached entry for that ApiConfig ID
6. WHEN an ApiConfig is requested by name, THE ApiConfig_Cache SHALL first attempt to resolve the name to an ID via a name-to-ID index in Redis, and if found, retrieve the ApiConfig from the ID-based cache. IF the name-to-ID index does not contain the entry, THEN the system SHALL query PostgreSQL directly and populate both the name-to-ID index and the ID-based cache

### Requirement 3: Pending Sessions in Redis

**User Story:** As a platform operator, I want pending chat sessions stored in Redis with automatic expiration, so that the application does not leak memory and supports multi-instance deployments.

#### Acceptance Criteria

1. WHEN a new chat session is initialized via `/app/chat.init`, THE Pending_Session_Store SHALL store the session ID in Redis with a configurable TTL (configured via `application.properties`) defaulting to 5 minutes
2. WHEN a pending session is consumed (workflow start), THE Pending_Session_Store SHALL atomically remove the session ID from Redis and return a success indicator
3. WHEN a session ID does not exist in the Pending_Session_Store, THE Pending_Session_Store SHALL return a failure indicator (session not found or expired)
4. WHILE multiple application instances are running, THE Pending_Session_Store SHALL provide consistent session validation across all instances
5. THE Pending_Session_Store SHALL not retain session IDs beyond the configured TTL, preventing unbounded growth

### Requirement 4: RestClient Connection Pooling

**User Story:** As the system, I want RestClient instances reused based on timeout configuration, so that connection overhead is reduced and resources are not wasted on per-request instantiation.

#### Acceptance Criteria

1. WHEN an HTTP request is executed with a given timeout value, THE RestClient_Pool SHALL return an existing RestClient configured with that timeout if one is available
2. WHEN no RestClient exists for the requested timeout value and the pool has not reached its maximum size, THE RestClient_Pool SHALL create a new RestClient, cache it, and return it
3. THE RestClient_Pool SHALL enforce a configurable maximum pool size (configured via `application.properties`) to cap the number of distinct RestClient instances
4. THE RestClient_Pool SHALL use connection pooling via Apache HttpClient (or similar) to reuse underlying TCP connections
5. THE RestClient_Pool SHALL be thread-safe for concurrent access from multiple workflow executions

### Requirement 5: Exponential Backoff for Retries

**User Story:** As the system, I want HTTP retries to use exponential backoff, so that downstream services are not overwhelmed during outages and thread blocking time is reduced for early retries.

#### Acceptance Criteria

1. WHEN a retryable HTTP error occurs (5xx or connection timeout), THE HttpExecutor SHALL wait an exponentially increasing delay before the next attempt
2. THE HttpExecutor SHALL use exact exponential delays with a base delay of 1 second, doubling for each subsequent retry (1s, 2s, 4s, 8s, ...)
3. THE HttpExecutor SHALL cap the maximum delay at 10 seconds regardless of retry count
4. IF the retry wait is interrupted, THEN THE HttpExecutor SHALL restore the thread interrupt flag and stop retrying
5. THE HttpExecutor SHALL preserve existing behavior where 4xx client errors are not retried

### Requirement 6: Processor Lookup Optimization

**User Story:** As the system, I want node processor lookups to use a map-based registry, so that processor resolution is O(1) instead of O(n) linear iteration.

#### Acceptance Criteria

1. THE Processor_Registry SHALL build a Map from node type identifiers to NodeProcessor instances at application startup
2. WHEN a node requires processing, THE Processor_Registry SHALL resolve the appropriate NodeProcessor in O(1) time using the node type as the lookup key
3. IF a node type has no registered processor, THEN THE Processor_Registry SHALL fall back to the MessageNodeProcessor
4. THE Processor_Registry SHALL support all existing node types (message, input, api, decision, workflow) without requiring changes to NodeProcessor implementations

## Future Considerations (Out of Scope)

### WebSocket Message Broadcasting Across Instances

Currently, Spring's in-memory STOMP broker maintains WebSocket subscriptions per JVM. When multiple application instances run behind a load balancer, a message published on Instance B cannot reach a user whose WebSocket connection lives on Instance A.

**Options to address this later:**

1. **Redis Pub/Sub STOMP Relay** — Replace the in-memory broker with a Redis-backed external broker. All instances subscribe to Redis channels, and whichever instance holds the WebSocket connection delivers the message to the client.
2. **Sticky Sessions at Load Balancer** — Route all requests from the same client to the same instance (simpler but reduces horizontal scaling benefits).
3. **Dedicated Message Broker (RabbitMQ/Kafka)** — Use a full-featured broker for cross-instance message delivery with ordering guarantees.

**Why deferred:** The current spec removes blocking dependencies (in-memory sessions, per-instance cache) that are prerequisites for multi-instance deployments. WebSocket relay is a follow-up step when actually deploying behind a load balancer with multiple instances.
