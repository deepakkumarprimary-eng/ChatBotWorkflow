# Implementation Plan: Production Readiness

## Overview

This plan implements six production-readiness enhancements for the Chatbot Workflow Engine: health monitoring with custom Actuator indicators, structured JSON logging with correlation IDs, environment-specific configuration profiles, stale session cleanup, graceful shutdown, and OpenAPI documentation. Each task builds incrementally — infrastructure and configuration first, then service components, then wiring and integration.

## Tasks

- [x] 1. Add dependencies and configure Actuator health monitoring
  - [x] 1.1 Add production-readiness Maven dependencies to pom.xml
    - Add `logstash-logback-encoder:7.4` for structured JSON logging
    - Add `springdoc-openapi-starter-webmvc-ui:2.3.0` for OpenAPI documentation
    - Add `micrometer-registry-prometheus` (optional, already pulled via actuator starter)
    - Actuator dependency already exists — update `management.endpoints.web.exposure.include` in application.properties to expose `health,metrics,info`
    - _Requirements: 1.6, 1.7, 6.1_

  - [x] 1.2 Implement WebSocketHealthIndicator
    - Create `src/main/java/com/xpressbees/chatbot/config/WebSocketHealthIndicator.java`
    - Implement `HealthIndicator` interface
    - Inject `ConnectionLimitInterceptor` to get active connection count
    - Read `chatbot.health.websocket.degraded-threshold` from config (default 800)
    - Return DEGRADED when count exceeds threshold, UP otherwise
    - Include `activeConnections` and `threshold` in health details
    - _Requirements: 1.2, 1.4_

  - [x] 1.3 Implement WorkflowEngineHealthIndicator
    - Create `src/main/java/com/xpressbees/chatbot/config/WorkflowEngineHealthIndicator.java`
    - Implement `HealthIndicator` interface
    - Inject `ApplicationContext` and check for `WorkflowExecutionService` bean availability
    - Return UP if bean is present and accessible, DOWN with exception otherwise
    - _Requirements: 1.5_

  - [x] 1.4 Configure Actuator endpoints in application.properties
    - Update `management.endpoints.web.exposure.include` to include `health,metrics,info`
    - Add `management.endpoint.health.show-details=always`
    - Add `management.info.env.enabled=true` and set `info.app.name`, `info.app.version`, `info.app.build-time`
    - Add `chatbot.health.websocket.degraded-threshold=800`
    - _Requirements: 1.1, 1.6, 1.7_

  - [x]* 1.5 Write property test for WebSocket health indicator threshold correctness
    - **Property 1: WebSocket health indicator threshold correctness**
    - **Validates: Requirements 1.4**
    - Create `src/test/java/com/xpressbees/chatbot/config/WebSocketHealthIndicatorPropertyTest.java`
    - Use jqwik to generate random `(connectionCount, threshold)` pairs
    - Assert: DEGRADED when count > threshold, UP when count <= threshold

  - [x]* 1.6 Write unit tests for health indicators
    - Create `src/test/java/com/xpressbees/chatbot/config/WebSocketHealthIndicatorTest.java`
    - Create `src/test/java/com/xpressbees/chatbot/config/WorkflowEngineHealthIndicatorTest.java`
    - Test scenarios: 0 connections, exactly at threshold, one above threshold
    - Test: bean present → UP, bean missing → DOWN
    - _Requirements: 1.2, 1.4, 1.5_

- [x] 2. Implement structured logging with correlation IDs
  - [x] 2.1 Create CorrelationIdManager utility component
    - Create `src/main/java/com/xpressbees/chatbot/service/CorrelationIdManager.java`
    - Implement `set(String sessionId)`, `clear()`, and `get()` methods using SLF4J MDC
    - Use constant key `"correlationId"` for the MDC entry
    - Mark as `@Component` with constructor injection
    - _Requirements: 2.2, 2.4_

  - [x] 2.2 Integrate correlation ID into WorkflowExecutionServiceImpl
    - Inject `CorrelationIdManager` into `WorkflowExecutionServiceImpl`
    - Call `correlationIdManager.set(sessionId)` at entry of `startWorkflow()` and `handleUserInput()`
    - Call `correlationIdManager.clear()` in all exit paths (finally blocks)
    - Add SLF4J logging: INFO for workflow start/completion, DEBUG for node processing, WARN for recoverable errors, ERROR for unexpected failures
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8_

  - [x] 2.3 Create logback-spring.xml with profile-conditional configuration
    - Create `src/main/resources/logback-spring.xml`
    - Configure prod profile: JSON output using `LogstashEncoder` with `correlationId` MDC key included
    - Configure dev/staging profiles: pattern layout with `[%X{correlationId}]` in pattern
    - Set root logger level and package-specific levels
    - _Requirements: 2.9_

  - [x]* 2.4 Write property test for MDC correlation ID lifecycle
    - **Property 2: MDC correlation ID lifecycle round-trip**
    - **Validates: Requirements 2.2, 2.4**
    - Create `src/test/java/com/xpressbees/chatbot/service/CorrelationIdManagerPropertyTest.java`
    - Use jqwik to generate random session ID strings
    - Assert: after set(), MDC contains the ID; after clear(), MDC does not contain the ID

  - [x]* 2.5 Write unit tests for CorrelationIdManager
    - Create `src/test/java/com/xpressbees/chatbot/service/CorrelationIdManagerTest.java`
    - Test set/get/clear lifecycle
    - Test thread isolation (MDC is thread-local)
    - _Requirements: 2.2, 2.4_

- [x] 3. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Configure environment-specific profiles
  - [x] 4.1 Update application-dev.properties
    - Set `spring.jpa.show-sql=true`
    - Set HikariCP max pool size to 5
    - Set `chatbot.cleanup.enabled=true` (with option to disable)
    - Ensure text-based console logging (no JSON)
    - _Requirements: 3.2, 3.6, 4.6_

  - [x] 4.2 Update application-staging.properties
    - Set `spring.jpa.show-sql=false`
    - Set HikariCP max pool size to 10
    - Configure moderate connection pool timeouts
    - Ensure text-based console logging
    - _Requirements: 3.3_

  - [x] 4.3 Update application-prod.properties
    - Set `spring.jpa.show-sql=false`
    - Set HikariCP max pool size to 20
    - Set `spring.datasource.hikari.connection-timeout=30000`
    - Set `spring.datasource.hikari.idle-timeout=600000`
    - Set `spring.datasource.hikari.max-lifetime=1800000`
    - Add `springdoc.swagger-ui.enabled=false` (disabled by default in prod)
    - _Requirements: 3.4, 3.5, 6.5_

  - [x] 4.4 Verify default profile activation in application.properties
    - Confirm `spring.profiles.default=dev` is set (already present)
    - Ensure shared configuration is in base application.properties
    - _Requirements: 3.6_

- [x] 5. Implement stale session cleanup
  - [x] 5.1 Add expireStaleSessions query to ChatSessionRepository
    - Add `@Modifying` `@Query` method to `ChatSessionRepository`
    - Query: `UPDATE ChatSession c SET c.status = 'expired' WHERE c.status = 'active' AND c.updatedAt < :cutoff`
    - Return `int` count of affected rows
    - _Requirements: 4.1, 4.3_

  - [x] 5.2 Create StaleSessionCleanupService
    - Create `src/main/java/com/xpressbees/chatbot/service/StaleSessionCleanupService.java`
    - Annotate with `@Service` and `@ConditionalOnProperty(name = "chatbot.cleanup.enabled", havingValue = "true", matchIfMissing = true)`
    - Inject `ChatSessionRepository`
    - Read `chatbot.cleanup.inactivity-threshold-hours` (default 24) from config
    - Implement `@Scheduled(fixedDelayString = "${chatbot.cleanup.interval-ms:3600000}")` method
    - Calculate cutoff as `LocalDateTime.now().minusHours(threshold)`
    - Call repository method and log result at INFO level
    - Add `@EnableScheduling` to the main application class or a configuration class
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

  - [x] 5.3 Add database index for stale session query performance
    - Add to `schema.sql`: `CREATE INDEX IF NOT EXISTS idx_chat_session_status_updated ON chat_session(status, updated_at) WHERE status = 'active';`
    - _Requirements: 4.1_

  - [x]* 5.4 Write property test for stale session cleanup correctness
    - **Property 3: Stale session cleanup correctness**
    - **Validates: Requirements 4.1, 4.3**
    - Create `src/test/java/com/xpressbees/chatbot/service/StaleSessionCleanupPropertyTest.java`
    - Use jqwik to generate random sets of sessions with varied timestamps and thresholds
    - Assert: only sessions with status "active" AND updatedAt < cutoff are expired; all others unchanged

  - [x]* 5.5 Write unit tests for StaleSessionCleanupService
    - Create `src/test/java/com/xpressbees/chatbot/service/StaleSessionCleanupServiceTest.java`
    - Test: default threshold of 24 hours produces correct cutoff
    - Test: disabled via `chatbot.cleanup.enabled=false` — bean not created
    - _Requirements: 4.2, 4.5, 4.6_

- [x] 6. Implement graceful shutdown
  - [x] 6.1 Create ExecutionTracker component
    - Create `src/main/java/com/xpressbees/chatbot/service/ExecutionTracker.java`
    - Use `AtomicInteger` for active execution count and `volatile boolean` for shutdown state
    - Implement `tryStart()` — returns false if shutting down, increments counter otherwise
    - Implement `complete()` — decrements counter
    - Implement `getActiveCount()`, `beginShutdown()`, `isShuttingDown()`
    - _Requirements: 5.2_

  - [x] 6.2 Create GracefulShutdownListener
    - Create `src/main/java/com/xpressbees/chatbot/service/GracefulShutdownListener.java`
    - Implement `ApplicationListener<ContextClosedEvent>`
    - Inject `ExecutionTracker` and read `chatbot.shutdown.timeout-seconds` (default 30)
    - On context close: call `beginShutdown()`, poll `getActiveCount()` until 0 or timeout
    - If timeout reached, log WARN with count of interrupted executions
    - _Requirements: 5.2, 5.3, 5.4_

  - [x] 6.3 Integrate ExecutionTracker into WorkflowExecutionServiceImpl
    - Inject `ExecutionTracker` into `WorkflowExecutionServiceImpl`
    - Call `executionTracker.tryStart()` at entry of `startWorkflow()` and `handleUserInput()`
    - If `tryStart()` returns false, reject the request (application is shutting down)
    - Call `executionTracker.complete()` in finally blocks
    - _Requirements: 5.1, 5.2_

  - [x] 6.4 Configure Spring Boot graceful shutdown
    - Add `server.shutdown=graceful` to application.properties
    - Add `spring.lifecycle.timeout-per-shutdown-phase=30s` to application.properties
    - Add `chatbot.shutdown.timeout-seconds=30` to application.properties
    - _Requirements: 5.3, 5.5, 5.6_

  - [x]* 6.5 Write unit tests for ExecutionTracker and GracefulShutdownListener
    - Create `src/test/java/com/xpressbees/chatbot/service/ExecutionTrackerTest.java`
    - Create `src/test/java/com/xpressbees/chatbot/service/GracefulShutdownListenerTest.java`
    - Test: tryStart() returns false when shutting down
    - Test: concurrent increment/decrement integrity
    - Test: timeout behavior with mock ExecutionTracker
    - _Requirements: 5.2, 5.3, 5.4_

- [x] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement OpenAPI documentation
  - [x] 8.1 Create OpenApiConfig configuration class
    - Create `src/main/java/com/xpressbees/chatbot/config/OpenApiConfig.java`
    - Define `@Bean OpenAPI chatbotOpenAPI()` with title, version, description
    - _Requirements: 6.1_

  - [x] 8.2 Annotate WorkflowController with OpenAPI annotations
    - Add `@Tag(name = "Workflows", description = "Workflow CRUD operations")` to class
    - Add `@Operation(summary, description)` to each endpoint method
    - Add `@ApiResponse` annotations with response schemas
    - Add `@Parameter` annotations for path/query parameters
    - _Requirements: 6.4, 6.6_

  - [x] 8.3 Annotate ApiConfigController with OpenAPI annotations
    - Add `@Tag(name = "API Configurations", description = "API configuration CRUD operations")` to class
    - Add `@Operation(summary, description)` to each endpoint method
    - Add `@ApiResponse` annotations with response schemas
    - Add `@Parameter` annotations for path/query parameters
    - _Requirements: 6.4, 6.6_

  - [x] 8.4 Configure SpringDoc properties
    - Add `springdoc.api-docs.path=/v3/api-docs` to application.properties
    - Add `springdoc.swagger-ui.path=/swagger-ui.html` to application.properties
    - Add `springdoc.swagger-ui.enabled=true` to base config (overridden per profile)
    - _Requirements: 6.2, 6.3, 6.5_

  - [x]* 8.5 Write property test for OpenAPI documentation completeness
    - **Property 4: OpenAPI endpoint documentation completeness**
    - **Validates: Requirements 6.4**
    - Create `src/test/java/com/xpressbees/chatbot/config/OpenApiCompletenessPropertyTest.java`
    - Load Spring context, fetch `/v3/api-docs` JSON
    - For each REST endpoint in WorkflowController and ApiConfigController, verify path entry exists with non-empty description
    - Verify POST/PUT methods have request body schema

  - [x]* 8.6 Write integration test for OpenAPI endpoints
    - Create `src/test/java/com/xpressbees/chatbot/integration/OpenApiIntegrationTest.java`
    - Verify `/v3/api-docs` returns valid JSON with expected paths
    - Verify `/swagger-ui.html` returns 200 when enabled
    - _Requirements: 6.2, 6.3_

- [x] 9. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The Actuator dependency and `spring.profiles.default=dev` already exist in the project — tasks update rather than create from scratch
- Profile property files (application-dev/staging/prod.properties) already exist — tasks update their content
- `@EnableScheduling` must be added to enable the stale session cleanup cron

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "4.1", "4.2", "4.3", "4.4"] },
    { "id": 1, "tasks": ["1.2", "1.3", "1.4", "2.2", "2.3", "5.1", "6.1", "8.1"] },
    { "id": 2, "tasks": ["1.5", "1.6", "2.4", "2.5", "5.2", "5.3", "6.2", "6.4", "8.2", "8.3", "8.4"] },
    { "id": 3, "tasks": ["5.4", "5.5", "6.3"] },
    { "id": 4, "tasks": ["6.5", "8.5", "8.6"] }
  ]
}
```
