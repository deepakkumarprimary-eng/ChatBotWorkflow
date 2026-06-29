# Implementation Plan

## Overview

Fix three production-reliability bugs: data race on concurrent messages (missing `@Version` optimistic locking on ChatSession), missing reverse proxy configuration (`server.forward-headers-strategy=native`), and cache bypass in ChildWorkflowService and NavigationService (direct WorkflowRepository calls instead of WorkflowCacheService).

## Tasks

- [ ] 1. Write bug condition exploration tests
  - **Property 1: Bug Condition** - Production Reliability Bugs (Data Race, Proxy Misconfiguration, Cache Bypass)
  - **CRITICAL**: These tests MUST FAIL on unfixed code - failure confirms the bugs exist
  - **DO NOT attempt to fix the tests or the code when they fail**
  - **NOTE**: These tests encode the expected behavior - they will validate the fixes when they pass after implementation
  - **GOAL**: Surface counterexamples that demonstrate the three bugs exist
  - **Scoped PBT Approach**:
    - Bug 1 (Data Race): Two concurrent threads read same `ChatSession` row, apply independent context mutations, save — assert that `ObjectOptimisticLockingFailureException` is thrown for the stale writer (will FAIL on unfixed code because no `@Version` exists)
    - Bug 2 (Reverse Proxy): Send request with `X-Forwarded-For: 1.2.3.4` and `X-Forwarded-Proto: https` headers — assert `request.getRemoteAddr()` returns `1.2.3.4` and `request.isSecure()` returns `true` (will FAIL on unfixed code because `server.forward-headers-strategy` is not set)
    - Bug 3 (Cache Bypass): Mock `WorkflowCacheService` and verify `ChildWorkflowService.enterChild()`, `ChildWorkflowService.handleChildEnd()`, `NavigationService.handleBack()`, `NavigationService.handleRestart()` call `workflowCacheService.findById()` instead of `workflowRepository.findById()` (will FAIL on unfixed code because services inject repository directly)
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct - it proves the bugs exist)
  - Document counterexamples found:
    - Concurrent write test: both transactions succeed without error (no conflict detection)
    - Forward headers test: proxy IP returned instead of client IP, `isSecure()` returns `false`
    - Cache bypass tests: `workflowRepository.findById()` called directly, cache service never invoked
  - Mark task complete when tests are written, run, and failures are documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_

- [ ] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Single-Writer Saves, Direct Connection IP, Existing Cache Behavior
  - **IMPORTANT**: Follow observation-first methodology
  - **Observe behavior on UNFIXED code for non-buggy inputs:**
    - Observe: Single `chat.message` with no concurrent writes saves context successfully
    - Observe: Request without `X-Forwarded-*` headers resolves direct TCP source IP
    - Observe: `WorkflowExecutionServiceImpl` calls `WorkflowCacheService.findById()` for workflow loading
    - Observe: Workflow REST update evicts cached entry so subsequent reads reflect changes
    - Observe: When Redis is unavailable, `WorkflowCacheService` falls back to PostgreSQL transparently
  - **Write property-based tests (jqwik) capturing observed behavior:**
    - Generate random `ChatSession` context maps and verify single-writer saves always succeed without version conflict errors
    - Generate random workflow IDs and verify `WorkflowExecutionServiceImpl` routes through `WorkflowCacheService`
    - Verify cache eviction on REST API workflow updates/deletes
    - Verify Redis fallback behavior when Redis is unavailable
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ] 3. Fix for production reliability bugs (Data Race, Reverse Proxy, Cache Bypass)

  - [ ] 3.1 Add `@Version` optimistic locking to ChatSession entity
    - Add `@Version` annotated `Long version` field to `ChatSession.java`
    - Add `ALTER TABLE chat_session ADD COLUMN version BIGINT NOT NULL DEFAULT 0;` to `schema.sql`
    - Hibernate will automatically include `WHERE version = ?` in UPDATE statements
    - _Bug_Condition: isBugCondition(input) where input.type == "chat.message" AND existsConcurrentWriteForSameSession(input.sessionId)_
    - _Expected_Behavior: ObjectOptimisticLockingFailureException thrown for stale writer, preventing lost updates_
    - _Preservation: Single-writer session saves must continue to succeed without version conflicts_
    - _Requirements: 2.1, 3.1_

  - [ ] 3.2 Add `server.forward-headers-strategy=native` to production properties
    - Add `server.forward-headers-strategy=native` to `application-prod.properties`
    - This tells Spring Boot's embedded Tomcat to trust `X-Forwarded-For` and `X-Forwarded-Proto` headers
    - Only applied in prod profile — dev profile remains unaffected
    - _Bug_Condition: isBugCondition(input) where input.type == "httpRequest" AND input.isFromReverseProxy == true AND serverForwardHeadersStrategy != "native"_
    - _Expected_Behavior: Real client IP resolved from X-Forwarded-For, HTTPS detected from X-Forwarded-Proto_
    - _Preservation: Direct-connection deployments (dev profile) continue to resolve actual TCP source IP_
    - _Requirements: 2.2, 2.3, 3.2_

  - [ ] 3.3 Replace `WorkflowRepository` with `WorkflowCacheService` in `ChildWorkflowService`
    - Remove `WorkflowRepository` constructor parameter from `ChildWorkflowService`
    - Add `WorkflowCacheService` constructor parameter instead
    - Replace all `workflowRepository.findById()` calls with `workflowCacheService.findById()` in `enterChild()` and `handleChildEnd()`
    - _Bug_Condition: isBugCondition(input) where input.callerService == "ChildWorkflowService" AND input.targetMethod == "workflowRepository.findById"_
    - _Expected_Behavior: WorkflowCacheService.findById() called, leveraging Redis cache layer_
    - _Preservation: Existing cache eviction on REST updates and Redis fallback behavior unchanged_
    - _Requirements: 2.4, 2.5, 3.3, 3.4, 3.5_

  - [ ] 3.4 Replace `WorkflowRepository` with `WorkflowCacheService` in `NavigationService`
    - Remove `WorkflowRepository` constructor parameter from `NavigationService`
    - Add `WorkflowCacheService` constructor parameter instead
    - Replace all `workflowRepository.findById()` calls with `workflowCacheService.findById()` in `handleBack()` and `handleRestart()`
    - _Bug_Condition: isBugCondition(input) where input.callerService == "NavigationService" AND input.targetMethod == "workflowRepository.findById"_
    - _Expected_Behavior: WorkflowCacheService.findById() called, leveraging Redis cache layer_
    - _Preservation: Existing cache eviction on REST updates and Redis fallback behavior unchanged_
    - _Requirements: 2.6, 2.7, 3.3, 3.4, 3.5_

  - [ ] 3.5 Verify bug condition exploration tests now pass
    - **Property 1: Expected Behavior** - Production Reliability Fixes Validated
    - **IMPORTANT**: Re-run the SAME tests from task 1 - do NOT write new tests
    - The tests from task 1 encode the expected behavior for all three bugs
    - When these tests pass, it confirms:
      - Concurrent write conflict is detected (ObjectOptimisticLockingFailureException thrown)
      - Forward headers resolve real client IP and HTTPS correctly
      - ChildWorkflowService and NavigationService route through WorkflowCacheService
    - Run bug condition exploration tests from step 1
    - **EXPECTED OUTCOME**: Tests PASS (confirms all three bugs are fixed)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

  - [ ] 3.6 Verify preservation tests still pass
    - **Property 2: Preservation** - No Regressions in Existing Behavior
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all tests still pass after fix:
      - Single-writer session saves succeed without version conflicts
      - Direct-connection IP resolution works correctly
      - WorkflowExecutionServiceImpl still uses WorkflowCacheService
      - Cache eviction on REST updates still works
      - Redis fallback still works when Redis is unavailable

- [ ] 4. Checkpoint - Ensure all tests pass
  - Run full test suite with `mvn test`
  - Ensure all property-based tests (jqwik) pass
  - Ensure all unit tests pass
  - Ensure no compilation errors
  - Ask the user if questions arise

## Task Dependency Graph

```json
{
  "waves": [
    ["1", "2"],
    ["3.1", "3.2", "3.3", "3.4"],
    ["3.5", "3.6"],
    ["4"]
  ]
}
```

## Notes

- Tests use jqwik 1.8.2 for property-based testing as per project conventions
- The exploration tests (task 1) are expected to FAIL before the fix and PASS after — this is intentional and confirms the bug condition methodology works
- The preservation tests (task 2) must PASS both before and after the fix — failure after fix indicates a regression
- All three bugs are independent and can be fixed in any order within task 3
- The `schema.sql` DDL change (version column) will apply on next application restart since `spring.jpa.hibernate.ddl-auto=none`
