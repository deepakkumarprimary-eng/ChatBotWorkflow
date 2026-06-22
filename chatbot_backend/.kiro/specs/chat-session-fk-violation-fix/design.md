# Chat Session FK Violation Bugfix Design

## Overview

The `ChatWebSocketHandler.onChatInit()` method eagerly persists a `ChatSession` entity with a dummy `workflowId = 0L` before the user has selected a workflow. The `chat_session.workflow_id` column has a `NOT NULL REFERENCES workflow(id)` constraint, so this insert always fails with a foreign key violation because no workflow row with `id = 0` exists.

The fix defers database persistence of the `ChatSession` until `chat.start` is called (when the real `workflowId` is known). During `chat.init`, only an in-memory session ID is generated and returned alongside the workflow list. A thread-safe `ConcurrentHashMap` holds pending session IDs between the two calls.

## Glossary

- **Bug_Condition (C)**: The condition that triggers the bug — `onChatInit()` attempts to persist a `ChatSession` with `workflowId = 0L`, violating the FK constraint
- **Property (P)**: The desired behavior — `onChatInit()` returns a valid `sessionId` + workflow list without any database write
- **Preservation**: Existing `chat.start`, `chat.message`, `chat.back`, and `chat.restart` flows must continue to function identically after the fix
- **onChatInit()**: The method in `ChatWebSocketHandler` annotated with `@SubscribeMapping("/chat.init")` that initializes a chat session
- **startWorkflow()**: The method in `WorkflowExecutionServiceImpl` that looks up a `ChatSession` by `sessionId`, assigns the real `workflowId`, and begins workflow execution
- **Pending Session Store**: A `ConcurrentHashMap<String, Instant>` holding session IDs generated at init time but not yet persisted to the database

## Bug Details

### Bug Condition

The bug manifests when any WebSocket client subscribes to `/app/chat.init`. The `onChatInit()` method creates a `ChatSession` entity with `workflowId = 0L` and calls `chatSessionRepository.save(session)`. Because the `chat_session` table defines `workflow_id BIGINT NOT NULL REFERENCES workflow(id)`, PostgreSQL rejects the insert since no row in `workflow` has `id = 0`.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type SubscribeEvent("/app/chat.init")
  OUTPUT: boolean
  
  RETURN input.destination == "/app/chat.init"
         AND chatSessionRepository.save() is called
         AND session.workflowId == 0L
         AND NOT EXISTS(SELECT 1 FROM workflow WHERE id = 0)
END FUNCTION
```

### Examples

- **Example 1**: Client subscribes to `/app/chat.init` → `onChatInit()` creates `ChatSession{workflowId=0}` → `save()` throws `PSQLException: Key (workflow_id)=(0) is not present in table "workflow"` → client receives no response (error propagates as exception)
- **Example 2**: Even if workflows exist in the DB (e.g., id=1, id=2), the init still fails because `workflowId=0` is always used regardless of available workflows
- **Example 3**: Multiple concurrent clients all subscribing to `/app/chat.init` simultaneously — all fail with the same FK violation
- **Edge Case**: If a workflow with `id = 0` happened to exist (auto-generated IDs start at 1 in `BIGSERIAL`), the bug would be masked but the session would reference the wrong workflow

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- `chat.start` with a valid `sessionId` and `workflowId` must continue to load the workflow, find the first node, and begin processing
- `chat.message` with a valid `sessionId` must continue to look up the persisted `ChatSession` and handle user input
- `chat.back` and `chat.restart` must continue to operate on persisted sessions
- The response format from `onChatInit()` (Map with `sessionId` and `workflows` keys) must remain identical
- Error handling for invalid `workflowId` in `startWorkflow()` must continue to send "Workflow not found" error

**Scope:**
All inputs that do NOT involve the `/app/chat.init` subscription path are completely unaffected by this fix. This includes:
- All `chat.start`, `chat.message`, `chat.back`, `chat.restart` message flows (these operate on already-persisted sessions)
- REST API endpoints (`/api/workflows`, `/api/api-configs`)
- WebSocket topic subscriptions (`/topic/chat/{sessionId}`)

## Hypothesized Root Cause

Based on the code analysis, the root cause is clear and singular:

1. **Premature Persistence with Invalid FK Value**: `ChatWebSocketHandler.onChatInit()` at line `chatSessionRepository.save(session)` persists a `ChatSession` with `workflowId = 0L`. The `chat_session.workflow_id` column has a FK constraint to `workflow(id)`. Since `BIGSERIAL` auto-increment starts at 1, no row with `id = 0` ever exists.

2. **Tight Coupling Between Init and Start**: The original design assumed `ChatSession` must exist in DB before `startWorkflow()` is called, because `startWorkflow()` does `chatSessionRepository.findBySessionId(sessionId)` and rejects if not found. This coupling forced the premature persist at init time.

3. **No Deferred-Persistence Pattern**: There is no mechanism (in-memory store, nullable FK, or two-phase create) to hold session state between `chat.init` and `chat.start`.

## Correctness Properties

Property 1: Bug Condition - No Database Write During Chat Init

_For any_ subscription to `/app/chat.init`, the fixed `onChatInit()` method SHALL return a valid response containing a unique `sessionId` and the workflow list WITHOUT calling `chatSessionRepository.save()` or performing any database write operation.

**Validates: Requirements 2.1**

Property 2: Deferred Persistence - Session Created at Start Time

_For any_ `chat.start` message with a valid `sessionId` (one generated during a prior `chat.init`) and a valid `workflowId` (referencing an existing workflow row), the fixed `startWorkflow()` method SHALL create and persist a new `ChatSession` entity with the provided `workflowId` and proceed with workflow execution.

**Validates: Requirements 2.2**

Property 3: Preservation - Existing Flows Unchanged

_For any_ input that is NOT a `/app/chat.init` subscription (i.e., `chat.message`, `chat.back`, `chat.restart` calls with an already-persisted session), the fixed code SHALL produce exactly the same behavior as the original code, preserving all existing workflow execution, navigation, and error handling.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4**

## Fix Implementation

### Changes Required

**File**: `src/main/java/com/xpressbees/chatbot/controller/ChatWebSocketHandler.java`

**Specific Changes**:
1. **Remove `chatSessionRepository` dependency**: The handler no longer persists sessions at init time. Remove the `ChatSessionRepository` field, constructor parameter, and import.
2. **Add pending session store**: Introduce a `ConcurrentHashMap<String, Instant>` field to track session IDs generated at init time. This provides thread-safe tracking without DB involvement.
3. **Modify `onChatInit()`**: Generate a UUID session ID, store it in the pending map (with creation timestamp), and return the response without any `save()` call.
4. **Expose a method to validate/consume pending sessions**: Add a public method `consumePendingSession(String sessionId)` that atomically removes and returns `true` if the session ID exists in the pending map, `false` otherwise. This is called by the service layer at `chat.start` time.

---

**File**: `src/main/java/com/xpressbees/chatbot/service/WorkflowExecutionServiceImpl.java`

**Function**: `startWorkflow(String sessionId, Long workflowId)`

**Specific Changes**:
1. **Inject `ChatWebSocketHandler`**: Add constructor parameter to access the pending session store.
2. **Replace `findBySessionId` lookup with pending session validation**: Instead of `chatSessionRepository.findBySessionId(sessionId)` (which would fail since no row exists yet), call `chatWebSocketHandler.consumePendingSession(sessionId)` to validate the session ID was legitimately created during init.
3. **Create and persist `ChatSession` here**: After validating the workflow exists, create a new `ChatSession` entity with the real `workflowId`, persist it via `chatSessionRepository.save()`, and continue with workflow execution.
4. **Keep existing error paths**: "Workflow not found" and "Session ID is required" error handling remain unchanged.

---

**File**: `src/main/java/com/xpressbees/chatbot/entity/ChatSession.java`

**No changes required**: The entity remains as-is. The `workflowId` column stays `NOT NULL` — we simply no longer attempt to persist with a dummy value.

---

**File**: `src/main/resources/schema.sql`

**No changes required**: The FK constraint `workflow_id BIGINT NOT NULL REFERENCES workflow(id)` remains intact. The fix respects the existing schema rather than weakening it.

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write a test that invokes `onChatInit()` on the unfixed code and asserts it completes without throwing. Run on UNFIXED code to observe the `PSQLException` failure.

**Test Cases**:
1. **Init Persistence Failure Test**: Call `onChatInit()` with a real (or mocked) `ChatSessionRepository` backed by a DB with FK constraints — expect `PSQLException` (will fail on unfixed code)
2. **Concurrent Init Test**: Call `onChatInit()` from multiple threads simultaneously — all should fail with FK violation (will fail on unfixed code)
3. **Session ID Uniqueness Test**: Verify that generated session IDs are unique UUIDs (should pass on both unfixed and fixed code)

**Expected Counterexamples**:
- `chatSessionRepository.save()` throws `DataIntegrityViolationException` wrapping `PSQLException: Key (workflow_id)=(0) is not present in table "workflow"`
- Root cause confirmed: premature persist with invalid FK value

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := onChatInit_fixed()
  ASSERT result.containsKey("sessionId")
  ASSERT result.containsKey("workflows")
  ASSERT result.get("sessionId") is a valid UUID string
  ASSERT NO database write occurred
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  // chat.start, chat.message, chat.back, chat.restart flows
  ASSERT originalFunction(input) = fixedFunction(input)
END FOR
```

**Testing Approach**: Property-based testing (jqwik) is recommended for preservation checking because:
- It generates many random workflow configurations and session states automatically
- It catches edge cases in the startWorkflow flow that manual tests might miss
- It provides strong guarantees that behavior is unchanged for post-init operations

**Test Plan**: Observe behavior on UNFIXED code for `chat.start` and `chat.message` flows (which work correctly once a session exists), then write property-based tests ensuring these flows continue to function identically after the fix.

**Test Cases**:
1. **Start Workflow Preservation**: Verify that `startWorkflow()` with a valid pending sessionId and valid workflowId creates the session and begins execution — same end state as before
2. **Message Handling Preservation**: Verify that `handleUserInput()` continues to look up persisted sessions and process input normally
3. **Error Path Preservation**: Verify that invalid workflowId still produces "Workflow not found" error, and unknown sessionId still produces "No active session found" error
4. **Back/Restart Preservation**: Verify navigation history operations continue unchanged

### Unit Tests

- Test `onChatInit()` returns valid response without DB interaction
- Test `consumePendingSession()` returns true for known IDs and false for unknown
- Test `startWorkflow()` creates and persists `ChatSession` with correct `workflowId`
- Test `startWorkflow()` rejects unknown session IDs with appropriate error
- Test thread-safety of concurrent `onChatInit()` + `consumePendingSession()` calls

### Property-Based Tests

- Generate random valid workflow IDs and verify `startWorkflow()` correctly persists sessions with the provided ID (jqwik `@ForAll`)
- Generate random session IDs (both valid pending and invalid) and verify correct acceptance/rejection behavior
- Generate random sequences of init → start → message and verify session lifecycle integrity

### Integration Tests

- Test full WebSocket flow: subscribe to `/app/chat.init` → send `/app/chat.start` → verify workflow begins
- Test that multiple clients can independently init and start without interference
- Test session expiry/cleanup of pending sessions that are never started (if TTL is implemented)
