# Implementation Plan: Decision Node Processor

## Overview

Implement a `DecisionNodeProcessor` component that handles `"decision"` type nodes in the chatbot workflow engine. The processor evaluates conditions on outgoing transitions using the existing `ConditionEvaluator`, routes via the `_targetNodeId` mechanism in session context, and returns `CONTINUE` with a null response on success or `PAUSE` with error messages on failure. No changes to the workflow engine are required.

## Tasks

- [x] 1. Implement DecisionNodeProcessor class
  - [x] 1.1 Create `DecisionNodeProcessor.java` in the processor package
    - Create `src/main/java/com/xpressbees/chatbot/processor/DecisionNodeProcessor.java`
    - Annotate with `@Component` and `@Order(5)`
    - Implement `NodeProcessor` interface
    - Use constructor injection for `WorkflowRepository`, `ConditionEvaluator`, and `SimpMessagingTemplate`
    - Implement `canHandle` method: return `true` only when `node.get("type")` equals `"decision"` (case-sensitive)
    - _Requirements: 1.1, 1.2, 1.3, 7.1, 7.2, 7.3, 7.4_

  - [x] 1.2 Implement the `process` method logic
    - Extract the decision node's `id` from the node map
    - Load workflow from `WorkflowRepository` using `session.getWorkflowId()`
    - If workflow not found or `workflowJson` is null, send `ChatErrorResponse("Workflow is no longer available")` via `SimpMessagingTemplate` to `/topic/chat/{sessionId}` and return `PAUSE` with null
    - Extract the `transitions` array from the workflow JSON
    - Filter transitions where `sourceNodeId` equals the decision node's ID, preserving array order
    - If zero outgoing transitions, send `ChatErrorResponse("Decision node has no outgoing transitions")` and return `PAUSE`
    - Iterate filtered transitions in order:
      - Skip transitions with null or empty `condition`
      - Call `conditionEvaluator.evaluate(condition, session.getContext())`
      - On first `true`: validate `targetNodeId` is not null/empty (else send error "Matched transition has no target node" and return `PAUSE`), store `targetNodeId` as `_targetNodeId` in session context, return `new NodeProcessingResult(Action.CONTINUE, null)`
    - If no condition matched, send `ChatErrorResponse("No matching condition found for decision node")` and return `PAUSE`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3, 5.1, 5.2, 6.1, 6.3_

- [x] 2. Checkpoint - Verify compilation
  - Ensure the project compiles with `mvn compile`, ask the user if questions arise.

- [x] 3. Write unit tests for DecisionNodeProcessor
  - [x] 3.1 Create `DecisionNodeProcessorTest.java` with JUnit 5 unit tests
    - Create `src/test/java/com/xpressbees/chatbot/processor/DecisionNodeProcessorTest.java`
    - Mock `WorkflowRepository`, `ConditionEvaluator`, and `SimpMessagingTemplate` using Mockito
    - Test `canHandle` returns `true` for `type: "decision"` and `false` for other types/null
    - Test workflow not found → error message + PAUSE
    - Test null `workflowJson` → error message + PAUSE
    - Test zero outgoing transitions → error message + PAUSE
    - Test single transition match (happy path): `_targetNodeId` stored, CONTINUE returned, no WebSocket messages sent
    - Test multiple transitions where second matches (first skipped)
    - Test transition with null condition is skipped
    - Test matched transition with null `targetNodeId` → error + PAUSE
    - Test no condition matches → error + PAUSE, `_targetNodeId` NOT in context
    - Verify `@Order(5)` and `@Component` annotations are present via reflection
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3, 5.1, 5.2, 7.1, 7.2_

  - [ ]* 3.2 Write property test for canHandle correctness (Property 1)
    - Create `src/test/java/com/xpressbees/chatbot/processor/DecisionNodeProcessorPropertyTest.java`
    - **Property 1: canHandle Correctness**
    - Generate arbitrary node maps with varying `type` field values (null, empty, "decision", random strings)
    - Assert `canHandle` returns `true` if and only if type equals `"decision"`
    - // Feature: decision-node-processor, Property 1: canHandle returns true iff type equals "decision"
    - **Validates: Requirements 1.1, 1.2**

  - [ ]* 3.3 Write property test for transition filtering order (Property 2)
    - **Property 2: Transition Filtering Preserves Source Order**
    - Generate arbitrary transition lists with mixed `sourceNodeId` values
    - Assert filtered transitions maintain their original index order from the JSON array
    - // Feature: decision-node-processor, Property 2: Filtered transitions preserve source array order
    - **Validates: Requirements 2.2**

  - [ ]* 3.4 Write property test for first-match-wins routing (Property 3)
    - **Property 3: First-Match-Wins Routing**
    - Generate arbitrary ordered lists of outgoing transitions with conditions and session context maps
    - Assert the processor stores `targetNodeId` of the FIRST matching transition as `_targetNodeId`
    - Assert transitions with null/empty conditions are skipped
    - Assert result is CONTINUE with null response
    - // Feature: decision-node-processor, Property 3: First matching condition's targetNodeId is stored as _targetNodeId
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4**

  - [ ]* 3.5 Write property test for no-match error without context pollution (Property 4)
    - **Property 4: No-Match Error Without Context Pollution**
    - Generate arbitrary context maps and transition lists where no condition evaluates to true
    - Assert result is PAUSE with null response
    - Assert `_targetNodeId` is NOT present in session context after processing
    - Assert error message is sent via SimpMessagingTemplate
    - // Feature: decision-node-processor, Property 4: No-match returns PAUSE without storing _targetNodeId
    - **Validates: Requirements 4.1, 4.2, 4.3**

  - [ ]* 3.6 Write property test for silent success (Property 5)
    - **Property 5: Silent Success (No WebSocket Messages on Match)**
    - Generate arbitrary successful condition match scenarios
    - Assert NO `ChatResponse` or `ChatErrorResponse` is sent via `SimpMessagingTemplate`
    - // Feature: decision-node-processor, Property 5: No WebSocket messages sent on successful match
    - **Validates: Requirements 5.1, 5.2**

  - [ ]* 3.7 Write property test for context isolation on success (Property 6)
    - **Property 6: Context Isolation on Success**
    - Generate arbitrary session contexts with pre-existing keys
    - Assert only `_targetNodeId` is added/modified; no other internal keys are introduced or changed
    - // Feature: decision-node-processor, Property 6: Only _targetNodeId is modified in context on success
    - **Validates: Requirements 6.3**

- [x] 4. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass with `mvn test`, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document using jqwik 1.8.2
- Unit tests validate specific examples and edge cases using JUnit 5 + Mockito
- The processor follows the same patterns as `ApiNodeProcessor` (error handling via WebSocket, constructor injection, `@Component`/`@Order`)
- No changes to the workflow engine (`WorkflowExecutionServiceImpl`) are required

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["3.1", "3.2"] },
    { "id": 3, "tasks": ["3.3", "3.4", "3.5", "3.6", "3.7"] }
  ]
}
```
