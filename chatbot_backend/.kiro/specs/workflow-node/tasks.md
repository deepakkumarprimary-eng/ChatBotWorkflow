# Implementation Plan: Workflow Node

## Overview

Implement a "workflow" node type that enables workflow composition — one workflow can call another by reference. This involves adding the `ENTER_CHILD` action to `NodeProcessingResult`, creating a `WorkflowNodeProcessor`, and modifying `WorkflowExecutionServiceImpl` to handle workflow stack management, child workflow entry/exit, navigation history tracking, and recursion protection.

## Tasks

- [x] 1. Add ENTER_CHILD action and create WorkflowNodeProcessor
  - [x] 1.1 Add ENTER_CHILD to NodeProcessingResult.Action enum
    - Add `ENTER_CHILD` value to the `Action` enum in `NodeProcessingResult.java`
    - This signals the engine to switch execution to a child workflow
    - _Requirements: 1.6_

  - [x] 1.2 Create WorkflowNodeProcessor class
    - Create `WorkflowNodeProcessor.java` in `com.xpressbees.chatbot.processor`
    - Implement `NodeProcessor` interface with `canHandle` and `process` methods
    - Annotate with `@Component` and `@Order(4)`
    - Inject `WorkflowRepository` via constructor injection
    - `canHandle`: return `true` only when node type equals "workflow"
    - `process`: extract `workflowId` from `config` map, validate presence, parse as Long, check existence in DB, check recursion depth via `_workflowStack` size (max 10), store child workflow ID in context under `_childWorkflowId`, return `ENTER_CHILD` result
    - Return CONTINUE with descriptive error ChatResponse for: missing config/workflowId, invalid (non-numeric) workflowId, workflow not found, recursion depth exceeded
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 5.1, 5.2, 5.3, 5.4, 6.1, 6.2, 6.3, 6.4_

  - [x]* 1.3 Write property test for invalid workflowId rejection
    - **Property 1: Invalid workflowId rejection**
    - Generate random non-numeric strings for workflowId config value
    - Assert processor returns CONTINUE action with invalid identifier message
    - **Validates: Requirements 1.4**

  - [x]* 1.4 Write property test for canHandle rejects non-workflow types
    - **Property 8: canHandle rejects non-workflow node types**
    - Generate random type strings (excluding "workflow")
    - Assert canHandle returns false for all non-"workflow" types
    - **Validates: Requirements 6.3**

  - [x]* 1.5 Write property test for recursion depth enforcement
    - **Property 7: Recursion depth enforcement with state preservation**
    - Generate workflow stacks of size >= 10
    - Assert processor returns CONTINUE with error, stack remains unchanged, session not marked completed
    - **Validates: Requirements 5.2, 5.4**

- [x] 2. Implement navigation history tracking in the engine
  - [x] 2.1 Add recordNavigationEntry method to WorkflowExecutionServiceImpl
    - Create `recordNavigationEntry(ChatSession session, Map<String, Object> node)` method
    - Get or initialize `_navigationHistory` list from session context
    - Append entry with `workflowId` (from session), `nodeId` (from node), `timestamp` (ISO-8601 now)
    - _Requirements: 8.1, 8.2, 8.6_

  - [x] 2.2 Integrate navigation recording into processNodes loop
    - Call `recordNavigationEntry` at the top of the while loop in `processNodes`, before `findProcessor` is called
    - This ensures every node visit is recorded before any state change
    - _Requirements: 8.2, 8.3, 8.4_

  - [x]* 2.3 Write property test for navigation history append-only with correct format
    - **Property 10: Navigation history is append-only with correct format**
    - Generate session contexts with existing navigation history of size N
    - After processing one node, assert history size is N+1, first N entries unchanged, new entry has correct format
    - **Validates: Requirements 8.2, 8.3, 8.4, 8.6**

- [x] 3. Implement child workflow entry logic in the engine
  - [x] 3.1 Add ENTER_CHILD handling branch in processNodes loop
    - Add `else if (result.getAction() == NodeProcessingResult.Action.ENTER_CHILD)` branch in the `processNodes` while loop
    - Read and remove `_childWorkflowId` from session context
    - Call `enterChildWorkflow(session, childWorkflowId, node)`
    - Return after entering child (control transfers to child processing)
    - _Requirements: 2.5_

  - [x] 3.2 Implement enterChildWorkflow method
    - Create `enterChildWorkflow(ChatSession session, Long childWorkflowId, Map<String, Object> workflowNode)` method
    - Push stack entry `{parentWorkflowId, workflowNodeId}` onto `_workflowStack` (initialize stack if absent)
    - Call `clearTransientKeys(context)` to remove `_targetNodeId`, `_inputVariableName`, `_displayVariable`, `_buttonOptions`
    - Set `session.setWorkflowId(childWorkflowId)`
    - Load child workflow from repository, parse workflow JSON
    - Find first node of child workflow (via transitions list)
    - Call `processNodes(session, firstNode, childWorkflowJson)`
    - Handle error case: child workflow has no starting node → send error response
    - _Requirements: 2.1, 2.4, 2.5, 3.1, 3.4, 4.1, 4.2_

  - [x] 3.3 Implement clearTransientKeys helper method
    - Create `clearTransientKeys(Map<String, Object> context)` method
    - Remove keys: `_targetNodeId`, `_inputVariableName`, `_displayVariable`, `_buttonOptions`
    - Do NOT remove `_workflowStack` or `_navigationHistory`
    - _Requirements: 3.4_

  - [x] 3.4 Implement getWorkflowStack helper method
    - Create `getWorkflowStack(Map<String, Object> context)` method
    - Return existing `_workflowStack` list or initialize and store a new empty list
    - _Requirements: 4.1_

  - [x]* 3.5 Write property test for context preservation on child workflow entry
    - **Property 2: Context preservation on child workflow entry**
    - Generate session contexts with arbitrary user variables (non-underscore-prefixed keys)
    - After entering child workflow, assert all non-transient keys remain with original values
    - **Validates: Requirements 2.5, 3.1**

  - [x]* 3.6 Write property test for transient key cleanup on child workflow entry
    - **Property 3: Transient key cleanup on child workflow entry**
    - Generate session contexts containing transient keys (_targetNodeId, _inputVariableName, etc.)
    - After entering child workflow, assert all transient keys are removed
    - **Validates: Requirements 3.4**

  - [x]* 3.7 Write property test for workflow stack push correctness
    - **Property 4: Workflow stack push correctness**
    - Generate stacks of size N (0..9) and random workflow node data
    - After push, assert stack size is N+1, top entry has correct parentWorkflowId and workflowNodeId
    - **Validates: Requirements 4.2**

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement child workflow completion and return to parent
  - [x] 5.1 Implement handleChildWorkflowEnd method
    - Create `handleChildWorkflowEnd(ChatSession session)` method
    - Pop top entry from `_workflowStack`
    - Set `session.setWorkflowId(poppedEntry.parentWorkflowId)`
    - Load parent workflow from repository, parse workflow JSON
    - Resolve next node after `workflowNodeId` in parent workflow's transitions
    - If next node exists, call `processNodes(session, nextNode, parentWorkflowJson)`
    - If next node is null and stack is still non-empty, recursively call `handleChildWorkflowEnd`
    - If next node is null and stack is empty, mark session as "completed"
    - Handle error: parent workflow unavailable → send error, preserve context
    - _Requirements: 4.3, 4.4, 4.5, 4.6, 4.7_

  - [x] 5.2 Modify end-of-workflow detection in processNodes
    - Replace the current `if (node == null)` block that directly sets status to "completed"
    - New logic: if `node == null` and `_workflowStack` is non-empty, call `handleChildWorkflowEnd(session)` and return
    - If `node == null` and `_workflowStack` is empty, keep existing completion logic
    - _Requirements: 2.6, 4.3, 4.6_

  - [x] 5.3 Modify end-of-workflow detection in handleInputNodeResume
    - After resolving next node in `handleInputNodeResume`, if next node is null, check `_workflowStack`
    - If stack non-empty, call `handleChildWorkflowEnd(session)` instead of marking completed
    - _Requirements: 7.2, 7.3_

  - [x] 5.4 Modify end-of-workflow detection in handleApiNodeResume
    - After resolving next node in `handleApiNodeResume`, if next node is null, check `_workflowStack`
    - If stack non-empty, call `handleChildWorkflowEnd(session)` instead of marking completed
    - _Requirements: 7.2, 7.3_

  - [x]* 5.5 Write property test for workflow stack pop and parent restoration
    - **Property 5: Workflow stack pop and parent restoration**
    - Generate non-empty stacks, simulate child workflow end
    - Assert stack size decreases by 1, session workflowId restored to popped parentWorkflowId
    - **Validates: Requirements 4.3, 4.4, 4.5**

  - [x]* 5.6 Write property test for session completion on empty stack
    - **Property 6: Session completion on empty stack at workflow end**
    - Generate sessions with empty workflow stack at workflow end
    - Assert session status is set to "completed"
    - **Validates: Requirements 4.6**

  - [x]* 5.7 Write property test for child workflow variables retained after completion
    - **Property 11: Child workflow variables retained after completion**
    - Generate variables added during child execution
    - After child completes and returns to parent, assert variables remain in context
    - **Validates: Requirements 3.2, 3.3**

- [x] 6. Ensure child workflow pause and resume works correctly
  - [x] 6.1 Verify pause behavior preserves child workflowId
    - Confirm that existing PAUSE handling in `processNodes` saves session with `session.getWorkflowId()` which now points to child
    - Confirm `currentNodeId` and `currentNodeType` are set on session before save (already handled by InputNodeProcessor/ApiNodeProcessor)
    - No code changes expected if existing PAUSE logic uses session's current workflowId — add integration test to validate
    - _Requirements: 7.1, 7.3, 7.4_

  - [x] 6.2 Add error handling for workflow unavailable on resume
    - In `handleInputNodeResume` and `handleApiNodeResume`, add null check after loading workflow by session's workflowId
    - If workflow not found, send error via WebSocket indicating workflow is unavailable
    - _Requirements: 7.5_

  - [x]* 6.3 Write property test for child workflow pause preserves child workflowId
    - **Property 9: Child workflow pause preserves child workflowId**
    - Generate sessions in child workflow with input/api node causing PAUSE
    - Assert saved session has workflowId == child workflow ID, currentNodeId points to pausing node
    - **Validates: Requirements 7.1, 7.4**

- [x] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Write unit tests for WorkflowNodeProcessor
  - [x]* 8.1 Write unit tests for WorkflowNodeProcessor
    - Test `canHandle` returns true for type "workflow", false for other types
    - Test missing config map returns error response
    - Test null workflowId returns error response
    - Test non-numeric workflowId returns error response
    - Test workflow not found in DB returns error response
    - Test valid workflowId returns ENTER_CHILD with `_childWorkflowId` in context
    - Test recursion depth exceeded returns error response
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 5.2, 6.2, 6.3_

  - [x]* 8.2 Write integration tests for full workflow composition flow
    - Test parent → child → return to parent (happy path)
    - Test child with input node pause → user reply → continue child → complete child → return to parent
    - Test multi-level nesting: parent → child → grandchild → back to child → back to parent
    - Test recursion protection triggers at depth 10
    - Test child workflow with empty transitions returns error
    - Test navigation history records entries across parent and child workflows
    - _Requirements: 2.5, 2.6, 3.1, 3.2, 3.3, 4.2, 4.3, 4.4, 4.5, 4.6, 5.2, 7.1, 7.2, 8.2, 8.5_

- [x] 9. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The implementation uses Java 17, Spring Boot 3.3.5, and jqwik 1.8.2 for property-based testing
- All new code follows constructor injection, Lombok annotations, and existing package conventions
- No database schema changes required — all state stored in existing session context JSONB column

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1"] },
    { "id": 1, "tasks": ["1.2", "2.2", "3.4"] },
    { "id": 2, "tasks": ["1.3", "1.4", "1.5", "2.3", "3.3"] },
    { "id": 3, "tasks": ["3.1", "3.2"] },
    { "id": 4, "tasks": ["3.5", "3.6", "3.7"] },
    { "id": 5, "tasks": ["5.1"] },
    { "id": 6, "tasks": ["5.2", "5.3", "5.4"] },
    { "id": 7, "tasks": ["5.5", "5.6", "5.7", "6.1", "6.2"] },
    { "id": 8, "tasks": ["6.3", "8.1", "8.2"] }
  ]
}
```
