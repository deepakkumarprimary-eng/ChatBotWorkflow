# Implementation Plan

## Overview

Fix the empty `ChatWebSocketController.handleException()` method that silently swallows exceptions without sending error responses to WebSocket clients or logging. The fix adds logging at ERROR level with correlation context and sends a `ChatErrorResponse` to the client via `BufferedMessageSender.sendError()` when the session ID is resolvable.

## Tasks

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Empty Exception Handler Silently Swallows Exceptions
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the bug exists
  - **Scoped PBT Approach**: Scope the property to concrete failing cases — invoke `handleException(new RuntimeException("test"), headerAccessor)` directly on the controller and assert expected side effects
  - Write a jqwik property-based test in `src/test/java/com/xpressbees/chatbot/controller/ChatWebSocketControllerExceptionHandlerTest.java`
  - Create controller instance with mocked `WorkflowExecutionService`, `ConnectionRegistry`, and `BufferedMessageSender`
  - Mock `headerAccessor.getSessionId()` to return a valid STOMP session ID
  - Mock `connectionRegistry.getApplicationSessionId(stompSessionId)` to return a valid application session ID
  - Call `handleException(exception, headerAccessor)` directly on the controller
  - Assert that `bufferedMessageSender.sendError(applicationSessionId, "An unexpected error occurred")` is called (from Bug Condition in design: the empty handler produces no observable side effect)
  - Generate random exception types (RuntimeException, NullPointerException, IllegalStateException) with random messages via `@ForAll`
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists because `sendError()` is never called from the empty handler body)
  - Document counterexamples found: `handleException()` performs no side effects — no `sendError()` call, no log output
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 2.1, 2.2, 2.3_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Normal Message Processing Remains Unaffected
  - **IMPORTANT**: Follow observation-first methodology
  - Observe: Normal `startWorkflow`, `handleMessage`, `handleBack`, `handleRestart` calls on unfixed code delegate to service layer without triggering exception handler
  - Observe: Service-layer exceptions caught within `WorkflowExecutionServiceImpl` produce their own `sendError()` calls and do NOT reach `@MessageExceptionHandler`
  - Write jqwik property-based test in `src/test/java/com/xpressbees/chatbot/controller/ChatWebSocketControllerPreservationTest.java`
  - Property: For all valid `ChatStartRequest`, `ChatMessageRequest`, `ChatBackRequest` inputs where service methods execute normally (no exception thrown), the controller delegates to `workflowExecutionService` methods and `handleException` is never invoked
  - Property: For all valid requests where the service layer catches and handles its own exceptions internally, `handleException` is never invoked
  - Generate random session IDs and workflow IDs via `@ForAll`
  - Verify tests PASS on UNFIXED code (confirms baseline behavior to preserve)
  - **EXPECTED OUTCOME**: Tests PASS (confirms that normal flow and service-layer error handling work correctly without the exception handler)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 3. Fix for empty handleException() method in ChatWebSocketController

  - [x] 3.1 Implement the fix
    - Add `private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class)` field to `ChatWebSocketController`
    - Add `BufferedMessageSender bufferedMessageSender` as a constructor parameter and private final field (constructor injection per project convention)
    - In `handleException(Exception ex, SimpMessageHeaderAccessor headerAccessor)`:
      - Extract `stompSessionId` from `headerAccessor.getSessionId()` (may be null)
      - If `stompSessionId` is not null, resolve `applicationSessionId` via `connectionRegistry.getApplicationSessionId(stompSessionId)`
      - Log at ERROR level: `"Unhandled exception in WebSocket handler: sessionId={}, stompSessionId={}, error={}"` with applicationSessionId (or "unknown"), stompSessionId (or "unknown"), exception message, and the full exception as the last argument
      - If `applicationSessionId` is not null, call `bufferedMessageSender.sendError(applicationSessionId, "An unexpected error occurred")`
      - If `applicationSessionId` is null (unresolvable session), only log — do NOT call `sendError()` with null destination
    - _Bug_Condition: isBugCondition(input) where exception IS NOT NULL AND reached @MessageExceptionHandler AND (noErrorResponseSentToClient OR noLogEntryProduced)_
    - _Expected_Behavior: sendError(applicationSessionId, "An unexpected error occurred") called when session resolvable; ERROR log always produced with correlation context_
    - _Preservation: Normal message flow, service-layer error handling, and connection lifecycle events remain unchanged — fix only populates the previously empty handler body_
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3_

  - [x] 3.2 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Exception Handler Sends Error Response and Logs
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior (sendError called, exception logged)
    - When this test passes, it confirms the expected behavior is satisfied
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed — handler now sends error response and logs exception)
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.3 Verify preservation tests still pass
    - **Property 2: Preservation** - Normal Message Processing Remains Unaffected
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions — normal flow and service-layer error handling unchanged)
    - Confirm all tests still pass after fix (no regressions)

- [x] 4. Checkpoint - Ensure all tests pass
  - Run full test suite with `mvn test`
  - Verify both Property 1 (bug condition → expected behavior) and Property 2 (preservation) tests pass
  - Verify no other existing tests have been broken by the constructor change (adding `BufferedMessageSender` parameter)
  - Ensure all tests pass, ask the user if questions arise

## Task Dependency Graph

```json
{
  "waves": [
    {"tasks": ["1", "2"]},
    {"tasks": ["3.1"]},
    {"tasks": ["3.2", "3.3"]},
    {"tasks": ["4"]}
  ]
}
```

## Notes

- Property 1 (Bug Condition) test is expected to FAIL on unfixed code and PASS after the fix
- Property 2 (Preservation) test is expected to PASS on both unfixed and fixed code
- The fix only modifies `ChatWebSocketController.java` — no changes to `BufferedMessageSender`, `ConnectionRegistry`, or service-layer code
- Constructor injection of `BufferedMessageSender` may require updating any test classes that instantiate `ChatWebSocketController` directly
