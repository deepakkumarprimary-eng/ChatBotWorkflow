# Implementation Plan: Architecture Refactoring

## Overview

Decompose the monolithic `WorkflowExecutionServiceImpl` into focused, testable services following the 6-phase implementation order defined in the design. Each phase builds incrementally on the previous, ensuring the codebase compiles and tests pass at every checkpoint. The refactoring preserves complete behavioral equivalence.

## Tasks

- [ ] 1. Phase 1: Dead Code Removal and ChatMessageSender
  - [ ] 1.1 Remove dead code from ApiNodeProcessor
    - Remove the unused `ConditionEvaluator` field/injection from `ApiNodeProcessor`
    - Remove commented-out conditional branching code block
    - Verify compilation succeeds with `mvn compile`
    - _Requirements: 7.1, 7.2, 7.3_

  - [ ] 1.2 Create ChatMessageSender service
    - Create `com.xpressbees.chatbot.service.ChatMessageSender` class
    - Inject `SimpMessagingTemplate` via constructor
    - Implement `sendResponse(String sessionId, ChatResponse response)` sending to `/topic/chat/{sessionId}`
    - Implement `sendError(String sessionId, String errorMessage)` constructing `ChatErrorResponse` and sending to `/topic/chat/{sessionId}`
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [ ]* 1.3 Write unit tests for ChatMessageSender
    - Verify `sendResponse` calls `convertAndSend` with correct destination path
    - Verify `sendError` constructs `ChatErrorResponse` and sends to correct destination
    - _Requirements: 5.1, 5.2, 5.3_

- [ ] 2. Phase 2: WorkflowJson Parameter and Processor Decoupling
  - [ ] 2.1 Update NodeProcessor interface to accept workflowJson parameter
    - Add `Map<String, Object> workflowJson` parameter to the `process` method in `NodeProcessor` interface
    - Update `MessageNodeProcessor`, `InputNodeProcessor`, `ApiNodeProcessor`, `DecisionNodeProcessor`, `WorkflowNodeProcessor` to accept the new parameter
    - Update orchestrator's processor invocation to pass the already-loaded workflowJson
    - _Requirements: 6.1, 6.2_

  - [ ] 2.2 Update NodeProcessingResult to support ERROR action
    - Add `ERROR` to the `Action` enum in `NodeProcessingResult`
    - Add `errorMessage` field with convenience constructor `NodeProcessingResult.error(String message)`
    - Maintain backwards-compatible constructor `NodeProcessingResult(Action, ChatResponse)`
    - _Requirements: 8.4_

  - [ ] 2.3 Decouple ApiNodeProcessor from infrastructure
    - Remove `WorkflowRepository` dependency — use the passed `workflowJson` parameter to resolve transitions
    - Remove `SimpMessagingTemplate` dependency — return `NodeProcessingResult.error(message)` instead of sending errors directly
    - Update all error paths (API config not found, HTTP timeout, extraction failure) to return ERROR result
    - _Requirements: 6.3, 6.5, 8.1, 8.3_

  - [ ] 2.4 Decouple DecisionNodeProcessor from infrastructure
    - Remove `WorkflowRepository` dependency — use the passed `workflowJson` parameter for transition evaluation
    - Remove `SimpMessagingTemplate` dependency — return `NodeProcessingResult.error(message)` for "no matching condition"
    - _Requirements: 6.4, 6.6, 8.2, 8.3_

  - [ ] 2.5 Update orchestrator to handle ERROR results from processors
    - In `processNodes` loop, add handling for `Action.ERROR`: invoke `chatMessageSender.sendError(sessionId, errorMessage)`
    - Replace direct `SimpMessagingTemplate` usage in the orchestrator with `ChatMessageSender` calls
    - _Requirements: 8.5, 1.4_

  - [ ]* 2.6 Write property tests for processor decoupling (Property 9)
    - **Property 9: Processors return ERROR result for error conditions**
    - Test ApiNodeProcessor returns ERROR (not sending via messagingTemplate) for: missing API config, HTTP failure, extraction failure
    - Test DecisionNodeProcessor returns ERROR for: no matching condition
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5**

- [ ] 3. Checkpoint - Phase 2 verification
  - Ensure all tests pass with `mvn test`, ask the user if questions arise.

- [ ] 4. Phase 3: WorkflowJsonUtils and SessionStateManager
  - [ ] 4.1 Create WorkflowJsonUtils utility class
    - Create `com.xpressbees.chatbot.util.WorkflowJsonUtils` as a `final` class with private constructor
    - Implement static `resolveNextNode(String currentNodeId, Map workflowJson)` — find target node via first matching transition
    - Implement overloaded `resolveNextNode(String currentNodeId, String targetNodeId, Map workflowJson)` — targeted routing with fallback
    - Implement static `findFirstNode(Map workflowJson)` — return node matching first transition's sourceNodeId
    - Implement static `findNodeById(String nodeId, Map workflowJson)` — locate node by ID
    - Implement static `findTargetNodeByName(String currentNodeId, String targetName, Map workflowJson)` — find connected target by name
    - Implement static `extractNodeType(Map node)` — extract nodeType from node config
    - Extract logic from existing methods in `WorkflowExecutionServiceImpl`
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_

  - [ ]* 4.2 Write property tests for WorkflowJsonUtils (Properties 1-4)
    - **Property 1: resolveNextNode returns the correct target**
    - **Property 2: findFirstNode returns the source of the first transition**
    - **Property 3: findNodeById locates exactly the node with the given ID**
    - **Property 4: findTargetNodeByName finds the correctly named target**
    - Use jqwik to generate random workflow JSON maps with 1-20 nodes and 0-25 transitions
    - **Validates: Requirements 11.1, 11.2, 11.3, 11.4**

  - [ ] 4.3 Create SaveResult DTO
    - Create `com.xpressbees.chatbot.dto.SaveResult` with `success`, `session`, `errorMessage` fields
    - Implement static factory methods `SaveResult.success(ChatSession)` and `SaveResult.failure(String)`
    - _Requirements: 4.1, 4.4_

  - [ ] 4.4 Create SessionStateManager service
    - Create `com.xpressbees.chatbot.service.SessionStateManager`
    - Inject `ChatSessionRepository` via constructor
    - Implement `save(ChatSession)` catching `DataAccessException` and returning `SaveResult`
    - Implement `findBySessionId(String sessionId)` returning `Optional<ChatSession>`
    - Implement `createSession(String sessionId, Long workflowId)` creating session with initial state
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [ ]* 4.5 Write unit tests for SessionStateManager
    - Test `createSession` creates session with correct initial state (active status, empty context)
    - Test `save` returns success on normal operation
    - Test `save` returns failure with descriptive message when DataAccessException occurs
    - Test `findBySessionId` delegates to repository
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [ ] 4.6 Replace inline node resolution in orchestrator with WorkflowJsonUtils
    - Replace all `resolveNextNode`, `findFirstNode`, `findNodeById`, `findTargetNodeByName` logic in `WorkflowExecutionServiceImpl` with calls to `WorkflowJsonUtils`
    - Remove now-redundant private methods from the orchestrator
    - _Requirements: 11.6_

  - [ ] 4.7 Replace direct ChatSessionRepository usage in orchestrator with SessionStateManager
    - Replace `chatSessionRepository.save(...)` calls with `sessionStateManager.save(...)` and handle `SaveResult`
    - Replace `chatSessionRepository.findBySessionId(...)` with `sessionStateManager.findBySessionId(...)`
    - Replace session creation logic with `sessionStateManager.createSession(...)`
    - Remove `ChatSessionRepository` from orchestrator's constructor injection
    - _Requirements: 1.3_

- [ ] 5. Checkpoint - Phase 3 verification
  - Ensure all tests pass with `mvn test`, ask the user if questions arise.

- [ ] 6. Phase 4: NavigationService and ChildWorkflowService
  - [ ] 6.1 Create NavigationResult DTO
    - Create `com.xpressbees.chatbot.dto.NavigationResult` with `Outcome` enum (RESUME_NODE, UNAVAILABLE, ERROR)
    - Include fields: `targetNode`, `workflowJson`, `prompt`, `errorMessage`
    - Implement static factory methods: `resumeNode(...)`, `unavailable()`, `error(String)`
    - _Requirements: 13.1_

  - [ ] 6.2 Create ChildWorkflowResult DTO
    - Create `com.xpressbees.chatbot.dto.ChildWorkflowResult` with `Outcome` enum (NEXT_NODE, COMPLETE, ERROR)
    - Include fields: `nextNode`, `workflowJson`, `errorMessage`
    - Implement static factory methods: `nextNode(...)`, `complete()`, `error(String)`
    - _Requirements: 13.2_

  - [ ] 6.3 Create NavigationService
    - Create `com.xpressbees.chatbot.service.NavigationService`
    - Inject `WorkflowRepository` and `PlaceholderService` via constructor
    - Implement `handleBack(ChatSession)` — scan history for most recent awaitsInput entry, restore state, resolve prompt, handle cross-workflow unwinding
    - Implement `handleRestart(ChatSession)` — clear non-underscore keys, reset history/stack, restore root workflow ID, return first node
    - Implement `recordNavigationEntry(ChatSession, Map node)` — append entry with workflowId, nodeId, nodeType, timestamp
    - Implement `markLastEntryAwaitsInput(ChatSession)` — set `awaitsInput=true` on last history entry
    - Use `WorkflowJsonUtils` for all node resolution
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 13.3_

  - [ ] 6.4 Create ChildWorkflowService
    - Create `com.xpressbees.chatbot.service.ChildWorkflowService`
    - Inject `WorkflowRepository` via constructor
    - Implement `enterChild(ChatSession, Long childWorkflowId, Map workflowNode)` — push parent onto stack, switch session to child, return first node
    - Implement `handleChildEnd(ChatSession)` — pop stack, restore parent, resolve next node in parent (recursive unwind if needed)
    - Return `ChildWorkflowResult.error(...)` when child workflow not found in DB
    - Return `ChildWorkflowResult.complete()` when stack empty and no next node
    - Use `WorkflowJsonUtils` for node resolution
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 13.2, 13.3_

  - [ ]* 6.5 Write property tests for NavigationService (Properties 5, 6, 7)
    - **Property 5: handleBack finds the correct target or signals unavailable**
    - **Property 6: handleRestart clears user context and resets structural keys**
    - **Property 7: recordNavigationEntry appends a correct entry**
    - Generate navigation histories with 0-30 entries, random awaitsInput placement
    - Generate session contexts with user and internal keys
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**

  - [ ]* 6.6 Write property tests for ChildWorkflowService (Property 8)
    - **Property 8: ChildWorkflowService enter/end produces correct results**
    - Generate workflow stacks of depth 0-10, child workflows with 1-10 nodes
    - Test stack growth on enter, stack pop on end, COMPLETE when stack empty
    - **Validates: Requirements 3.1, 3.2, 3.4**

- [ ] 7. Checkpoint - Phase 4 verification
  - Ensure all tests pass with `mvn test`, ask the user if questions arise.

- [ ] 8. Phase 5: Orchestrator Slim-Down and Confirmation Requirements
  - [ ] 8.1 Wire NavigationService into orchestrator
    - Replace inline `handleBack` logic in orchestrator with delegation to `NavigationService.handleBack(session)`
    - Interpret `NavigationResult`: send prompt on RESUME_NODE, send error on UNAVAILABLE/ERROR
    - Replace inline `handleRestart` logic with delegation to `NavigationService.handleRestart(session)`
    - Add `NavigationService.recordNavigationEntry(...)` calls in the `processNodes` loop
    - Add `NavigationService.markLastEntryAwaitsInput(...)` when processor returns PAUSE
    - _Requirements: 1.1, 2.1, 2.2, 2.3, 2.4, 2.5_

  - [ ] 8.2 Wire ChildWorkflowService into orchestrator
    - Replace inline child workflow entry logic with delegation to `ChildWorkflowService.enterChild(...)`
    - Replace inline child workflow end logic with delegation to `ChildWorkflowService.handleChildEnd(session)`
    - Interpret `ChildWorkflowResult`: process next node on NEXT_NODE, end loop on COMPLETE, send error on ERROR
    - _Requirements: 1.2, 3.1, 3.2, 3.3, 3.4_

  - [ ] 8.3 Confirm resume logic remains in orchestrator
    - Verify `handleApiNodeResume` and `handleInputNodeResume` remain as orchestrator methods
    - Verify processors never handle post-PAUSE replies
    - Verify `consumePendingSession` call remains in `startWorkflow`
    - Verify `InputValidationService` is invoked in `handleInputNodeResume` before storing input
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 12.1, 12.2, 12.3, 14.1, 14.2, 14.3, 14.4_

  - [ ] 8.4 Clean up orchestrator — remove extracted code
    - Remove private navigation methods that are now in NavigationService
    - Remove private child workflow methods that are now in ChildWorkflowService
    - Remove `ChatSessionRepository` injection (replaced by SessionStateManager)
    - Remove direct `SimpMessagingTemplate` injection (replaced by ChatMessageSender)
    - Update constructor to inject the four new services
    - Verify orchestrator retains only: `startWorkflow`, `handleUserInput`, `handleBack`, `handleRestart`, `processNodes`, `handleInputNodeResume`, `handleApiNodeResume`, `findProcessor`
    - _Requirements: 1.5, 1.6_

  - [ ]* 8.5 Write property tests for orchestrator dispatch (Property 12)
    - **Property 12: Orchestrator dispatches correctly per NodeProcessingResult action**
    - Test CONTINUE → sendResponse + resolveNextNode
    - Test PAUSE → save + sendResponse
    - Test ENTER_CHILD → delegate to ChildWorkflowService
    - Test ERROR → sendError
    - **Validates: Requirements 1.6, 10.3, 10.4**

  - [ ]* 8.6 Write property tests for input validation gate (Property 11)
    - **Property 11: Input validation failure blocks node advancement**
    - Generate invalid inputs against various validation rules
    - Verify error sent and session node position unchanged
    - **Validates: Requirements 14.1, 14.2**

- [ ] 9. Checkpoint - Phase 5 verification
  - Ensure all tests pass with `mvn test`, ask the user if questions arise.

- [ ] 10. Phase 6: Behavioral Equivalence Verification
  - [ ] 10.1 Capture golden output from current implementation
    - Define representative workflow definitions (linear, branching, nested child, error cases)
    - Record expected sequences of ChatResponse and ChatErrorResponse messages for each scenario
    - Store as test fixtures for comparison
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [ ]* 10.2 Write behavioral equivalence integration tests (Property 10)
    - **Property 10: Behavioral equivalence of WebSocket message sequences**
    - Replay identical inputs against refactored implementation
    - Assert identical output sequences (ChatResponse and ChatErrorResponse with same field values)
    - Verify back navigation produces same prompt restoration
    - Verify restart produces same first-node execution
    - Verify child workflow entry produces same node processing sequence
    - Verify infinite loop detection fires at threshold of 50 consecutive message nodes
    - **Validates: Requirements 9.1, 9.2, 9.3, 9.4, 9.5**

  - [ ]* 10.3 Write integration tests for cross-cutting scenarios
    - Test end-to-end workflow execution via orchestrator (start → process → complete)
    - Test cross-workflow back navigation (enter child, back to parent input node)
    - Test restart mid-workflow (verify context cleared, execution restarts)
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

- [ ] 11. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass with `mvn test`, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation after each phase
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The phased implementation order ensures the codebase compiles at every step
- Phase 1-2 establish the foundation (clean code, result-based errors, workflowJson parameter)
- Phase 3-4 extract the new services (utilities, persistence, navigation, child workflows)
- Phase 5 wires everything together and slims the orchestrator
- Phase 6 confirms behavioral equivalence end-to-end

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "2.1", "2.2"] },
    { "id": 2, "tasks": ["2.3", "2.4"] },
    { "id": 3, "tasks": ["2.5", "2.6"] },
    { "id": 4, "tasks": ["4.1", "4.3"] },
    { "id": 5, "tasks": ["4.2", "4.4"] },
    { "id": 6, "tasks": ["4.5", "4.6"] },
    { "id": 7, "tasks": ["4.7"] },
    { "id": 8, "tasks": ["6.1", "6.2"] },
    { "id": 9, "tasks": ["6.3", "6.4"] },
    { "id": 10, "tasks": ["6.5", "6.6"] },
    { "id": 11, "tasks": ["8.1", "8.2"] },
    { "id": 12, "tasks": ["8.3", "8.4"] },
    { "id": 13, "tasks": ["8.5", "8.6"] },
    { "id": 14, "tasks": ["10.1"] },
    { "id": 15, "tasks": ["10.2", "10.3"] }
  ]
}
```
