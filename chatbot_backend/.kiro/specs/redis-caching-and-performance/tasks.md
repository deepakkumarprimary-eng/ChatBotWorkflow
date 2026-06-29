# Implementation Plan: Redis Caching and Performance

## Overview

This plan implements Redis-backed caching, connection pooling, and algorithmic optimizations across six areas: Workflow caching, ApiConfig caching, pending session store, RestClient pool, exponential backoff, and processor registry. Each task builds incrementally, wiring components together at the end.

## Tasks

- [x] 1. Add dependencies and configuration
  - [x] 1.1 Add Maven dependencies for Spring Data Redis and Apache HttpClient 5
    - Add `spring-boot-starter-data-redis` and `httpclient5` to `pom.xml`
    - _Requirements: 1.1, 2.1, 3.1, 4.4_

  - [x] 1.2 Add Redis and pool configuration to application.properties
    - Add `spring.data.redis.host`, `spring.data.redis.port`, `spring.data.redis.timeout`, `spring.data.redis.connect-timeout`
    - Add `cache.workflow.ttl-minutes=10`, `cache.apiconfig.ttl-minutes=10`, `session.pending.ttl-minutes=5`
    - Add `http.client.pool.max-size=20`, `http.client.pool.max-connections-per-route=20`, `http.client.pool.max-connections-total=100`
    - Add `http.retry.base-delay-ms=1000`, `http.retry.max-delay-ms=10000`
    - _Requirements: 1.3, 2.3, 3.1, 4.3, 5.2, 5.3_

- [x] 2. Implement Workflow caching
  - [x] 2.1 Create WorkflowCacheService with read-through cache logic
    - Create `com.xpressbees.chatbot.service.WorkflowCacheService`
    - Implement `findById(Long workflowId)` with Redis GET → deserialize → return; on miss → PostgreSQL load → Redis SET with TTL → return
    - Implement `evict(Long workflowId)` to DEL from Redis
    - Wrap all Redis calls in try-catch for graceful degradation (log WARN, fall back to PostgreSQL)
    - Add `@JsonIgnore` on any back-references in Workflow entity if needed for serialization
    - _Requirements: 1.1, 1.2, 1.3, 1.6_

  - [x]* 2.2 Write property test for Workflow cache serialization round-trip
    - **Property 1: Workflow cache serialization round-trip**
    - **Validates: Requirements 1.1, 1.2**

  - [x] 2.3 Integrate WorkflowCacheService into WorkflowExecutionServiceImpl
    - Replace `workflowRepository.findById()` calls with `workflowCacheService.findById()`
    - _Requirements: 1.1, 1.2_

  - [x] 2.4 Add cache eviction to WorkflowController on update and delete
    - Call `workflowCacheService.evict(id)` in update and delete endpoints
    - _Requirements: 1.4, 1.5_

  - [x]* 2.5 Write property test for Workflow cache eviction on mutation
    - **Property 2: Workflow cache eviction on mutation**
    - **Validates: Requirements 1.4, 1.5**

- [x] 3. Implement ApiConfig caching
  - [x] 3.1 Create ApiConfigCacheService with read-through cache and name-to-ID index
    - Create `com.xpressbees.chatbot.service.ApiConfigCacheService`
    - Implement `findById(Long apiConfigId)` with Redis read-through pattern
    - Implement `findByName(String name)` using `apiconfig:name:{name}` → resolve ID → delegate to `findById`
    - Implement `evict(Long apiConfigId, String name)` to DEL both keys
    - Add `@JsonIgnore` on back-references in ApiHeader, ApiPayload, ApiResponseMapping entities to avoid circular serialization
    - Graceful degradation on all Redis operations
    - _Requirements: 2.1, 2.2, 2.3, 2.6_

  - [x]* 3.2 Write property test for ApiConfig cache serialization round-trip
    - **Property 3: ApiConfig cache serialization round-trip (full entity graph)**
    - **Validates: Requirements 2.1, 2.2**

  - [x]* 3.3 Write property test for ApiConfig name-to-ID index consistency
    - **Property 4: ApiConfig name-to-ID index consistency**
    - **Validates: Requirements 2.6**

  - [x] 3.4 Integrate ApiConfigCacheService into ApiNodeProcessor
    - Replace direct repository calls with `apiConfigCacheService.findById()` or `findByName()`
    - _Requirements: 2.1, 2.6_

  - [x] 3.5 Add cache eviction to ApiConfigController on create, update, and delete
    - Call `apiConfigCacheService.evict(id, name)` in create, update, and delete endpoints
    - _Requirements: 2.4, 2.5_

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement Pending Session Store in Redis
  - [x] 5.1 Create PendingSessionStore service
    - Create `com.xpressbees.chatbot.service.PendingSessionStore`
    - Implement `register(String sessionId)` — SET key `pending-session:{sessionId}` with value `"1"` and TTL
    - Implement `consume(String sessionId)` — DEL key and return true if key existed (atomic)
    - Graceful degradation: if Redis unavailable on register, return false; on consume, return false
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 5.2 Integrate PendingSessionStore into ChatWebSocketHandler
    - Replace `ConcurrentHashMap<String, Instant> pendingSessions` usage with `PendingSessionStore`
    - Update `onChatInit()` to call `pendingSessionStore.register(sessionId)`
    - Update `consumePendingSession()` to delegate to `pendingSessionStore.consume(sessionId)`
    - _Requirements: 3.1, 3.2, 3.3_

  - [x]* 5.3 Write property test for pending session atomic consume
    - **Property 5: Pending session atomic consume (exactly-once)**
    - **Validates: Requirements 3.2, 3.3**

- [x] 6. Implement RestClient Connection Pool
  - [x] 6.1 Create RestClientPool component
    - Create `com.xpressbees.chatbot.service.RestClientPool`
    - Use `ConcurrentHashMap<Integer, RestClient>` for thread-safe O(1) lookup by timeout
    - Build each `RestClient` with Apache HttpClient 5 `PoolingHttpClientConnectionManager`
    - Cap pool size at configurable max; return closest-timeout client when full
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 6.2 Integrate RestClientPool into HttpExecutor
    - Replace `buildRestClient(int timeoutMs)` with `restClientPool.getClient(timeoutMs)`
    - Remove the inline `SimpleClientHttpRequestFactory` creation
    - _Requirements: 4.1, 4.2_

  - [x]* 6.3 Write property test for RestClient pool consistency and bounded size
    - **Property 6: RestClient pool consistency and bounded size**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.5**

- [x] 7. Implement Exponential Backoff
  - [x] 7.1 Modify HttpExecutor to use exponential backoff
    - Add `computeDelay(int attemptNumber)` method: `min(baseDelay * 2^(attempt-1), maxDelay)`
    - Replace `sleepBeforeRetry()` with `sleepBeforeRetry(int attemptNumber)` using computed delay
    - Externalize `baseDelay` and `maxDelay` via `@Value` from application.properties
    - Preserve existing behavior: 4xx errors not retried, interrupt flag restored on InterruptedException
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [x]* 7.2 Write property test for exponential backoff delay formula
    - **Property 7: Exponential backoff delay formula**
    - **Validates: Requirements 5.1, 5.2, 5.3**

  - [x]* 7.3 Write property test for 4xx client errors not retried
    - **Property 8: 4xx client errors are never retried**
    - **Validates: Requirements 5.5**

- [x] 8. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement Processor Registry
  - [x] 9.1 Add `getNodeType()` default method to NodeProcessor interface
    - Add `default String getNodeType() { return null; }` to `NodeProcessor` interface
    - Override in each processor: MessageNodeProcessor → `"message"`, InputNodeProcessor → `"input"`, ApiNodeProcessor → `"api"`, DecisionNodeProcessor → `"decision"`, WorkflowNodeProcessor → `"workflow"`
    - _Requirements: 6.1, 6.4_

  - [x] 9.2 Create ProcessorRegistry component
    - Create `com.xpressbees.chatbot.service.ProcessorRegistry`
    - Build a `HashMap<String, NodeProcessor>` at startup from injected `List<NodeProcessor>`
    - Implement `getProcessor(String nodeType)` with fallback to MessageNodeProcessor
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 9.3 Integrate ProcessorRegistry into WorkflowExecutionServiceImpl
    - Replace `findProcessor(Map<String, Object> node)` method with call to `processorRegistry.getProcessor(nodeType)`
    - Extract node type from node map and pass to registry
    - _Requirements: 6.2_

  - [x]* 9.4 Write property test for Processor registry lookup correctness
    - **Property 9: Processor registry lookup correctness**
    - **Validates: Requirements 6.2, 6.3**

- [x] 10. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- All Redis operations use graceful degradation — system remains functional if Redis is down
- Jackson `ObjectMapper` with `JavaTimeModule` is used for entity serialization
- `@JsonIgnore` annotations prevent circular references in entity graph serialization

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1", "3.1", "5.1", "6.1", "7.1", "9.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "3.2", "3.3", "3.4", "5.2", "5.3", "6.2", "6.3", "7.2", "7.3", "9.2"] },
    { "id": 3, "tasks": ["2.4", "2.5", "3.5", "9.3", "9.4"] }
  ]
}
```
