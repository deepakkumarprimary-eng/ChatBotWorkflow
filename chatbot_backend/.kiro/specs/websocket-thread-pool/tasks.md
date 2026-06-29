# Implementation Plan: WebSocket Thread Pool

## Overview

Add dedicated, bounded `ThreadPoolTaskExecutor` instances to the WebSocket inbound and outbound channels. This involves extending `WebSocketResilienceProperties` with pool sizing fields, modifying `WebSocketConfig` to wire executors into channel registrations, creating a `LoggingCallerRunsPolicy` for saturation visibility, and adding environment-specific property values. Property-based tests (jqwik) validate configuration propagation, validation rules, thread naming, and task loss prevention.

## Tasks

- [x] 1. Extend WebSocketResilienceProperties with pool sizing fields
  - [x] 1.1 Add inbound and outbound pool fields with validation annotations
    - Add `inboundPoolCoreSize` (default 10), `inboundPoolMaxSize` (default 50), `inboundPoolQueueCapacity` (default 200) with `@Min(1)` on each
    - Add `outboundPoolCoreSize` (default 10), `outboundPoolMaxSize` (default 50), `outboundPoolQueueCapacity` (default 200) with `@Min(1)` on each
    - Add `@AssertTrue` method `isInboundPoolSizesValid()` that returns `inboundPoolMaxSize >= inboundPoolCoreSize`
    - Add `@AssertTrue` method `isOutboundPoolSizesValid()` that returns `outboundPoolMaxSize >= outboundPoolCoreSize`
    - Add Lombok getters/setters for new fields
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 4.6_

  - [x]* 1.2 Write property test for invalid pool size rejection (Property 3)
    - **Property 3: Invalid Pool Size Rejection**
    - Generate random integers ≤ 0 and verify Jakarta Bean Validation produces constraint violations for each pool sizing field
    - Use `jakarta.validation.Validator` to validate a `WebSocketResilienceProperties` instance
    - Test class: `WebSocketThreadPoolValidationPropertyTest`
    - **Validates: Requirements 2.4, 2.5, 2.6, 4.6**

  - [x]* 1.3 Write property test for cross-field max ≥ core validation (Property 4)
    - **Property 4: Cross-Field Max ≥ Core Validation**
    - Generate random pairs (coreSize, maxSize) both ≥ 1; verify validation passes when maxSize ≥ coreSize and fails when maxSize < coreSize
    - Test class: `WebSocketThreadPoolValidationPropertyTest`
    - **Validates: Requirements 2.7**

- [x] 2. Create LoggingCallerRunsPolicy class
  - [x] 2.1 Implement LoggingCallerRunsPolicy extending CallerRunsPolicy
    - Create `com.xpressbees.chatbot.config.LoggingCallerRunsPolicy`
    - Override `rejectedExecution(Runnable, ThreadPoolExecutor)` to log at WARN level with active count and queue size before calling `super.rejectedExecution()`
    - Use SLF4J logger
    - _Requirements: 5.1, 5.2, 5.4_

  - [x]* 2.2 Write unit test for LoggingCallerRunsPolicy WARN log emission
    - Use Logback `ListAppender` to capture WARN log when `rejectedExecution` is called
    - Verify log message contains active thread count and queue size
    - Test class: `LoggingCallerRunsPolicyTest`
    - _Requirements: 5.2_

- [x] 3. Configure thread pool executors in WebSocketConfig
  - [x] 3.1 Add inbound and outbound ThreadPoolTaskExecutor bean methods
    - Create `@Bean` method `inboundExecutor()` that builds a `ThreadPoolTaskExecutor` from `WebSocketResilienceProperties` inbound fields
    - Set thread name prefix to `"ws-inbound-"`, set `CallerRunsPolicy` via `LoggingCallerRunsPolicy`, enable `waitForTasksToCompleteOnShutdown(true)`, set `awaitTerminationSeconds(30)`
    - Create `@Bean` method `outboundExecutor()` similarly with prefix `"ws-outbound-"` using outbound fields
    - Log INFO at startup with pool configuration for both executors
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.7, 4.1, 4.2, 4.3, 4.4, 4.5, 5.3_

  - [x] 3.2 Wire executors into channel registrations
    - Override `configureClientInboundChannel(ChannelRegistration)` to call `registration.taskExecutor(inboundExecutor())` in addition to existing interceptor registration
    - Ensure interceptor order is preserved: `WebSocketAuthInterceptor` before `ConnectionLimitInterceptor`
    - Override `configureClientOutboundChannel(ChannelRegistration)` to call `registration.taskExecutor(outboundExecutor())`
    - _Requirements: 1.1, 1.6, 3.1, 3.2, 3.3, 4.1_

  - [x]* 3.3 Write property test for configuration propagation (Property 1)
    - **Property 1: Configuration Propagation**
    - Generate random valid (core ≥ 1, max ≥ core, queue ≥ 1) tuples, build `ThreadPoolTaskExecutor`, and verify executor's corePoolSize, maxPoolSize, and queueCapacity match input values
    - Test class: `WebSocketThreadPoolPropertyTest`
    - **Validates: Requirements 1.2, 1.3, 1.4, 4.2, 4.3, 4.4**

  - [x]* 3.4 Write property test for thread naming prefix (Property 2)
    - **Property 2: Thread Naming Prefix**
    - Submit tasks to inbound/outbound executors and verify executing thread names start with `"ws-inbound-"` / `"ws-outbound-"` respectively
    - Test class: `WebSocketThreadPoolPropertyTest`
    - **Validates: Requirements 1.5, 4.5**

- [x] 4. Checkpoint - Verify core implementation compiles and tests pass
  - All tests pass (16/16, 0 failures).

- [x] 5. Add application properties for all environments
  - [x] 5.1 Add thread pool properties to application property files
    - Add `chatbot.websocket.inbound-pool-core-size`, `chatbot.websocket.inbound-pool-max-size`, `chatbot.websocket.inbound-pool-queue-capacity` to `application-dev.properties`, `application-staging.properties`, `application-prod.properties`
    - Add `chatbot.websocket.outbound-pool-core-size`, `chatbot.websocket.outbound-pool-max-size`, `chatbot.websocket.outbound-pool-queue-capacity` similarly
    - Dev: core=4, max=10, queue=50; Staging: core=8, max=30, queue=150; Prod: core=10, max=50, queue=200
    - _Requirements: 2.1, 2.2, 2.3, 4.6_

  - [x] 5.2 Add test properties for integration tests
    - Add pool properties to `application-test.properties` with small values: core=2, max=4, queue=5 for both inbound and outbound
    - _Requirements: 2.1, 2.2, 2.3_

- [x] 6. Write integration and saturation tests
  - [x]* 6.1 Write integration test for full context load with custom properties
    - Verify `WebSocketResilienceProperties` binds values from `application-test.properties`
    - Verify inbound and outbound executor beans are created with correct configuration
    - Test class: `WebSocketThreadPoolIntegrationTest`
    - _Requirements: 1.1, 2.1, 2.2, 2.3, 4.1_

  - [x]* 6.2 Write property test for no task loss under saturation (Property 5)
    - **Property 5: No Task Loss Under Saturation**
    - Generate random pool configs (small core/max/queue) and submit more tasks than queue + max; verify all tasks complete (completedTaskCount == submitted count)
    - Use `CountDownLatch` to synchronize task completion counting
    - Test class: `WebSocketThreadPoolSaturationPropertyTest`
    - **Validates: Requirements 5.1, 5.4**

  - [x]* 6.3 Write unit test for interceptor ordering preserved
    - Verify `configureClientInboundChannel` registers `WebSocketAuthInterceptor` before `ConnectionLimitInterceptor`
    - Verify interceptors execute before pool dispatch (mock channel registration)
    - Test class: `WebSocketConfigTest`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 7. Final checkpoint - Ensure all tests pass
  - All 16 tests pass (BUILD SUCCESS).

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (jqwik 1.8.2)
- Unit tests validate specific examples and edge cases
- Small pool sizes in test configuration make saturation scenarios easy to reproduce
- Existing interceptors (`WebSocketAuthInterceptor`, `ConnectionLimitInterceptor`) require no code changes — Spring's channel registration guarantees `preSend` executes before pool dispatch

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "2.2", "3.1"] },
    { "id": 2, "tasks": ["3.2", "3.3", "3.4"] },
    { "id": 3, "tasks": ["5.1", "5.2"] },
    { "id": 4, "tasks": ["6.1", "6.2", "6.3"] }
  ]
}
```
