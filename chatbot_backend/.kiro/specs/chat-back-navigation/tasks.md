# Implementation Plan: Chat Back Navigation

## Overview

This plan implements `chat.back` and `chat.restart` WebSocket handlers for the chatbot workflow engine. The approach extends the existing layered architecture: a new DTO, two new controller message mappings, service interface extension, and the core navigation logic in the service implementation. Navigation history recording is enhanced with `nodeType` and `awaitsInput` metadata to support backward scanning.

## Tasks

- [x] 1. Enhance navigation history recording and store root workflow ID
  - [x] 1.1 Enhance `recordNavigationEntry` to include `nodeType` field
    - In `WorkflowExecutionServiceImpl.java`, modify `recordNavigationEntry` to extract `nodeType` from `node.config.nodeType` and add it to the navigation entry map
    - Add a private helper method `extractNodeType(Map<String, Object> node)` that reads `config.nodeType` from the node map
    - Entry format becomes: `{workflowId, nodeId, nodeType, timestamp}`
    - _Requirements: 1.1, 1.2_

  - [x] 1.2 Record `awaitsInput` flag after PAUSE action in `processNodes`
    - In the `PAUSE` branch of `processNodes`, after saving the session, set `awaitsInput = true` on the last entry in `_navigationHistory`
    - This marks entries that paused for user input (input nodes and interactive API nodes)
    - _Requirements: 1.1, 3.1_

  - [x] 1.3 Store `_rootWorkflowId` in session context during `startWorkflow`
    - In `startWorkflow`, after the session is loaded and before calling `processNodes`, add `context.put("_rootWorkflowId", workflowId)`
    - This enables `handleRestart` to always locate the root workflow
    - _Requirements: 9.1_

- [x] 2. Create DTO and extend service interface
  - [x] 2.1 Create `ChatBackRequest` DTO
    - Create `src/main/java/com/xpressbees/chatbot/dto/ChatBackRequest.java`
    - Include a single `sessionId` field with Lombok `@Data` annotation
    - This DTO serves both `chat.back` and `chat.restart` handlers
    - _Requirements: 2.1, 7.1_

  - [x] 2.2 Add `handleBack` and `handleRestart` to `WorkflowExecutionService` interface
    - Add `void handleBack(String sessionId)` method declaration
    - Add `void handleRestart(String sessionId)` method declaration
    - _Requirements: 11.1, 11.2_

- [x] 3. Implement controller message mappings
  - [x] 3.1 Add `chat.back` and `chat.restart` message mappings to `ChatWebSocketController`
    - Add `@MessageMapping("/chat.back")` method that accepts `ChatBackRequest` and delegates to `workflowExecutionService.handleBack(request.getSessionId())`
    - Add `@MessageMapping("/chat.restart")` method that accepts `ChatBackRequest` and delegates to `workflowExecutionService.handleRestart(request.getSessionId())`
    - _Requirements: 2.1, 2.2, 7.1, 7.2_

- [x] 4. Implement back-navigation logic
  - [x] 4.1 Implement `handleBack` method in `WorkflowExecutionServiceImpl`
    - Add session validation: check session exists (error: "No active session found"), check not completed (error: "Session is already completed")
    - Retrieve `_navigationHistory` from context
    - Scan backwards for most recent entry where `awaitsInput == true`
    - If no target found, send error "No previous input to go back to"
    - If target found: truncate history (remove target entry and everything after), update `currentNodeId`, `currentNodeType`, and `workflowId` on session
    - Handle cross-workflow navigation: if target's `workflowId` differs from session's current `workflowId`, unwind `_workflowStack` until match
    - Load target workflow, find target node, resolve prompt placeholder, send `ChatResponse` with node prompt
    - Persist session
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 5.1, 5.2, 5.3, 6.1_

  - [x]* 4.2 Write property test for navigation entry structure (Property 1)
    - **Property 1: Navigation entry structure preservation**
    - Create `NavigationEntryPropertyTest.java` in test source
    - Generate random node configurations with varying `nodeType` values, verify entry always contains required fields
    - `@Property(tries = 100)`
    - **Validates: Requirements 1.1, 1.2**

  - [x]* 4.3 Write property test for back-scan correctness (Property 2)
    - **Property 2: Back-scan correctness**
    - Create `BackScanPropertyTest.java` in test source
    - Generate random navigation histories with mixed node types and `awaitsInput` flags, verify scan returns most recent `awaitsInput == true` entry
    - `@Property(tries = 100)`
    - **Validates: Requirements 3.1, 3.2, 3.3, 4.1**

  - [x]* 4.4 Write property test for history truncation (Property 3)
    - **Property 3: History truncation on back-navigation**
    - In `BackScanPropertyTest.java`, verify that after back-navigation the history contains exactly K entries (0 to K-1) when target was at index K
    - `@Property(tries = 100)`
    - **Validates: Requirements 3.4**

  - [x]* 4.5 Write property test for workflow stack unwinding (Property 4)
    - **Property 4: Workflow stack unwinding on cross-workflow back**
    - Create `WorkflowStackPropertyTest.java` in test source
    - Generate random workflow stacks with varying depths and target workflowIds, verify stack is unwound correctly
    - `@Property(tries = 100)`
    - **Validates: Requirements 4.2, 4.3**

  - [x]* 4.6 Write property test for single-step back granularity (Property 5)
    - **Property 5: Single-step back granularity**
    - In `BackScanPropertyTest.java`, verify that a single `handleBack` targets exactly one entry and other `awaitsInput` entries remain in history
    - `@Property(tries = 100)`
    - **Validates: Requirements 6.1**

- [x] 5. Checkpoint — Verify back-navigation
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement restart logic
  - [x] 6.1 Implement `handleRestart` method in `WorkflowExecutionServiceImpl`
    - Add session validation: check session exists (error: "No active session found")
    - Retrieve `_rootWorkflowId` from context
    - Clear all user context variables (keys not prefixed with `_`)
    - Clear `_navigationHistory` (set to empty list)
    - Clear `_workflowStack` (set to empty list)
    - Set `session.workflowId` to root workflow ID
    - If session status is "completed", reset to "active"
    - Load root workflow (error: "Workflow not found" if missing)
    - Find first node (error: "Workflow has no starting node" if missing)
    - Call `processNodes(session, firstNode, workflowJson)` to replay from beginning
    - _Requirements: 8.1, 8.2, 8.3, 9.1, 9.2, 9.3, 9.4, 10.1, 10.2, 10.3_

  - [x]* 6.2 Write property test for restart state clearing (Property 6)
    - **Property 6: Restart clears user state and restores root workflow**
    - Create `RestartPropertyTest.java` in test source
    - Generate random contexts with mixed user/internal keys, verify after restart: no user keys remain, history is empty, stack is empty, workflowId equals `_rootWorkflowId`
    - `@Property(tries = 100)`
    - **Validates: Requirements 8.1, 8.2, 8.3, 9.1**

  - [x]* 6.3 Write unit tests for error edge cases
    - Test `handleBack` with non-existent session returns "No active session found"
    - Test `handleBack` on completed session returns "Session is already completed"
    - Test `handleBack` with empty history returns "No previous input to go back to"
    - Test `handleRestart` with non-existent session returns "No active session found"
    - Test `handleRestart` when root workflow not found returns "Workflow not found"
    - _Requirements: 5.1, 5.2, 5.3, 10.1, 10.2, 10.3_

- [x] 7. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The implementation uses Java 17 with Spring Boot 3.3.5 and jqwik 1.8.2 for property-based testing
- No database schema changes are required — all navigation state is stored in the existing `context` JSONB column

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.3", "2.1", "2.2"] },
    { "id": 1, "tasks": ["1.2", "3.1"] },
    { "id": 2, "tasks": ["4.1", "4.2"] },
    { "id": 3, "tasks": ["4.3", "4.4", "4.5", "4.6"] },
    { "id": 4, "tasks": ["6.1"] },
    { "id": 5, "tasks": ["6.2", "6.3"] }
  ]
}
```
