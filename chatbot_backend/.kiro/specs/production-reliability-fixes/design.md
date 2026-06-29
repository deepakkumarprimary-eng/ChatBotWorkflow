# Production Reliability Fixes — Bugfix Design

## Overview

This design addresses three production-reliability bugs that affect the chatbot backend under real-world deployment conditions:

1. **Data Race on Concurrent Messages** — `ChatSession` lacks `@Version` optimistic locking, allowing lost updates when concurrent `chat.message` frames mutate the same row.
2. **Missing Reverse Proxy Configuration** — No `server.forward-headers-strategy=native` property, breaking client IP resolution and HTTPS detection behind load balancers.
3. **Cache Bypass in ChildWorkflowService and NavigationService** — Both services inject `WorkflowRepository` directly instead of using `WorkflowCacheService`, issuing unnecessary PostgreSQL queries on every child workflow entry/exit and back/restart navigation.

The fix strategy is minimal and targeted: add a version column, add one configuration property, and swap repository calls for cache-service calls.

## Glossary

- **Bug_Condition (C)**: The set of conditions that trigger one of the three bugs — concurrent session writes, deployment behind a reverse proxy, or workflow lookups in ChildWorkflowService/NavigationService
- **Property (P)**: The desired correct behavior — conflict detection, correct IP/protocol resolution, and Redis-cached workflow lookups
- **Preservation**: Existing behaviors that must remain unchanged — single-writer session saves, direct-connection IP resolution, existing cache usage in WorkflowExecutionServiceImpl, cache eviction on REST updates, Redis fallback
- **ChatSession**: JPA entity mapped to `chat_session` table, holds session state including JSONB context
- **WorkflowCacheService**: Service that wraps `WorkflowRepository` with a Redis cache layer (check Redis → miss → load PostgreSQL → populate cache)
- **WorkflowRepository**: Spring Data JPA repository for the `workflow` table
- **Optimistic Locking**: Hibernate mechanism using a `@Version` column to detect concurrent modifications — stale writes throw `ObjectOptimisticLockingFailureException`
- **Forward Headers Strategy**: Spring Boot configuration that tells the embedded server to trust `X-Forwarded-*` headers from reverse proxies

## Bug Details

### Bug Condition

The bugs manifest under three independent conditions:

1. **Data Race** — Two concurrent `chat.message` WebSocket frames arrive for the same session, both read the same `ChatSession` row version, apply independent context mutations, and the last writer silently overwrites the first writer's changes.

2. **Reverse Proxy Misconfiguration** — The application is deployed behind a TLS-terminating reverse proxy (Nginx, K8s Ingress, ALB) but does not configure Spring Boot to trust forwarded headers. `request.getRemoteAddr()` returns the proxy IP, and `request.isSecure()` returns `false`.

3. **Cache Bypass** — `ChildWorkflowService` and `NavigationService` call `workflowRepository.findById()` directly, bypassing the Redis caching layer in `WorkflowCacheService`. Every child workflow entry/exit and every back/restart triggers a PostgreSQL query.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type SystemEvent
  OUTPUT: boolean
  
  // Bug 1: Data race
  IF input.type == "chat.message"
     AND existsConcurrentWriteForSameSession(input.sessionId)
  THEN RETURN true

  // Bug 2: Reverse proxy
  IF input.type == "httpRequest"
     AND input.isFromReverseProxy == true
     AND serverForwardHeadersStrategy != "native"
  THEN RETURN true

  // Bug 3: Cache bypass
  IF input.type == "workflowLookup"
     AND input.callerService IN ["ChildWorkflowService", "NavigationService"]
     AND input.targetMethod == "workflowRepository.findById"
  THEN RETURN true

  RETURN false
END FUNCTION
```

### Examples

- **Data Race**: User sends "yes" and "no" simultaneously (network retry or duplicate frame). Both transactions read `version=1`. First commits context `{answer: "yes"}`, second commits `{answer: "no"}` overwriting the first — lost update.
- **Reverse Proxy IP**: Client `192.168.1.50` connects through Nginx at `10.0.0.1`. App logs `remoteAddr=10.0.0.1` instead of `192.168.1.50`. Rate limiting and audit trails reference the wrong IP.
- **Reverse Proxy HTTPS**: Client connects via `https://chat.example.com`. Proxy terminates TLS and forwards plain HTTP. `request.isSecure()` returns `false`, secure cookies are not set.
- **Cache Bypass (enterChild)**: User navigates to a child workflow. `ChildWorkflowService.enterChild()` calls `workflowRepository.findById(childId)` — hits PostgreSQL even though the workflow was recently loaded and is cached in Redis.
- **Cache Bypass (handleBack)**: User presses "Back". `NavigationService.handleBack()` calls `workflowRepository.findById(targetWorkflowId)` — another unnecessary PostgreSQL query.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Single-writer session updates (no concurrent conflict) must continue to save successfully without version errors
- Application running locally without a reverse proxy (dev profile) must continue to resolve the direct client IP correctly
- `WorkflowExecutionServiceImpl` must continue using `WorkflowCacheService.findById()` as before
- Workflow update/delete via REST API must continue to evict cached entries so subsequent reads reflect changes
- When Redis is unavailable, `WorkflowCacheService` must continue to fall back to PostgreSQL transparently

**Scope:**
All inputs that do NOT involve the three bug conditions should be completely unaffected by this fix. This includes:
- Normal single-writer chat message processing
- REST API CRUD operations on workflows and API configs
- WebSocket connection management, heartbeats, session cleanup
- Direct-connection deployments (no reverse proxy)

## Hypothesized Root Cause

Based on the bug description, the most likely issues are:

1. **Missing `@Version` annotation on ChatSession**: The entity has no version field. Hibernate therefore performs no optimistic lock check — `UPDATE` statements include no `WHERE version = ?` clause, and concurrent writes succeed silently.

2. **Missing `server.forward-headers-strategy=native` property**: Neither `application.properties` nor `application-prod.properties` configure this. Spring Boot's embedded Tomcat ignores `X-Forwarded-For` and `X-Forwarded-Proto` headers by default.

3. **Direct WorkflowRepository injection**: `ChildWorkflowService` and `NavigationService` were created after `WorkflowCacheService` existed but were wired to `WorkflowRepository` instead of `WorkflowCacheService`. The `findById` calls bypass the cache because the services simply don't reference the cache service.

## Correctness Properties

Property 1: Bug Condition — Optimistic Lock Conflict Detection

_For any_ pair of concurrent `chat.message` transactions targeting the same `ChatSession` row, the fixed system SHALL detect the conflict when the stale writer attempts to commit, throwing `ObjectOptimisticLockingFailureException` and preventing the lost update.

**Validates: Requirements 2.1**

Property 2: Bug Condition — Forward Headers Resolution

_For any_ HTTP request arriving through a reverse proxy that sets `X-Forwarded-For` and `X-Forwarded-Proto` headers, the fixed system SHALL resolve the real client IP from `X-Forwarded-For` and detect HTTPS from `X-Forwarded-Proto=https`.

**Validates: Requirements 2.2, 2.3**

Property 3: Bug Condition — Cached Workflow Lookup

_For any_ call to `ChildWorkflowService.enterChild()`, `ChildWorkflowService.handleChildEnd()`, `NavigationService.handleBack()`, or `NavigationService.handleRestart()` that requires a workflow lookup, the fixed system SHALL use `WorkflowCacheService.findById()` instead of `WorkflowRepository.findById()`, leveraging the Redis cache layer.

**Validates: Requirements 2.4, 2.5, 2.6, 2.7**

Property 4: Preservation — Single-Writer Session Saves

_For any_ single `chat.message` with no concurrent write conflict, the fixed system SHALL save the session context update successfully without triggering a version conflict, preserving normal chat flow.

**Validates: Requirements 3.1**

Property 5: Preservation — Direct Connection IP Resolution

_For any_ request arriving without `X-Forwarded-*` headers (direct connection, dev profile), the fixed system SHALL continue to resolve the actual TCP source IP correctly, preserving local development behavior.

**Validates: Requirements 3.2**

Property 6: Preservation — Existing Cache Behavior

_For any_ workflow lookup performed by `WorkflowExecutionServiceImpl` or REST API operations, the fixed system SHALL produce the same caching, eviction, and fallback behavior as the original code.

**Validates: Requirements 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `src/main/java/com/xpressbees/chatbot/entity/ChatSession.java`

**Change: Add @Version optimistic locking**

1. **Add version field**: Add a `@Version` annotated `Long version` field to `ChatSession`
2. **DDL update**: Add `ALTER TABLE chat_session ADD COLUMN version BIGINT NOT NULL DEFAULT 0;` to `schema.sql`

---

**File**: `src/main/resources/application-prod.properties`

**Change: Add forward headers strategy**

3. **Add property**: Add `server.forward-headers-strategy=native` to `application-prod.properties`

---

**File**: `src/main/java/com/xpressbees/chatbot/service/ChildWorkflowService.java`

**Change: Replace WorkflowRepository with WorkflowCacheService**

4. **Swap constructor dependency**: Replace `WorkflowRepository workflowRepository` with `WorkflowCacheService workflowCacheService`
5. **Update all `workflowRepository.findById()` calls**: Replace with `workflowCacheService.findById()`

---

**File**: `src/main/java/com/xpressbees/chatbot/service/NavigationService.java`

**Change: Replace WorkflowRepository with WorkflowCacheService**

6. **Swap constructor dependency**: Replace `WorkflowRepository workflowRepository` with `WorkflowCacheService workflowCacheService`
7. **Update all `workflowRepository.findById()` calls**: Replace with `workflowCacheService.findById()`

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bugs on unfixed code, then verify the fixes work correctly and preserve existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bugs BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write tests that simulate concurrent writes, proxy header scenarios, and trace workflow lookup paths. Run these tests on the UNFIXED code to observe failures and understand the root cause.

**Test Cases**:
1. **Concurrent Session Write Test**: Simulate two threads reading the same `ChatSession`, applying different context mutations, and saving — observe that both succeed without error (will pass on unfixed code, demonstrating the bug)
2. **Forward Headers Test**: Send a request with `X-Forwarded-For: 1.2.3.4` and assert `request.getRemoteAddr()` returns `1.2.3.4` (will fail on unfixed code)
3. **Cache Bypass Trace Test**: Instrument or mock `WorkflowRepository.findById()` in `ChildWorkflowService.enterChild()` and verify it is called directly instead of via cache (will demonstrate bypass on unfixed code)
4. **Cache Bypass Navigation Test**: Same for `NavigationService.handleBack()` (will demonstrate bypass on unfixed code)

**Expected Counterexamples**:
- Concurrent write test shows both transactions succeed, proving no conflict detection
- Forward headers test shows proxy IP returned instead of client IP
- Cache bypass tests show direct PostgreSQL hits via repository, confirming cache is not used

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := fixedSystem(input)
  ASSERT expectedBehavior(result)
END FOR
```

Specifically:
- For concurrent writes: assert `ObjectOptimisticLockingFailureException` is thrown for the stale writer
- For proxy requests: assert real client IP and HTTPS are correctly resolved
- For workflow lookups in ChildWorkflowService/NavigationService: assert `WorkflowCacheService.findById()` is called (not `WorkflowRepository.findById()`)

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalSystem(input) = fixedSystem(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for single-writer saves, direct-connection requests, and existing cache operations, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Single-Writer Save Preservation**: Verify that a single concurrent-free `chat.message` saves context updates successfully with no version conflict after `@Version` is added
2. **Direct Connection IP Preservation**: Verify that requests without `X-Forwarded-*` headers continue to resolve the TCP source IP correctly
3. **Existing Cache Usage Preservation**: Verify that `WorkflowExecutionServiceImpl` continues to use `WorkflowCacheService.findById()` after the other services are also wired to it
4. **Cache Eviction Preservation**: Verify that workflow REST updates still evict cached entries
5. **Redis Fallback Preservation**: Verify that when Redis is unavailable, `WorkflowCacheService` falls back to PostgreSQL

### Unit Tests

- Test `ChatSession` save with `@Version` — single writer succeeds, concurrent stale writer throws
- Test forward headers property loading in prod profile
- Test `ChildWorkflowService.enterChild()` calls `WorkflowCacheService.findById()` (mock verification)
- Test `ChildWorkflowService.handleChildEnd()` calls `WorkflowCacheService.findById()` (mock verification)
- Test `NavigationService.handleBack()` calls `WorkflowCacheService.findById()` (mock verification)
- Test `NavigationService.handleRestart()` calls `WorkflowCacheService.findById()` (mock verification)

### Property-Based Tests

- Generate random `ChatSession` context maps and verify single-writer saves always succeed with version increment (jqwik)
- Generate random workflow ID sequences and verify `ChildWorkflowService` always routes through cache service
- Generate random navigation history states and verify `NavigationService.handleBack()` always routes through cache service
- Generate random session states with `_rootWorkflowId` and verify `NavigationService.handleRestart()` always routes through cache service

### Integration Tests

- Full WebSocket flow: send concurrent `chat.message` frames and verify one is rejected with a retriable error
- Deploy with `application-prod.properties` active and verify `X-Forwarded-For` is honored
- End-to-end child workflow navigation confirming Redis cache is populated after first lookup
- End-to-end back/restart navigation confirming Redis cache hit on subsequent lookups
