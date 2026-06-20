# Requirements Document

## Introduction

This feature adds two new WebSocket message handlers to the chatbot workflow engine: `chat.back` for navigating back to the previous user-input node, and `chat.restart` for resetting the current workflow to its starting point within the same session. These handlers complement the existing `chat.start` and `chat.message` handlers and leverage the existing `_navigationHistory` context structure.

## Glossary

- **Navigation_Handler**: The WebSocket controller component (`ChatWebSocketController`) that receives and routes STOMP messages at `/app/chat.back` and `/app/chat.restart`
- **Workflow_Engine**: The service layer component (`WorkflowExecutionService` / `WorkflowExecutionServiceImpl`) that executes workflow logic, manages session state, and sends responses via `SimpMessagingTemplate`
- **Chat_Session**: The JPA entity representing a user's active conversation, storing `sessionId`, `workflowId`, `currentNodeId`, `currentNodeType`, `context` (JSONB), and `status`
- **Navigation_History**: A list stored in the session context under the key `_navigationHistory`, recording every node visited during workflow execution
- **Navigation_Entry**: A single record in the Navigation_History containing `workflowId`, `nodeId`, `nodeType`, and `timestamp`
- **Input_Node**: A workflow node of type "input" that pauses execution and waits for user reply
- **Interactive_API_Node**: A workflow node of type "api" configured to pause for user selection (via `displayVariable` or button options)
- **Workflow_Stack**: A list stored in the session context under the key `_workflowStack`, tracking parent workflow entries when child workflows are active
- **Root_Workflow**: The top-level workflow that was originally started via `chat.start`, before any child workflow entries
- **User_Context_Variable**: A session context variable whose key does not start with an underscore character, representing user-entered data

## Requirements

### Requirement 1: Enhanced Navigation History Recording

**User Story:** As a developer, I want the navigation history to include node type and variable name metadata, so that the back-navigation logic can identify which entries represent user-input nodes.

#### Acceptance Criteria

1. WHEN the Workflow_Engine records a Navigation_Entry, THE Workflow_Engine SHALL include the `nodeType` field with the value of the visited node's configured node type (e.g., "input", "workflow", or null for message nodes)
2. THE Workflow_Engine SHALL record each Navigation_Entry with the format: `{"workflowId": Long, "nodeId": String, "nodeType": String or null, "timestamp": String}`

### Requirement 2: Back Navigation Handler Registration

**User Story:** As a chatbot frontend developer, I want to send a `chat.back` WebSocket message, so that the user can return to the previous input node and re-answer.

#### Acceptance Criteria

1. THE Navigation_Handler SHALL expose a STOMP message mapping at `/app/chat.back` that accepts a request containing a `sessionId` field
2. WHEN the Navigation_Handler receives a `chat.back` message, THE Navigation_Handler SHALL delegate to the Workflow_Engine `handleBack` method with the provided `sessionId`

### Requirement 3: Back Navigation Logic

**User Story:** As a chatbot user, I want to go back to my previous answer, so that I can correct a mistake without restarting the entire conversation.

#### Acceptance Criteria

1. WHEN the Workflow_Engine receives a back-navigation request, THE Workflow_Engine SHALL scan the Navigation_History backwards to find the most recent Navigation_Entry with a `nodeType` of "input" or an Interactive_API_Node entry
2. WHEN a target Navigation_Entry is found, THE Workflow_Engine SHALL set the Chat_Session `currentNodeId` to the target entry's `nodeId` value
3. WHEN a target Navigation_Entry is found, THE Workflow_Engine SHALL set the Chat_Session `currentNodeType` to the target entry's `nodeType` value
4. WHEN a target Navigation_Entry is found, THE Workflow_Engine SHALL truncate the Navigation_History up to and including the target entry, removing the target and all entries after it
5. WHEN a target Navigation_Entry is found, THE Workflow_Engine SHALL load the workflow identified by the target entry's `workflowId`, locate the target node, and send the node's prompt message (the node `name` field) to the user via WebSocket
6. WHEN the user provides a new response after back-navigation, THE Workflow_Engine SHALL overwrite the User_Context_Variable using the existing `handleInputNodeResume` logic with `context.put(variableName, message)`

### Requirement 4: Back Navigation Cross-Workflow Behavior

**User Story:** As a chatbot user in a child workflow, I want back-navigation to cross workflow boundaries, so that I can return to a parent workflow's input node when there are no previous inputs in the current child workflow.

#### Acceptance Criteria

1. WHILE the Chat_Session is executing a child workflow, WHEN the Workflow_Engine cannot find a target input Navigation_Entry within the current child workflow's entries, THE Workflow_Engine SHALL continue scanning the Navigation_History backwards into parent workflow entries
2. WHEN the target Navigation_Entry has a `workflowId` that differs from the Chat_Session's current `workflowId`, THE Workflow_Engine SHALL unwind the Workflow_Stack by removing entries until the Chat_Session `workflowId` matches the target entry's `workflowId`
3. WHEN the target Navigation_Entry has a `workflowId` that differs from the Chat_Session's current `workflowId`, THE Workflow_Engine SHALL update the Chat_Session `workflowId` to the target entry's `workflowId`

### Requirement 5: Back Navigation Edge Cases

**User Story:** As a chatbot user, I want clear error messages when back-navigation is not possible, so that I understand why the action cannot be performed.

#### Acceptance Criteria

1. IF the Navigation_History is empty or contains no input-type entries, THEN THE Workflow_Engine SHALL send an error message "No previous input to go back to" via WebSocket
2. IF the Chat_Session status is "completed", THEN THE Workflow_Engine SHALL send an error message "Session is already completed" via WebSocket
3. IF no Chat_Session exists for the provided `sessionId`, THEN THE Workflow_Engine SHALL send an error message "No active session found" via WebSocket

### Requirement 6: Back Navigation Scope

**User Story:** As a chatbot user, I want back-navigation to move one input at a time, so that I can step through my previous answers incrementally.

#### Acceptance Criteria

1. THE Workflow_Engine SHALL navigate back to exactly one previous input node per `chat.back` request, regardless of how many message or API nodes exist between the current position and the target

### Requirement 7: Restart Handler Registration

**User Story:** As a chatbot frontend developer, I want to send a `chat.restart` WebSocket message, so that the user can start the conversation over from the beginning.

#### Acceptance Criteria

1. THE Navigation_Handler SHALL expose a STOMP message mapping at `/app/chat.restart` that accepts a request containing a `sessionId` field
2. WHEN the Navigation_Handler receives a `chat.restart` message, THE Navigation_Handler SHALL delegate to the Workflow_Engine `handleRestart` method with the provided `sessionId`

### Requirement 8: Restart Logic — Context Clearing

**User Story:** As a chatbot user, I want restarting to clear all my previous answers, so that I begin the conversation with a clean slate.

#### Acceptance Criteria

1. WHEN the Workflow_Engine receives a restart request, THE Workflow_Engine SHALL remove all User_Context_Variables (keys not prefixed with underscore) from the Chat_Session context
2. WHEN the Workflow_Engine receives a restart request, THE Workflow_Engine SHALL clear the Navigation_History (set to an empty list)
3. WHEN the Workflow_Engine receives a restart request, THE Workflow_Engine SHALL clear the Workflow_Stack (set to an empty list)

### Requirement 9: Restart Logic — Session Reset

**User Story:** As a chatbot user, I want restarting to reset my session to the root workflow's beginning, so that I re-experience the conversation from the first node.

#### Acceptance Criteria

1. WHEN the Workflow_Engine receives a restart request, THE Workflow_Engine SHALL restore the Chat_Session `workflowId` to the Root_Workflow identifier
2. WHEN the Workflow_Engine receives a restart request, THE Workflow_Engine SHALL find the first node of the Root_Workflow and begin processing from that node using the existing `processNodes` method
3. WHEN the Workflow_Engine receives a restart request, THE Workflow_Engine SHALL retain the same `sessionId` without creating a new Chat_Session record
4. IF the Chat_Session status is "completed", WHEN the Workflow_Engine receives a restart request, THEN THE Workflow_Engine SHALL reset the status to "active" before processing

### Requirement 10: Restart Edge Cases

**User Story:** As a chatbot user, I want restart to handle unusual session states gracefully, so that the action succeeds or provides a clear error.

#### Acceptance Criteria

1. IF no Chat_Session exists for the provided `sessionId`, THEN THE Workflow_Engine SHALL send an error message "No active session found" via WebSocket
2. IF the Root_Workflow cannot be loaded from the database, THEN THE Workflow_Engine SHALL send an error message "Workflow not found" via WebSocket
3. IF the Root_Workflow has no starting node, THEN THE Workflow_Engine SHALL send an error message "Workflow has no starting node" via WebSocket

### Requirement 11: Service Interface Extension

**User Story:** As a developer, I want the WorkflowExecutionService interface to declare the new methods, so that the service contract is explicit and testable.

#### Acceptance Criteria

1. THE WorkflowExecutionService interface SHALL declare a method `handleBack(String sessionId)` with void return type
2. THE WorkflowExecutionService interface SHALL declare a method `handleRestart(String sessionId)` with void return type
