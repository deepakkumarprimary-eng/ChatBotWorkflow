# Exception Handler Fix — Bugfix Design

## Overview

The `ChatWebSocketController.handleException()` method annotated with `@MessageExceptionHandler` has an empty body. When an unhandled exception escapes the service layer's try/catch blocks and reaches this controller-level handler, it is silently swallowed — no error response reaches the client, and no log entry is produced. The fix will populate this handler to (1) send a `ChatErrorResponse` to the client via `BufferedMessageSender.sendError()`, and (2) log the exception at ERROR level with correlation context (application session ID, STOMP session ID, exception details). The fix must gracefully handle the edge case where the session ID cannot be resolved.

## Glossary

- **Bug_Condition (C)**: An unhandled exception reaches `ChatWebSocketController.handleException()` — i.e., an exception that was not caught by the service layer's own try/catch blocks
- **Property (P)**: The handler sends a `ChatErrorResponse` to the client and logs the exception at ERROR level with correlation context
- **Preservation**: Existing service-layer error handling (inside `WorkflowExecutionServiceImpl` catch blocks), normal message flow, and non-exceptional WebSocket operations must remain unchanged
- **handleException**: The `@MessageExceptionHandler` method in `ChatWebSocketController` that catches exceptions propagating from `@MessageMapping` methods
- **BufferedMessageSender.sendError()**: Existing mechanism that sends a `ChatErrorResponse` to `/topic/chat/{sessionId}`, bypassing the per-session send buffer
- **ConnectionRegistry**: Registry that maps STOMP session IDs to application session IDs and vice-versa
- **SimpMessageHeaderAccessor**: Spring framework class providing access to STOMP message headers including session ID

## Bug Details

### Bug Condition

The bug manifests when an unhandled exception propagates from any `@MessageMapping` handler method (`startWorkflow`, `handleMessage`, `handleBack`, `handleRestart`) up to the `@MessageExceptionHandler`. The empty handler body means the exception is swallowed without any observable side effect — no client notification, no log output.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type {exception: Exception, headerAccessor: SimpMessageHeaderAccessor}
  OUTPUT: boolean
  
  RETURN input.exception IS NOT NULL
         AND input.exception was NOT caught by service-layer try/catch
         AND input.exception reached @MessageExceptionHandler in ChatWebSocketController
         AND (noErrorResponseSentToClient(input) OR noLogEntryProduced(input))
END FUNCTION
```

### Examples

- **Example 1**: `startWorkflow` is called but `ConnectionRegistry.associateApplicationSession()` throws an unexpected `NullPointerException` before the service-layer try/catch. The exception propagates to `handleException()`. **Actual**: nothing happens, client hangs. **Expected**: client receives a `ChatErrorResponse`, exception is logged at ERROR level.
- **Example 2**: Spring validation on `@Valid ChatMessageRequest` throws `MethodArgumentNotValidException` before reaching the service method. **Actual**: silently swallowed. **Expected**: error response sent, exception logged.
- **Example 3**: An unexpected `OutOfMemoryError` or `StackOverflowError` wrapped in a Spring messaging exception reaches the handler. **Actual**: no trace in logs. **Expected**: logged at ERROR level; if session resolvable, client notified.
- **Edge case**: Exception occurs but `headerAccessor.getSessionId()` returns `null` (e.g., connection already closed). **Expected**: exception logged (without STOMP session context), no attempt to send to a null destination.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Exceptions caught and handled within `WorkflowExecutionServiceImpl` try/catch blocks must continue to be handled at the service layer (these never reach `@MessageExceptionHandler`)
- Normal (non-exceptional) WebSocket message processing must continue to deliver responses without any spurious error responses
- The `BufferedMessageSender.sendError()` mechanism itself must not be modified
- The `ConnectionRegistry` interface and its implementation must remain unchanged
- Mouse/programmatic invocations of `sendError()` from within the service layer must continue to work as before

**Scope:**
All inputs that do NOT result in an unhandled exception reaching `@MessageExceptionHandler` should be completely unaffected by this fix. This includes:
- Successful message processing (chat.start, chat.message, chat.back, chat.restart)
- Service-layer handled errors (already call `sendError` internally)
- Connection lifecycle events (connect, disconnect, subscribe)

## Hypothesized Root Cause

Based on the bug description, the root cause is straightforward:

1. **Empty Handler Body**: The `handleException()` method was implemented as a no-op stub. The developer likely added the `@MessageExceptionHandler` annotation as a placeholder intending to implement it later, or assumed all exceptions would be caught at the service layer.

2. **Incomplete Error Boundary**: The service layer (`WorkflowExecutionServiceImpl`) has comprehensive try/catch blocks that call `sendError()` for expected failures. However, exceptions that occur *before* entering the service method (e.g., validation failures, serialization errors, Spring framework exceptions) or unexpected runtime exceptions that slip through the service catch blocks have no safety net.

3. **Missing Dependency Injection**: The controller currently only injects `WorkflowExecutionService` and `ConnectionRegistry`. It does not inject `BufferedMessageSender` (needed to send the error response) or an SLF4J logger (which is typically a static field, but needs to be added).

## Correctness Properties

Property 1: Bug Condition - Exception Handler Sends Error Response

_For any_ input where an unhandled exception reaches `@MessageExceptionHandler` and the application session ID can be resolved from the STOMP session (via `ConnectionRegistry`), the fixed `handleException` method SHALL send a `ChatErrorResponse` with a generic error message to the client using `BufferedMessageSender.sendError(sessionId, message)` AND log the exception at ERROR level with the STOMP session ID, application session ID, and exception details.

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Preservation - Non-Exceptional Message Processing

_For any_ input where no exception is thrown (normal message processing), the fixed code SHALL produce exactly the same behavior as the original code — messages are processed normally, responses are delivered via the existing flow, and no spurious error responses are generated. Service-layer error handling also remains unchanged.

**Validates: Requirements 3.1, 3.2, 3.3**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `src/main/java/com/xpressbees/chatbot/controller/ChatWebSocketController.java`

**Function**: `handleException(Exception ex, SimpMessageHeaderAccessor headerAccessor)`

**Specific Changes**:

1. **Add Logger**: Add a private static `Logger` field using SLF4J (`LoggerFactory.getLogger(ChatWebSocketController.class)`)

2. **Inject BufferedMessageSender**: Add `BufferedMessageSender` as a constructor parameter and field, following the existing constructor-injection convention

3. **Resolve STOMP Session ID**: Extract `stompSessionId` from `headerAccessor.getSessionId()` — handle null case

4. **Resolve Application Session ID**: Use `connectionRegistry.getApplicationSessionId(stompSessionId)` to look up the application session ID — handle null case

5. **Log the Exception**: Log at ERROR level with:
   - Application session ID (or "unknown" if null)
   - STOMP session ID (or "unknown" if null)
   - Exception class name and message
   - Full stack trace (passed as the last argument to the log call)

6. **Send Error Response**: If the application session ID is non-null, call `bufferedMessageSender.sendError(applicationSessionId, "An unexpected error occurred")` to notify the client

7. **Guard Against Null Destination**: If `stompSessionId` is null or `applicationSessionId` is null, only log — do NOT call `sendError()` with a null session ID

**Pseudocode:**
```
FUNCTION handleException(exception, headerAccessor)
  stompSessionId := headerAccessor.getSessionId()   // may be null
  applicationSessionId := null
  
  IF stompSessionId IS NOT NULL THEN
    applicationSessionId := connectionRegistry.getApplicationSessionId(stompSessionId)
  END IF
  
  log.error("Unhandled exception in WebSocket handler: sessionId={}, stompSessionId={}, error={}",
            applicationSessionId OR "unknown",
            stompSessionId OR "unknown",
            exception.getMessage(),
            exception)
  
  IF applicationSessionId IS NOT NULL THEN
    bufferedMessageSender.sendError(applicationSessionId, "An unexpected error occurred")
  END IF
END FUNCTION
```

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm that exceptions reaching `handleException()` produce no observable output (no log, no client response).

**Test Plan**: Write unit tests that directly invoke `handleException()` on the unfixed controller and assert that no interaction occurs with `BufferedMessageSender` or logging infrastructure. Run these tests on the UNFIXED code to confirm the empty-body behavior.

**Test Cases**:
1. **Direct Invocation Test**: Call `handleException(new RuntimeException("test"), headerAccessor)` and verify no `sendError()` call is made (will pass on unfixed code — confirming the bug)
2. **Simulated Propagation Test**: Mock a `@MessageMapping` method to throw, verify that no error response reaches the client (will demonstrate the hung behavior)
3. **Null Session Test**: Call `handleException()` with a header accessor returning null session ID — verify no crash but also no action (current behavior)
4. **Logging Absence Test**: Verify no ERROR-level log entry is produced when the exception reaches the handler (confirms the silent-swallow behavior)

**Expected Counterexamples**:
- `handleException()` performs no side effects — no `sendError()` call, no log output
- Client receives no notification of the failure
- Confirms root cause: the method body is literally empty

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := handleException_fixed(input.exception, input.headerAccessor)
  ASSERT errorResponseSentToClient(input.applicationSessionId)
  ASSERT errorLoggedWithCorrelation(input.stompSessionId, input.applicationSessionId, input.exception)
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalBehavior(input) = fixedBehavior(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many randomized message payloads and verifies that normal processing is unaffected
- It catches edge cases where the fix might inadvertently interfere with non-exceptional flows
- It provides strong guarantees that the `@MessageExceptionHandler` is only triggered for actual exceptions

**Test Plan**: Observe behavior on UNFIXED code first for normal message processing, then write property-based tests capturing that behavior remains identical after the fix.

**Test Cases**:
1. **Normal Message Flow Preservation**: Verify that `startWorkflow`, `handleMessage`, `handleBack`, `handleRestart` continue to function identically when no exception is thrown
2. **Service-Layer Error Handling Preservation**: Verify that exceptions caught within `WorkflowExecutionServiceImpl` still produce their own `sendError()` calls and do NOT trigger the controller-level handler
3. **Connection Lifecycle Preservation**: Verify that connect/disconnect events are unaffected

### Unit Tests

- Test `handleException()` with a valid STOMP session that maps to a known application session — verify `sendError()` is called with the correct session ID and a generic message
- Test `handleException()` with a null STOMP session ID — verify only logging occurs, no `sendError()` call
- Test `handleException()` with a STOMP session that has no associated application session — verify only logging occurs
- Test that the logged message contains the STOMP session ID, application session ID, and exception details
- Test with various exception types (`RuntimeException`, `NullPointerException`, `IllegalStateException`) to ensure consistent handling

### Property-Based Tests

- Generate random exception types and messages, pair with random session ID states (null, valid, unresolvable), and verify the handler always logs and conditionally sends error response based on session resolution
- Generate random valid `ChatMessageRequest` / `ChatStartRequest` payloads and verify that normal message processing produces no spurious error responses after the fix
- Generate random combinations of STOMP session IDs and application session IDs to verify the null-safety invariants hold across all permutations

### Integration Tests

- End-to-end test: connect a WebSocket client, trigger an exception in a `@MessageMapping` handler (e.g., by providing invalid data that bypasses `@Valid`), and verify the client receives a `ChatErrorResponse` on `/topic/chat/{sessionId}`
- End-to-end test: perform normal chat flow (start, message, back) after the fix and verify no error responses are received
- Test graceful handling when the WebSocket connection is closed before the error response can be delivered
