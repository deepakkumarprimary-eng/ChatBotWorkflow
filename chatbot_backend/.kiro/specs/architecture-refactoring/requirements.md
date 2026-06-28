# Requirements Document

## Introduction

This feature addresses critical architectural issues in the chatbot workflow engine that impede maintainability and extensibility. The `WorkflowExecutionServiceImpl` class has grown into a 450+ line God class handling multiple unrelated responsibilities. Node processors directly access infrastructure (database, messaging) instead of being pure processing units. Redundant database queries occur because workflow JSON is not passed through the processor interface. Dead code and inconsistent error-sending patterns further degrade code quality. This refactoring decomposes the monolith into focused, testable services with clear boundaries.

## Glossary

- **Orchestrator**: The `WorkflowExecutionService` component responsible for driving the node processing loop, delegating to specialized services for navigation, child workflows, and session persistence.
- **NavigationService**: A service that manages back navigation, restart logic, and navigation history tracking within chat sessions.
- **ChildWorkflowService**: A service that handles entering child workflows, managing the workflow stack, and returning control to parent workflows when child workflows complete.
- **SessionStateManager**: A service responsible for all session save/load operations against the database, encapsulating `ChatSessionRepository` access.
- **ChatMessageSender**: A unified service for sending both success responses and error messages to clients via WebSocket, replacing direct `SimpMessagingTemplate` usage in processors.
- **NodeProcessor**: An interface implemented by node type handlers (message, input, api, decision, workflow) that processes a single node and returns a result without performing side effects.
- **WorkflowJson**: The JSON representation of a workflow definition containing nodes and transitions, stored as JSONB in the database.
- **Processor_Interface**: The `NodeProcessor` Java interface that defines the contract for node processing, accepting a node map, session, placeholder service, and workflow JSON.
- **WorkflowJsonUtils**: A static utility class providing pure node resolution functions (`resolveNextNode`, `findFirstNode`, `findNodeById`, `findTargetNodeByName`) that operate on workflow JSON maps without requiring instance state.
- **NavigationResult**: A lightweight result type returned by NavigationService to communicate the outcome of a navigation operation (e.g., target node found, unavailable) without throwing exceptions.
- **ChildWorkflowResult**: A lightweight result type returned by ChildWorkflowService to communicate the outcome of child workflow entry or exit (e.g., first node of child, next node in parent, workflow complete, error) without throwing exceptions.

## Requirements

### Requirement 1: Decompose WorkflowExecutionServiceImpl into Orchestrator

**User Story:** As a developer, I want the workflow execution service to only orchestrate the node processing loop, so that each responsibility is handled by a dedicated, testable service.

#### Acceptance Criteria

1. THE Orchestrator SHALL delegate navigation history recording, back navigation, and restart logic to the NavigationService.
2. THE Orchestrator SHALL delegate child workflow entry and child workflow end handling to the ChildWorkflowService.
3. THE Orchestrator SHALL delegate all session save and load operations to the SessionStateManager.
4. THE Orchestrator SHALL delegate all WebSocket message sending to the ChatMessageSender.
5. THE Orchestrator SHALL retain only the `processNodes` loop, `startWorkflow`, `handleUserInput`, and node resolution logic.
6. WHEN a node processor returns a result, THE Orchestrator SHALL interpret the result action (CONTINUE, PAUSE, ENTER_CHILD) and coordinate the appropriate service calls without processors needing to know about infrastructure.

### Requirement 2: Extract NavigationService

**User Story:** As a developer, I want navigation logic isolated in its own service, so that back navigation and restart can be tested and modified independently.

#### Acceptance Criteria

1. THE NavigationService SHALL implement the `handleBack` operation by scanning navigation history for the most recent input-awaiting entry and restoring session state to that point.
2. THE NavigationService SHALL implement the `handleRestart` operation by clearing user context variables, resetting navigation history and workflow stack, and restarting from the root workflow's first node.
3. THE NavigationService SHALL record a navigation entry (workflowId, nodeId, nodeType, timestamp) each time a node is processed.
4. THE NavigationService SHALL support cross-workflow back navigation by unwinding the workflow stack when the target node belongs to a different workflow than the current one.
5. WHEN there is no previous input-awaiting entry in navigation history, THE NavigationService SHALL signal that back navigation is unavailable.

### Requirement 3: Extract ChildWorkflowService

**User Story:** As a developer, I want child workflow management in its own service, so that the workflow nesting logic can be understood and tested in isolation.

#### Acceptance Criteria

1. WHEN a processor returns ENTER_CHILD action, THE ChildWorkflowService SHALL push the current workflow context onto the workflow stack, switch the session to the child workflow, and return the child workflow's first node for processing.
2. WHEN a child workflow reaches its end (no next node), THE ChildWorkflowService SHALL pop the workflow stack, restore the parent workflow ID on the session, and return the next node in the parent workflow after the workflow node.
3. IF the child workflow is not found in the database, THEN THE ChildWorkflowService SHALL return an error result indicating the child workflow is unavailable.
4. IF the workflow stack is empty when a child workflow ends, THEN THE ChildWorkflowService SHALL signal that the root workflow is complete.

### Requirement 4: Extract SessionStateManager

**User Story:** As a developer, I want all session persistence operations in a single service, so that error handling for database operations is consistent and centralized.

#### Acceptance Criteria

1. THE SessionStateManager SHALL provide an operation to save a ChatSession and return a success or failure result.
2. THE SessionStateManager SHALL provide an operation to load a ChatSession by sessionId and return the session or signal not-found.
3. THE SessionStateManager SHALL provide an operation to create a new ChatSession with initial state (sessionId, workflowId, status, empty context).
4. IF a database access error occurs during save, THEN THE SessionStateManager SHALL return a failure result with a descriptive error message rather than throwing an exception.

### Requirement 5: Create ChatMessageSender Service

**User Story:** As a developer, I want a unified interface for sending WebSocket messages, so that error and success message formatting is consistent across all components.

#### Acceptance Criteria

1. THE ChatMessageSender SHALL provide an operation to send a ChatResponse to a session's WebSocket topic.
2. THE ChatMessageSender SHALL provide an operation to send a ChatErrorResponse to a session's WebSocket topic.
3. THE ChatMessageSender SHALL construct the destination path as `/topic/chat/{sessionId}` for all messages.
4. WHEN any component needs to send a WebSocket message, THE ChatMessageSender SHALL be the sole point of access to `SimpMessagingTemplate` for chat messages.

### Requirement 6: Pass WorkflowJson into Processor Interface

**User Story:** As a developer, I want the workflow JSON passed to processors by the orchestrator, so that processors do not perform redundant database queries to load it themselves.

#### Acceptance Criteria

1. THE Processor_Interface SHALL accept a workflowJson parameter (Map<String, Object>) in addition to the existing node, session, and placeholderService parameters.
2. WHEN the Orchestrator invokes a processor, THE Orchestrator SHALL pass the already-loaded workflowJson to the processor.
3. THE ApiNodeProcessor SHALL use the provided workflowJson parameter to resolve outgoing transitions instead of loading the workflow from WorkflowRepository.
4. THE DecisionNodeProcessor SHALL use the provided workflowJson parameter to evaluate transitions instead of loading the workflow from WorkflowRepository.
5. AFTER refactoring, THE ApiNodeProcessor SHALL NOT inject WorkflowRepository as a dependency.
6. AFTER refactoring, THE DecisionNodeProcessor SHALL NOT inject WorkflowRepository as a dependency.

### Requirement 7: Remove Dead Code

**User Story:** As a developer, I want dead code removed, so that the codebase is easier to understand and does not mislead future contributors.

#### Acceptance Criteria

1. THE ApiNodeProcessor SHALL NOT inject ConditionEvaluator as a dependency (the field is unused since conditional branching was moved to DecisionNodeProcessor).
2. THE ApiNodeProcessor SHALL NOT contain the commented-out conditional branching code block.
3. AFTER removal, THE application SHALL compile without errors and all existing tests SHALL pass.

### Requirement 8: Decouple Processors from Infrastructure

**User Story:** As a developer, I want processors to be pure processing units that return results, so that they can be tested without infrastructure dependencies and the orchestrator retains full control of side effects.

#### Acceptance Criteria

1. THE ApiNodeProcessor SHALL NOT inject SimpMessagingTemplate as a dependency.
2. THE DecisionNodeProcessor SHALL NOT inject SimpMessagingTemplate as a dependency.
3. WHEN a processor encounters an error condition that requires sending an error to the client, THE processor SHALL return a NodeProcessingResult with an error indicator and message rather than sending the error directly.
4. THE NodeProcessingResult SHALL support an ERROR action (or equivalent signaling mechanism) that the Orchestrator interprets to send an error via the ChatMessageSender.
5. THE Orchestrator SHALL be the sole component that invokes ChatMessageSender based on processor results.

### Requirement 9: Behavioral Equivalence

**User Story:** As a developer, I want the refactored architecture to preserve existing runtime behavior, so that no regressions are introduced for end users.

#### Acceptance Criteria

1. FOR ALL valid workflow executions, THE refactored system SHALL produce the same sequence of WebSocket messages (responses and errors) as the current implementation.
2. FOR ALL valid back navigation requests, THE refactored system SHALL restore session state and re-send the same prompt as the current implementation.
3. FOR ALL valid restart requests, THE refactored system SHALL reset session state and begin execution from the first node identically to the current implementation.
4. FOR ALL valid child workflow entries, THE refactored system SHALL maintain the same workflow stack semantics and produce the same node processing sequence as the current implementation.
5. THE refactored system SHALL maintain the same infinite loop detection threshold (50 consecutive message nodes) as the current implementation.

### Requirement 10: Resume Logic Remains in Orchestrator

**User Story:** As a developer, I want resume logic (handling user replies after PAUSE) to stay in the orchestrator, so that processors remain single-pass units with no awareness of conversation lifecycle.

#### Acceptance Criteria

1. THE Orchestrator SHALL retain the `handleApiNodeResume` method that validates user replies against stored options, updates session context, and resolves the next node.
2. THE Orchestrator SHALL retain the `handleInputNodeResume` method that validates user input against configured validation rules, stores the input in session context, and resolves the next node.
3. WHEN a processor returns PAUSE, THE processor's responsibility SHALL be considered complete for that invocation — the processor SHALL NOT be re-invoked to handle the subsequent user reply.
4. THE Orchestrator SHALL be the sole component responsible for interpreting user replies, updating context with user-provided values, and determining which node to process next after a PAUSE.

### Requirement 11: Extract WorkflowJsonUtils Utility Class

**User Story:** As a developer, I want pure node resolution functions in a shared static utility class, so that multiple services can resolve nodes from workflow JSON without coupling to each other.

#### Acceptance Criteria

1. THE WorkflowJsonUtils SHALL provide a static `resolveNextNode(String currentNodeId, Map workflowJson)` method that finds the target node for a given source node via transitions.
2. THE WorkflowJsonUtils SHALL provide a static `findFirstNode(Map workflowJson)` method that returns the first node in a workflow based on the first transition's source.
3. THE WorkflowJsonUtils SHALL provide a static `findNodeById(String nodeId, Map workflowJson)` method that locates a node by its ID.
4. THE WorkflowJsonUtils SHALL provide a static `findTargetNodeByName(String currentNodeId, String targetName, Map workflowJson)` method that finds a connected target node whose name matches the given value.
5. THE WorkflowJsonUtils SHALL be a pure utility class with no injected dependencies and no mutable state.
6. THE Orchestrator, NavigationService, and ChildWorkflowService SHALL use WorkflowJsonUtils for all node resolution operations instead of containing duplicated resolution logic.

### Requirement 12: consumePendingSession Remains in Orchestrator

**User Story:** As a developer, I want the pending session validation to stay in the orchestrator's `startWorkflow` method, so that request validation is clearly separated from session persistence concerns.

#### Acceptance Criteria

1. THE Orchestrator SHALL invoke `chatWebSocketHandler.consumePendingSession(sessionId)` within the `startWorkflow` method as a request validation pre-condition.
2. IF `consumePendingSession` returns false, THEN THE Orchestrator SHALL reject the request with an error indicating no active session was found.
3. THE SessionStateManager SHALL NOT be responsible for pending session validation — this concern belongs to the orchestrator's request handling flow.

### Requirement 13: Extracted Services Return Result Objects

**User Story:** As a developer, I want NavigationService and ChildWorkflowService to communicate outcomes via result types, so that the orchestrator interprets results consistently without relying on exception-based control flow.

#### Acceptance Criteria

1. THE NavigationService SHALL return a NavigationResult from its `handleBack` and `handleRestart` operations, indicating either the target node and workflow JSON to resume processing, or that the operation is unavailable.
2. THE ChildWorkflowService SHALL return a ChildWorkflowResult from its enter and end operations, indicating either the next node and workflow JSON to process, a completion signal, or an error with a descriptive message.
3. THE NavigationService and ChildWorkflowService SHALL NOT throw exceptions for expected outcomes (e.g., no previous input node, empty workflow stack, child workflow not found).
4. THE Orchestrator SHALL interpret NavigationResult and ChildWorkflowResult in the same pattern it uses to interpret NodeProcessingResult — checking the outcome and coordinating the next action accordingly.
5. IF a truly unexpected failure occurs (e.g., null pointer, unchecked runtime exception), THEN THE NavigationService and ChildWorkflowService MAY allow the exception to propagate to the orchestrator's existing error handling.

### Requirement 14: Input Validation Gate Remains in Orchestrator

**User Story:** As a developer, I want the input validation pre-condition check to stay in the orchestrator's resume flow, so that processors are not burdened with validation responsibilities that depend on session state and node configuration.

#### Acceptance Criteria

1. THE Orchestrator SHALL invoke InputValidationService within `handleInputNodeResume` before storing the user's input in session context.
2. IF validation fails, THEN THE Orchestrator SHALL send an error response to the client and SHALL NOT advance to the next node.
3. THE InputNodeProcessor SHALL NOT perform input validation — the processor's responsibility is limited to constructing the prompt and returning PAUSE.
4. THE InputValidationService SHALL remain a standalone service injected into the Orchestrator, not into any processor.
