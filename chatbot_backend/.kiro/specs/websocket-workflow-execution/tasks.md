# Implementation Plan: WebSocket Workflow Execution

## Overview

Implement a real-time workflow execution engine over WebSocket (STOMP) that walks users through chatbot conversation flows. The engine processes a directed node graph — sending messages automatically at message nodes, pausing at input nodes for user responses, and persisting all session state in PostgreSQL. The architecture uses a Strategy Pattern for node processing, enabling new node types without modifying existing code.

## Tasks

- [ ] 1. Set up database schema, entity, and repository for ChatSession
  - [ ] 1.1 Add `chat_session` table DDL to `schema.sql`
    - Add the `chat_session` table with columns: `id` (BIGSERIAL PK), `session_id` (UUID UNIQUE NOT NULL), `workflow_id` (BIGINT NOT NULL REFERENCES workflow), `current_node_id` (VARCHAR), `current_type` (VARCHAR), `current_node_type` (VARCHAR), `context` (JSONB DEFAULT '{}'), `status` (VARCHAR NOT NULL DEFAULT 'active'), `created_at`, `updated_at`
    - Add indexes on `session_id` and `status`
    - _Requirements: 5.1_

  - [ ] 1.2 Create `ChatSession` JPA entity
    - Create `src/main/java/com/xpressbees/chatbot/entity/ChatSession.java`
    - Use `@Entity`, `@Table(name = "chat_session")`, Lombok annotations (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`)
    - Map `context` field as `Map<String, Object>` with `@Type(JsonType.class)` and `columnDefinition = "jsonb"`
    - Include `@PrePersist` and `@PreUpdate` lifecycle callbacks for timestamps
    - _Requirements: 5.1, 5.2_

  - [ ] 1.3 Create `ChatSessionRepository` interface
    - Create `src/main/java/com/xpressbees/chatbot/repository/ChatSessionRepository.java`
    - Extend `JpaRepository<ChatSession, Long>`
    - Add method `Optional<ChatSession> findBySessionId(String sessionId)`
    - _Requirements: 5.1, 5.6_

- [ ] 2. Create DTOs and response models
  - [ ] 2.1 Create WebSocket request DTOs
    - Create `ChatStartRequest.java` in the `dto` package with `Long workflowId` field
    - Create `ChatMessageRequest.java` in the `dto` package with `String sessionId` and `String message` fields
    - Use Lombok `@Data` annotations
    - _Requirements: 1.1, 4.1_

  - [ ] 2.2 Create WebSocket response DTOs
    - Create `ChatResponse.java` in the `dto` package with fields: `Map<String, Object> node`, `String response`, `String sessionId`, `Boolean completed`
    - Create `ChatErrorResponse.java` in the `dto` package with fields: `String error`, `String sessionId`
    - Include constructor overloads for normal responses (without `completed`) and completion responses
    - Use Lombok `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor`
    - _Requirements: 8.1, 8.4, 7.2_

  - [ ] 2.3 Create `NodeProcessingResult` DTO
    - Create `NodeProcessingResult.java` in the `dto` package with `Action action` (enum: CONTINUE, PAUSE, COMPLETE) and `ChatResponse response`
    - Use Lombok `@Data`, `@AllArgsConstructor`
    - _Requirements: 9.1_

- [ ] 3. Implement NodeProcessor strategy pattern
  - [ ] 3.1 Create `NodeProcessor` interface
    - Create `src/main/java/com/xpressbees/chatbot/processor/NodeProcessor.java`
    - Define `boolean canHandle(Map<String, Object> node)` method
    - Define `NodeProcessingResult process(Map<String, Object> node, ChatSession session, PlaceholderService placeholderService)` method
    - _Requirements: 9.1, 9.7_

  - [ ] 3.2 Implement `MessageNodeProcessor`
    - Create `src/main/java/com/xpressbees/chatbot/processor/MessageNodeProcessor.java`
    - Annotate with `@Component` and `@Order(2)`
    - `canHandle`: returns true when `type` is `"state"` AND (`config` is null OR `config` has no `nodeType` key)
    - `process`: resolves placeholder in node `name`, builds `ChatResponse`, returns result with `Action.CONTINUE`
    - _Requirements: 9.2, 2.1, 3.4_

  - [ ] 3.3 Implement `InputNodeProcessor`
    - Create `src/main/java/com/xpressbees/chatbot/processor/InputNodeProcessor.java`
    - Annotate with `@Component` and `@Order(1)`
    - `canHandle`: returns true when `type` is `"state"` AND `config` is not null AND `config.nodeType` equals `"input"`
    - `process`: resolves placeholder in node `name`, builds `ChatResponse`, updates session's `currentNodeId`/`currentType`/`currentNodeType`, returns result with `Action.PAUSE`
    - _Requirements: 9.3, 3.1, 3.2_

  - [ ]* 3.4 Write property test for node classification mutual exclusivity
    - **Property 2: Node Classification Mutual Exclusivity**
    - **Validates: Requirements 9.2, 9.3, 9.4, 3.4**

- [ ] 4. Implement PlaceholderService
  - [ ] 4.1 Create `PlaceholderService`
    - Create `src/main/java/com/xpressbees/chatbot/service/PlaceholderService.java`
    - Annotate with `@Service`
    - Implement `String resolve(String template, Map<String, Object> context)` method
    - Replace `<mobile_no>` token with value from context map under key `mobile_no`
    - If `mobile_no` key is absent from context, leave the token unchanged
    - Handle null template and null context gracefully
    - _Requirements: 6.1, 6.2, 6.3_

  - [ ]* 4.2 Write property test for placeholder substitution correctness
    - **Property 1: Placeholder Substitution Correctness**
    - **Validates: Requirements 6.1, 6.2, 6.3, 8.3**

- [ ] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Update WebSocket configuration
  - [ ] 6.1 Add application destination prefix to `WebSocketConfig`
    - Modify `src/main/java/com/xpressbees/chatbot/config/WebSocketConfig.java`
    - Add `config.setApplicationDestinationPrefixes("/app")` in `configureMessageBroker`
    - _Requirements: 8.2_

- [ ] 7. Implement WorkflowExecutionService
  - [ ] 7.1 Create `WorkflowExecutionService` interface
    - Create `src/main/java/com/xpressbees/chatbot/service/WorkflowExecutionService.java`
    - Define `void startWorkflow(Long workflowId, String stompSessionId)`
    - Define `void handleUserInput(String sessionId, String message)`
    - _Requirements: 1.1, 4.1, 9.7_

  - [ ] 7.2 Implement `WorkflowExecutionServiceImpl` — workflow start logic
    - Create `src/main/java/com/xpressbees/chatbot/service/WorkflowExecutionServiceImpl.java`
    - Inject `WorkflowRepository`, `ChatSessionRepository`, `List<NodeProcessor>`, `PlaceholderService`, `SimpMessagingTemplate` via constructor
    - Implement `startWorkflow`: validate workflowId, load workflow from DB, find first node via transitions array, create ChatSession with UUID, begin node processing
    - Implement `findFirstNode`: get `sourceNodeId` from first transition, locate node in `nodes` array
    - Handle errors: workflow not found, invalid workflowId, empty/missing transitions
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.6, 1.7_

  - [ ] 7.3 Implement `WorkflowExecutionServiceImpl` — node processing loop
    - Implement `processNodes`: loop through nodes calling appropriate processor, send responses via `SimpMessagingTemplate`, auto-advance on CONTINUE, stop on PAUSE/COMPLETE
    - Implement `resolveNextNode`: find transition with matching `sourceNodeId`, return target node or null if no transition
    - Implement `findProcessor`: iterate registered processors, return first where `canHandle` is true, fall back to message-node behavior if none match
    - Implement infinite loop guard: stop after 50 consecutive message nodes
    - On COMPLETE (no next transition): mark session `completed`, set `completed=true` in response
    - Persist session state after each pause/completion
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 7.1, 7.2, 9.4, 9.5_

  - [ ] 7.4 Implement `WorkflowExecutionServiceImpl` — user input handling
    - Implement `handleUserInput`: validate session exists and is active, validate message non-empty, validate session is awaiting input (`current_node_type` is `input`)
    - Store user input in session context under key `mobile_no`
    - Resolve next node from `current_node_id` and continue processing
    - Handle errors: session not found, session completed, not awaiting input, empty message
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 5.3_

  - [ ] 7.5 Implement response sending helpers
    - Implement `sendResponse(String sessionId, ChatResponse response)`: send to `/topic/chat/{sessionId}`
    - Implement `sendError(String sessionId, String errorMessage)`: send `ChatErrorResponse` to `/topic/chat/{sessionId}`
    - _Requirements: 8.2_

  - [ ]* 7.6 Write property test for graph traversal correctness
    - **Property 3: Graph Traversal Returns Correct Next Node**
    - **Validates: Requirements 2.2, 4.3, 1.2**

  - [ ]* 7.7 Write property test for infinite loop guard
    - **Property 7: Infinite Loop Guard**
    - **Validates: Requirements 2.5**

  - [ ]* 7.8 Write property test for workflow completion detection
    - **Property 6: Workflow Completion Detection**
    - **Validates: Requirements 7.1, 7.2**

- [ ] 8. Implement ChatWebSocketController
  - [ ] 8.1 Create `ChatWebSocketController`
    - Create `src/main/java/com/xpressbees/chatbot/controller/ChatWebSocketController.java`
    - Annotate with `@Controller`
    - Inject `WorkflowExecutionService` via constructor
    - Implement `@MessageMapping("/chat.start")` method: extract `workflowId` from `ChatStartRequest`, call `startWorkflow`
    - Implement `@MessageMapping("/chat.message")` method: extract `sessionId` and `message` from `ChatMessageRequest`, call `handleUserInput`
    - Implement `@MessageExceptionHandler` for unhandled exceptions
    - _Requirements: 1.1, 4.1, 9.9_

- [ ] 9. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 10. Add jqwik test dependency and write remaining property tests
  - [ ] 10.1 Add jqwik dependency to `pom.xml`
    - Add `net.jqwik:jqwik:1.8.2` with `<scope>test</scope>`
    - _Requirements: 9.1_

  - [ ]* 10.2 Write property test for response format consistency
    - **Property 4: Response Format Consistency**
    - **Validates: Requirements 8.1, 8.4, 2.1, 3.1**

  - [ ]* 10.3 Write property test for context merge preserves existing entries
    - **Property 5: Context Merge Preserves Existing Entries**
    - **Validates: Requirements 5.3, 4.2**

  - [ ]* 10.4 Write property test for input validation rejects invalid workflow IDs
    - **Property 8: Input Validation Rejects Invalid Workflow IDs**
    - **Validates: Requirements 1.1, 1.6**

  - [ ]* 10.5 Write property test for session state persistence after input node
    - **Property 9: Session State Persistence After Input Node**
    - **Validates: Requirements 3.2, 5.2**

- [ ] 11. Write unit tests for error handling paths
  - [ ]* 11.1 Write unit tests for WorkflowExecutionService error scenarios
    - Test start workflow with non-existent ID returns error (Req 1.5)
    - Test start workflow with empty transitions returns error (Req 1.7)
    - Test send message to non-existent session returns error (Req 4.5)
    - Test send message to session not awaiting input returns error (Req 4.6)
    - Test send message to completed session returns error (Req 7.3)
    - Test empty/missing message field returns error (Req 4.7)
    - Test session creation generates valid UUID (Req 5.4)
    - _Requirements: 1.5, 1.6, 1.7, 4.5, 4.6, 4.7, 5.4, 5.5, 7.3_

- [ ] 12. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties using jqwik (100 iterations minimum)
- Unit tests validate specific examples and edge cases
- The existing `ChatWebSocketHandler` (subscription-based workflow list) remains unchanged; the new `ChatWebSocketController` handles workflow execution via `@MessageMapping`
- The existing `WorkflowRepository` is reused for loading workflow data
- Constructor injection is used throughout (no `@Autowired` field injection)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "2.2", "2.3"] },
    { "id": 1, "tasks": ["1.2", "1.3", "3.1"] },
    { "id": 2, "tasks": ["3.2", "3.3", "4.1", "6.1"] },
    { "id": 3, "tasks": ["3.4", "4.2", "7.1"] },
    { "id": 4, "tasks": ["7.2", "7.3"] },
    { "id": 5, "tasks": ["7.4", "7.5"] },
    { "id": 6, "tasks": ["7.6", "7.7", "7.8", "8.1"] },
    { "id": 7, "tasks": ["10.1"] },
    { "id": 8, "tasks": ["10.2", "10.3", "10.4", "10.5", "11.1"] }
  ]
}
```
