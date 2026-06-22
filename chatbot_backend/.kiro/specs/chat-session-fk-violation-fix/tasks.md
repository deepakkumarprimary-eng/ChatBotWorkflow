# Implementation Plan

## Overview

Fix the foreign key violation in the chat session initialization flow. The `ChatWebSocketHandler.onChatInit()` method persists a `ChatSession` with `workflowId = 0L` which violates the FK constraint on `chat_session.workflow_id`. The fix defers persistence to `chat.start` time, using an in-memory `ConcurrentHashMap` to track pending session IDs.

## Tasks

- [ ] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Chat Init FK Violation on Premature Persistence
  - **IMPORTANT**: Write this property-based test BEFORE implementing the fix
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the bug exists
  - **Scoped PBT Approach**: Scope the property to the concrete failing case: `onChatInit()` persists `ChatSession` with `workflowId = 0L` which violates FK constraint
  - Create a jqwik property test class `ChatInitBugConditionTest` in `src/test/java/com/xpressbees/chatbot/`
  - Property: _For any_ subscription to `/app/chat.init`, `onChatInit()` SHALL return a response containing a valid UUID `sessionId` and a `workflows` list WITHOUT throwing an exception or performing a database write with `workflowId = 0L`
  - Test that calling `onChatInit()` completes without `DataIntegrityViolationException` (from Bug Condition: `isBugCondition(input)` where `session.workflowId == 0L AND NOT EXISTS workflow WHERE id = 0`)
  - Use `@Property` with mocked `ChatSessionRepository` that enforces FK constraint (throws `DataIntegrityViolationException` when `save()` is called with `workflowId = 0`)
  - Assert: response map contains `sessionId` key with a valid UUID string
  - Assert: response map contains `workflows` key with the workflow list
  - Assert: no `chatSessionRepository.save()` call with `workflowId = 0L`
  - Run test on UNFIXED code - expect FAILURE (`DataIntegrityViolationException` is thrown because `save()` is called with invalid FK value)
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found (e.g., "`onChatInit()` throws `DataIntegrityViolationException` wrapping PSQLException: Key (workflow_id)=(0) is not present in table workflow")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 2.1_

- [ ] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Existing Chat Start and Message Flows Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - **IMPORTANT**: Write these tests BEFORE implementing the fix
  - Observe behavior on UNFIXED code for non-buggy inputs (flows that operate on already-persisted sessions):
  - Observe: `startWorkflow(sessionId, validWorkflowId)` with an existing persisted `ChatSession` loads the workflow, finds first node, and begins processing
  - Observe: `handleUserInput(sessionId, message)` with an existing persisted `ChatSession` looks up the session and processes input normally
  - Observe: `startWorkflow(sessionId, invalidWorkflowId)` sends "Workflow not found" error without crashing
  - Observe: `startWorkflow(unknownSessionId, workflowId)` sends "No active session found" error
  - Create a jqwik property test class `ChatSessionPreservationTest` in `src/test/java/com/xpressbees/chatbot/`
  - Write property-based test: _For all_ valid `workflowId` values (referencing existing workflows), `startWorkflow()` with a known session creates a persisted `ChatSession` and begins workflow execution
  - Write property-based test: _For all_ invalid `workflowId` values (not referencing existing workflows), `startWorkflow()` sends "Workflow not found" error
  - Write property-based test: _For all_ valid persisted sessions, `handleUserInput()` looks up the session and processes input without error
  - Use `@ForAll` with `@LongRange` for workflowId generation, mock `WorkflowRepository` to control valid/invalid scenarios
  - Verify all preservation tests PASS on UNFIXED code (these flows work correctly on already-persisted sessions)
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [ ] 3. Fix for chat session FK violation on premature persistence

  - [ ] 3.1 Modify `ChatWebSocketHandler` to remove premature persistence and add pending session store
    - Remove `ChatSessionRepository` field and constructor parameter from `ChatWebSocketHandler`
    - Add `private final ConcurrentHashMap<String, Instant> pendingSessions = new ConcurrentHashMap<>()` field
    - Modify `onChatInit()`: generate UUID session ID, store in `pendingSessions` map with `Instant.now()`, return response map with `sessionId` + workflow list — NO `chatSessionRepository.save()` call
    - Add public method `consumePendingSession(String sessionId)` that atomically removes the session ID from the map and returns `true` if found, `false` otherwise
    - _Bug_Condition: isBugCondition(input) where onChatInit() persists ChatSession with workflowId = 0L violating FK constraint_
    - _Expected_Behavior: onChatInit() returns valid sessionId + workflows without any database write_
    - _Preservation: Response format (Map with sessionId and workflows keys) remains identical_
    - _Requirements: 1.1, 1.2, 2.1, 3.1_

  - [ ] 3.2 Modify `WorkflowExecutionServiceImpl.startWorkflow()` to validate pending sessions and persist `ChatSession` at start time
    - Inject `ChatWebSocketHandler` via constructor parameter
    - In `startWorkflow(String sessionId, Long workflowId)`: call `chatWebSocketHandler.consumePendingSession(sessionId)` to validate the session ID was generated during init
    - If pending session is not found, send error "No active session found" (maintains existing error behavior)
    - After validating workflow exists via `workflowRepository.findById(workflowId)`, create new `ChatSession` entity with the real `workflowId` and persist via `chatSessionRepository.save()`
    - Continue with existing workflow execution logic (find first node, begin processing)
    - Keep existing error path: "Workflow not found" for invalid workflowId
    - _Bug_Condition: isBugCondition(input) where session.workflowId == 0L AND NOT EXISTS workflow WHERE id = 0_
    - _Expected_Behavior: ChatSession persisted only at chat.start time with valid workflowId referencing existing workflow row_
    - _Preservation: startWorkflow still loads workflow, finds first node, begins processing; error paths unchanged_
    - _Requirements: 2.2, 3.2, 3.4_

  - [ ] 3.3 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Chat Init Returns Valid Response Without DB Write
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior: `onChatInit()` returns valid `sessionId` + `workflows` without throwing or writing to DB
    - When this test passes, it confirms the expected behavior is satisfied
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed - no more FK violation)
    - _Requirements: 2.1_

  - [ ] 3.4 Verify preservation tests still pass
    - **Property 2: Preservation** - Existing Chat Start and Message Flows Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions in chat.start, chat.message, error handling flows)
    - Confirm all tests still pass after fix (no regressions)
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [ ] 4. Checkpoint - Ensure all tests pass
  - Run `mvn test` to execute the full test suite
  - Verify bug condition exploration test (Property 1) passes
  - Verify preservation property tests (Property 2) pass
  - Verify no other tests are broken by the changes
  - Ensure all tests pass, ask the user if questions arise

## Task Dependency Graph

```json
{
  "waves": [
    ["1", "2"],
    ["3.1", "3.2"],
    ["3.3", "3.4"],
    ["4"]
  ]
}
```

## Notes

- Tech stack: Java 17, Spring Boot 3.3.5, jqwik 1.8.2 for property-based testing
- Tests should use Mockito for mocking repository and messaging dependencies
- The `ConcurrentHashMap` provides thread-safety for concurrent WebSocket connections
- The bug condition exploration test (task 1) is expected to FAIL on unfixed code — this is correct behavior confirming the bug exists
- The preservation tests (task 2) are expected to PASS on unfixed code — these verify non-buggy flows work
- After implementing the fix (tasks 3.1, 3.2), both test suites should PASS
