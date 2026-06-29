# Implementation Plan: Testing Coverage

## Overview

This plan implements comprehensive automated test coverage for the Chatbot Workflow Engine across six testing dimensions: database integration tests (TestContainers), REST controller tests (@WebMvcTest), WebSocket STOMP integration tests, ApiConfigServiceImpl validation unit tests, concurrent message handling tests, and extended property-based tests. Tasks are ordered to build infrastructure first, then layer test classes incrementally.

## Tasks

- [x] 1. Set up test infrastructure and dependencies
  - [x] 1.1 Add TestContainers and embedded Redis dependencies to pom.xml
    - Add `testcontainers-bom` (version 1.19.3) to `<dependencyManagement>`
    - Add `testcontainers`, `postgresql`, and `junit-jupiter` TestContainers dependencies with `<scope>test</scope>`
    - Add `embedded-redis` (com.github.codemonstur:embedded-redis:1.4.3) with `<scope>test</scope>`
    - Verify compile succeeds with `mvn compile`
    - _Requirements: 1.1_

  - [x] 1.2 Create BaseIntegrationTest abstract class
    - Create `src/test/java/com/xpressbees/chatbot/integration/BaseIntegrationTest.java`
    - Annotate with `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` and `@Testcontainers`
    - Define static `PostgreSQLContainer` with `postgres:15-alpine`, database `chatbot_db_test`, `withInitScript("schema.sql")`
    - Implement `@DynamicPropertySource` to wire container JDBC URL, username, password, and set `ddl-auto=none`
    - Add Redis mock or embedded Redis configuration to avoid connection failures
    - _Requirements: 1.1_

  - [x] 1.3 Create test fixture JSON files
    - Create `src/test/resources/fixtures/simple-workflow.json` — minimal 2-node workflow (message → input)
    - Create `src/test/resources/fixtures/complete-workflow.json` — full workflow with message, input, and api nodes
    - Create `src/test/resources/fixtures/api-config-fixture.json` — ApiConfig with headers, payload, and response mappings
    - _Requirements: 1.2, 1.5, 4.2_

- [x] 2. Implement database integration tests
  - [x] 2.1 Create WorkflowRepositoryIntegrationTest
    - Create `src/test/java/com/xpressbees/chatbot/integration/WorkflowRepositoryIntegrationTest.java` extending `BaseIntegrationTest`
    - Test that saving a workflow with JSONB workflow_json persists and retrieves the complete node/transition structure
    - Use AssertJ for fluent assertions comparing the serialized and deserialized JSONB content
    - _Requirements: 1.2_

  - [x] 2.2 Create ChatSessionRepositoryIntegrationTest
    - Create `src/test/java/com/xpressbees/chatbot/integration/ChatSessionRepositoryIntegrationTest.java` extending `BaseIntegrationTest`
    - Test that saving a ChatSession with context data (JSONB column) round-trips all fields correctly
    - Verify session status field persistence
    - _Requirements: 1.3_

  - [x] 2.3 Create ApiConfigCascadeIntegrationTest
    - Create `src/test/java/com/xpressbees/chatbot/integration/ApiConfigCascadeIntegrationTest.java` extending `BaseIntegrationTest`
    - Test that creating an ApiConfig with headers, payload, and response mappings persists all child entities via cascade
    - Test that deleting an ApiConfig removes all associated child records via cascade delete
    - _Requirements: 1.5, 1.6_

  - [x] 2.4 Create WorkflowExecutionIntegrationTest
    - Create `src/test/java/com/xpressbees/chatbot/integration/WorkflowExecutionIntegrationTest.java` extending `BaseIntegrationTest`
    - Test full workflow execution lifecycle: start session → process nodes → complete
    - Verify session status transitions from "active" to "completed" in the database
    - _Requirements: 1.4_

  - [ ]* 2.5 Write property test for JSONB round-trip preservation
    - **Property 1: JSONB Column Round-Trip Preservation**
    - Create property test verifying that any valid workflow JSON structure persisted via JPA produces identical content on retrieval
    - Use jqwik to generate varied JSONB structures (nodes, transitions, nested objects)
    - **Validates: Requirements 1.2, 1.3**

- [x] 3. Implement REST controller tests for WorkflowController
  - [x] 3.1 Create WorkflowControllerTest with @WebMvcTest
    - Create `src/test/java/com/xpressbees/chatbot/controller/WorkflowControllerTest.java`
    - Annotate with `@WebMvcTest(WorkflowController.class)`
    - Mock `WorkflowService` and `WorkflowCacheService` with `@MockBean`
    - Test POST /api/workflows returns 201 Created with persisted workflow data
    - Test GET /api/workflows/{id} returns 200 OK with correct workflow JSON
    - Test GET /api/workflows/{id} with non-existent ID returns 404 Not Found
    - Test PUT /api/workflows/{id} returns 200 OK with updated data
    - Test DELETE /api/workflows/{id} returns 204 No Content
    - Test GET /api/workflows returns 200 OK with JSON array of all workflows
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

- [x] 4. Implement REST controller tests for ApiConfigController
  - [x] 4.1 Create ApiConfigControllerTest with @WebMvcTest
    - Create `src/test/java/com/xpressbees/chatbot/controller/ApiConfigControllerTest.java`
    - Annotate with `@WebMvcTest(ApiConfigController.class)`
    - Mock `ApiConfigService` with `@MockBean`
    - Test POST /api/api-configs with valid request returns 201 Created
    - Test POST with missing required field (name, url, method) returns 400 Bad Request
    - Test POST with duplicate name returns 409 Conflict
    - Test POST with invalid HTTP method returns 400 Bad Request
    - Test GET /api/api-configs/{id} with non-numeric ID returns 400 Bad Request
    - Test GET /api/api-configs/{id} with non-existent numeric ID returns 404 Not Found
    - Test DELETE /api/api-configs/{id} with valid ID returns 204 No Content
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement WebSocket STOMP integration tests
  - [x] 6.1 Create WebSocketStompIntegrationTest
    - Create `src/test/java/com/xpressbees/chatbot/integration/WebSocketStompIntegrationTest.java` extending `BaseIntegrationTest`
    - Set up `WebSocketStompClient` with `StandardWebSocketClient` and `MappingJackson2MessageConverter`
    - Test subscribing to /app/chat.init returns non-null sessionId and workflows array
    - Test sending chat.start with valid sessionId/workflowId receives first node content on /topic/chat/{sessionId}
    - Test sending chat.message with valid sessionId during active session advances workflow
    - Test sending chat.message with invalid sessionId is rejected
    - Test sending chat.start with invalid sessionId receives ChatErrorResponse
    - Test workflow completion sets completion flag to true in final response
    - Test error during workflow processing pushes ChatErrorResponse to session topic
    - Use `CompletableFuture` with timeouts for async response collection
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

- [x] 7. Implement ApiConfigServiceImpl validation unit tests
  - [x] 7.1 Create ApiConfigValidationTest
    - Create `src/test/java/com/xpressbees/chatbot/service/ApiConfigValidationTest.java`
    - Test method normalization: "post" → "POST", "get" → "GET" (case insensitive)
    - Test invalid method throws InvalidMethodException (e.g., "PATCH", "OPTIONS", "FOO")
    - Test timeoutMs < 1 throws IllegalArgumentException
    - Test timeoutMs > 300000 throws IllegalArgumentException
    - Test retryCount < 0 throws IllegalArgumentException
    - Test retryCount > 10 throws IllegalArgumentException
    - Test headers list > 50 entries throws IllegalArgumentException
    - Test response mappings > 50 entries throws IllegalArgumentException
    - Test invalid context_variable_name pattern throws IllegalArgumentException
    - Test duplicate context_variable_name throws IllegalArgumentException
    - Test context_variable_name > 255 chars throws IllegalArgumentException
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9_

  - [ ]* 7.2 Write property test for method normalization and rejection
    - **Property 2: Method Normalization and Invalid Method Rejection**
    - Use jqwik to generate arbitrary strings; verify GET/POST/PUT/DELETE (any case) are normalized and accepted, all others are rejected
    - **Validates: Requirements 5.1, 5.2**

  - [ ]* 7.3 Write property test for numeric range validation
    - **Property 3: Numeric Range Validation**
    - Use jqwik to generate integers; verify timeoutMs in [1, 300000] and retryCount in [0, 10] are accepted, others rejected
    - **Validates: Requirements 5.3, 5.4**

  - [ ]* 7.4 Write property test for collection size validation
    - **Property 4: Collection Size Validation**
    - Use jqwik to generate lists of varying sizes; verify lists ≤ 50 are accepted, > 50 are rejected
    - **Validates: Requirements 5.5, 5.6**

  - [ ]* 7.5 Write property test for variable name validation
    - **Property 5: Response Mapping Variable Name Validation**
    - Use jqwik to generate strings; verify names matching `^[a-zA-Z_][a-zA-Z0-9_]*$`, ≤ 255 chars, and unique are accepted; all others rejected
    - **Validates: Requirements 5.7, 5.8, 5.9**

- [x] 8. Implement concurrent message handling tests
  - [x] 8.1 Create ConcurrentMessageHandlingTest
    - Create `src/test/java/com/xpressbees/chatbot/concurrent/ConcurrentMessageHandlingTest.java`
    - Use `ExecutorService` + `CountDownLatch` pattern for controlled parallel execution
    - Test multiple concurrent chat.message requests for same sessionId don't corrupt session context
    - Test chat.start and chat.message arriving nearly simultaneously: start is processed before message
    - _Requirements: 6.1, 6.3_

  - [x] 8.2 Create PendingSessionConcurrencyTest
    - Create `src/test/java/com/xpressbees/chatbot/concurrent/PendingSessionConcurrencyTest.java`
    - Test multiple concurrent chat.init requests store all generated sessionIds without overwriting
    - Test concurrent `consumePendingSession` calls: exactly one returns true, all others return false
    - Use `CountDownLatch` for deterministic thread coordination
    - _Requirements: 6.2, 6.4_

  - [ ]* 8.3 Write property test for concurrent session registration isolation
    - **Property 6: Concurrent Session Registration Isolation**
    - Use jqwik to generate sets of N distinct session IDs; verify all are stored concurrently without loss
    - **Validates: Requirements 6.2**

  - [ ]* 8.4 Write property test for exactly-once consume semantics
    - **Property 7: Exactly-Once Consume Semantics**
    - Use jqwik to generate session IDs and thread counts; verify exactly one consume returns true
    - **Validates: Requirements 6.4**

- [x] 9. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement extended property-based tests for PlaceholderService
  - [x] 10.1 Create PlaceholderServiceExtendedPropertyTest
    - Create `src/test/java/com/xpressbees/chatbot/service/PlaceholderServiceExtendedPropertyTest.java`
    - Use jqwik with `@Property(tries = 100)` and `@Tag("Feature: testing-coverage, Property {N}: {title}")`
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [ ]* 10.2 Write property test for placeholder resolution termination
    - **Property 8: Placeholder Resolution Termination**
    - Generate context maps with nested/self-referencing placeholders; verify resolution terminates without infinite recursion
    - **Validates: Requirements 7.1, 7.3**

  - [ ]* 10.3 Write property test for no-placeholder string preservation
    - **Property 9: No-Placeholder String Preservation (Idempotence)**
    - Generate strings without `{{` and `}}` pairs; verify PlaceholderService returns input byte-for-byte unchanged
    - **Validates: Requirements 7.2**

  - [ ]* 10.4 Write property test for complete resolution
    - **Property 10: Complete Resolution When All Keys Present**
    - Generate template strings and context maps where all keys exist; verify no unresolved `{{...}}` patterns remain
    - **Validates: Requirements 7.4**

- [x] 11. Implement extended property-based tests for ConditionEvaluator
  - [x] 11.1 Create ConditionEvaluatorExtendedPropertyTest
    - Create `src/test/java/com/xpressbees/chatbot/service/ConditionEvaluatorExtendedPropertyTest.java`
    - Use jqwik with `@Property(tries = 100)` and appropriate tags
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [ ]* 11.2 Write property test for single comparison expression correctness
    - **Property 11: Single Comparison Expression Correctness**
    - Generate variables, values, and operators; verify results match Java comparison semantics
    - **Validates: Requirements 8.1**

  - [ ]* 11.3 Write property test for conjunction semantics
    - **Property 12: Conjunction Semantics (AND)**
    - Generate compound conditions joined by "and"; verify result is true only when all sub-conditions are true
    - **Validates: Requirements 8.2**

  - [ ]* 11.4 Write property test for disjunction semantics
    - **Property 13: Disjunction Semantics (OR)**
    - Generate compound conditions joined by "or"; verify result is true when at least one sub-condition is true
    - **Validates: Requirements 8.3**

  - [ ]* 11.5 Write property test for missing variable evaluation
    - **Property 14: Missing Variable Evaluates to False**
    - Generate conditions referencing absent variables; verify evaluation returns false without exception
    - **Validates: Requirements 8.4**

- [x] 12. Implement extended property-based tests for UrlValidator
  - [x] 12.1 Create UrlValidatorExtendedPropertyTest
    - Create `src/test/java/com/xpressbees/chatbot/service/UrlValidatorExtendedPropertyTest.java`
    - Use jqwik with `@Property(tries = 100)` and appropriate tags
    - _Requirements: 9.1, 9.2, 9.3_

  - [ ]* 12.2 Write property test for private IP URL rejection
    - **Property 15: Private IP URL Rejection via Full Validation**
    - Generate URLs with private/internal IP hosts (10.x, 172.16-31.x, 192.168.x, 127.x); verify blocked result
    - **Validates: Requirements 9.1**

  - [ ]* 12.3 Write property test for HTTP/HTTPS scheme-agnostic acceptance
    - **Property 16: HTTP/HTTPS Scheme-Agnostic Acceptance**
    - Generate URLs with valid public hosts; verify both http and https schemes produce the same acceptance result
    - **Validates: Requirements 9.2**

  - [ ]* 12.4 Write property test for non-HTTP scheme rejection
    - **Property 17: Non-HTTP Scheme Rejection**
    - Generate URLs with schemes like file://, ftp://, gopher://, javascript:; verify blocked result
    - **Validates: Requirements 9.3**

- [x] 13. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (Properties 1-17)
- Unit tests validate specific examples and edge cases
- TestContainers requires Docker to be running for integration tests
- The `BaseIntegrationTest` class is shared across all integration tests to reuse a single PostgreSQL container
- jqwik 1.8.2 is already present in pom.xml; extended property tests follow existing conventions in the project

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["2.1", "2.2", "2.3", "3.1", "4.1", "7.1"] },
    { "id": 3, "tasks": ["2.4", "2.5", "7.2", "7.3", "7.4", "7.5"] },
    { "id": 4, "tasks": ["6.1", "8.1", "8.2"] },
    { "id": 5, "tasks": ["8.3", "8.4", "10.1"] },
    { "id": 6, "tasks": ["10.2", "10.3", "10.4", "11.1"] },
    { "id": 7, "tasks": ["11.2", "11.3", "11.4", "11.5", "12.1"] },
    { "id": 8, "tasks": ["12.2", "12.3", "12.4"] }
  ]
}
```
