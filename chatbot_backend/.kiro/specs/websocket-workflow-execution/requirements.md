# Requirements Document

## Introduction

This feature adds real-time workflow execution over WebSocket (STOMP) to the chatbot backend. When a user initiates a workflow via WebSocket, the Execution_Engine walks through the workflow's node graph — sending messages, waiting for user input, and persisting session state in PostgreSQL. The engine processes nodes sequentially using transitions, supports placeholder substitution in responses, and maintains a stateless server design where session state is loaded from the database on each interaction.

## Glossary

- **Execution_Engine**: The server-side service that loads a workflow from the database, resolves the current node, processes it according to its type, and sends responses back to the user over WebSocket.
- **Chat_Session**: A database-persisted record tracking a single user's progress through a workflow, including current position and collected inputs.
- **Workflow_JSON**: The JSON structure stored in the `workflow_json` JSONB column of the `workflow` table, containing `nodes` and `transitions` arrays.
- **Node**: An element in the `nodes` array of Workflow_JSON. Each node has an `id`, `name`, `type`, and optional `config` object.
- **Transition**: An element in the `transitions` array of Workflow_JSON. Each transition has a `sourceNodeId` and `targetNodeId` defining directed graph edges.
- **Message_Node**: A Node with `type: "state"` and no `config.nodeType` set (config is null or config.nodeType is absent). The Execution_Engine sends its `name` as a message and auto-advances.
- **Input_Node**: A Node with `type: "state"` and `config.nodeType: "input"`. The Execution_Engine sends its `name` as a prompt and pauses for user input.
- **Session_Context**: A JSONB map within Chat_Session that accumulates key-value pairs of user-provided input collected during workflow execution.
- **First_Node**: The node identified as the starting point of a workflow — determined by finding the `sourceNodeId` of the first transition in the transitions array.
- **Placeholder**: A token in a node's `name` field matching the pattern `<mobile_no>` (hardcoded for now) that the Execution_Engine replaces with the corresponding value from Session_Context.
- **NodeProcessor**: A strategy interface that encapsulates the logic for handling a specific node type. Each implementation declares which nodes it can handle and how to process them, enabling new node types to be added without modifying existing code.

## Requirements

### Requirement 1: Start Workflow Execution

**User Story:** As a chat user, I want to start a workflow by sending a workflow ID over WebSocket, so that the system begins walking me through the conversation flow in real-time.

#### Acceptance Criteria

1. WHEN the user sends a message containing a `workflowId` field via WebSocket, THE Execution_Engine SHALL validate that the `workflowId` is a non-null numeric value and load the Workflow_JSON from the database for the specified workflow ID.
2. WHEN the Workflow_JSON is loaded, THE Execution_Engine SHALL identify the First_Node by finding the `sourceNodeId` of the first transition in the transitions array.
3. WHEN the First_Node is identified, THE Execution_Engine SHALL create a new Chat_Session record with a server-generated UUID as `session_id`, the workflow ID as `workflow_id`, and status set to `active`, and SHALL send a session confirmation response to the user containing the `sessionId`.
4. WHEN a new Chat_Session is created, THE Execution_Engine SHALL begin processing the First_Node according to its node type.
5. IF the specified workflow ID does not exist in the database, THEN THE Execution_Engine SHALL send an error response to the user via WebSocket indicating the workflow was not found, without closing the WebSocket connection.
6. IF the `workflowId` field is missing, null, or not a valid numeric value, THEN THE Execution_Engine SHALL send an error response to the user via WebSocket indicating the workflow ID is invalid, without closing the WebSocket connection.
7. IF the Workflow_JSON transitions array is empty or missing, THEN THE Execution_Engine SHALL send an error response to the user via WebSocket indicating that the workflow has no starting node, without creating a Chat_Session record.

### Requirement 2: Process Message Nodes

**User Story:** As a chat user, I want to receive automatic messages from the workflow without needing to respond, so that informational steps are delivered seamlessly.

#### Acceptance Criteria

1. WHEN the Execution_Engine encounters a Message_Node, THE Execution_Engine SHALL send a WebSocket response containing the full Node JSON object and the node `name` (with Placeholder substitution applied) as the `response` field.
2. WHEN a Message_Node response is sent, THE Execution_Engine SHALL resolve the next node by finding the first transition whose `sourceNodeId` matches the current node's `id`, then locating the node whose `id` matches the transition's `targetNodeId`.
3. WHEN the next node is resolved after a Message_Node, THE Execution_Engine SHALL immediately process the next node without waiting for user input.
4. THE Execution_Engine SHALL send each node response as a separate WebSocket frame to the user's session-specific STOMP destination.
5. IF consecutive Message_Nodes exceed a chain of 50 nodes without encountering an Input_Node or end-of-workflow, THEN THE Execution_Engine SHALL stop processing and send an error response indicating a potential infinite loop.

### Requirement 3: Process Input Nodes

**User Story:** As a chat user, I want to be prompted for input and have the workflow pause until I respond, so that the system can collect information from me step by step.

#### Acceptance Criteria

1. WHEN the Execution_Engine encounters an Input_Node, THE Execution_Engine SHALL send a WebSocket response containing the full Node JSON object, the node `name` (with Placeholder substitution applied) as the `response` field, and the `sessionId` field.
2. WHEN an Input_Node response is sent, THE Execution_Engine SHALL update the Chat_Session record with the current node's `id` as `current_node_id`, `current_type` as the node's `type`, and `current_node_type` as the config's `nodeType` value, while keeping the session `status` as `active`.
3. WHEN an Input_Node response is sent, THE Execution_Engine SHALL stop processing further nodes and SHALL NOT advance to the next transition until the user sends a new message containing input for the current session.
4. IF the Execution_Engine encounters a Node with `type: "state"` but the `config` object is null or the `config.nodeType` field is absent, THEN THE Execution_Engine SHALL treat the node as a Message_Node and auto-advance to the next node.

### Requirement 4: Handle User Input

**User Story:** As a chat user, I want to submit my response to a prompt and have the workflow continue from where it left off, so that the conversation progresses based on my answers.

#### Acceptance Criteria

1. WHEN the user sends a message via WebSocket containing a `sessionId` field and a `message` field, THE Execution_Engine SHALL look up the active Chat_Session matching the provided session ID.
2. IF the Chat_Session is found and `current_node_type` is `input`, THEN THE Execution_Engine SHALL store the user's `message` value in Session_Context under the hardcoded key `mobile_no`.
3. WHEN the user input is stored in Session_Context, THE Execution_Engine SHALL resolve the next node by finding the transition whose `sourceNodeId` matches the `current_node_id` and update `current_node_id` to the transition's `targetNodeId`.
4. WHEN the next node is resolved after user input, THE Execution_Engine SHALL continue processing from the next node.
5. IF no active Chat_Session is found for the given session ID, THEN THE Execution_Engine SHALL send an error response to the user via WebSocket indicating no active session exists.
6. IF the Chat_Session is found but `current_node_type` is not `input`, THEN THE Execution_Engine SHALL send an error response to the user via WebSocket indicating that the session is not currently awaiting user input.
7. IF the user sends a message via WebSocket with a missing or empty `message` field, THEN THE Execution_Engine SHALL send an error response to the user via WebSocket indicating that a non-empty message is required.

### Requirement 5: Session Persistence

**User Story:** As a system operator, I want workflow session state persisted in PostgreSQL, so that sessions survive server restarts and no in-memory state is required.

#### Acceptance Criteria

1. THE Execution_Engine SHALL persist all Chat_Session state in a `chat_session` database table with columns: `id` (primary key), `session_id` (UUID, unique), `workflow_id`, `current_node_id`, `current_type`, `current_node_type`, `context` (JSONB), `status` (one of: `active`, `completed`), `created_at`, and `updated_at`.
2. WHEN the Execution_Engine completes processing of a node, THE Execution_Engine SHALL update the Chat_Session record with the new `current_node_id`, `current_node_type`, and set `updated_at` to the current server timestamp.
3. WHEN the user provides input, THE Execution_Engine SHALL merge the new key-value pair into the `context` JSONB field, overwriting the value if the key already exists and preserving all other existing entries.
4. THE Execution_Engine SHALL generate the `session_id` as a UUID on the server side when creating a new Chat_Session.
5. IF a database write fails during session state persistence, THEN THE Execution_Engine SHALL return an error response indicating the persistence failure and SHALL NOT advance the session to the next node.
6. WHEN a client reconnects with an existing `session_id`, THE Execution_Engine SHALL load the Chat_Session record from PostgreSQL and resume execution from the persisted `current_node_id` without requiring any in-memory state from the prior server instance.

### Requirement 6: Placeholder Substitution

**User Story:** As a workflow designer, I want to use placeholders like `<mobile_no>` in node names, so that responses include previously collected user input.

#### Acceptance Criteria

1. WHEN the Execution_Engine prepares a node response, THE Execution_Engine SHALL scan the node `name` for the hardcoded Placeholder token `<mobile_no>`.
2. WHEN the `<mobile_no>` Placeholder token is found in the node `name`, THE Execution_Engine SHALL replace the token with the value stored under the key `mobile_no` in Session_Context.
3. IF the `<mobile_no>` Placeholder token is found but no `mobile_no` key exists in Session_Context, THEN THE Execution_Engine SHALL leave the `<mobile_no>` token unchanged in the response.

### Requirement 7: Workflow Completion

**User Story:** As a chat user, I want the workflow to end gracefully when all nodes are processed, so that I know the conversation is complete.

#### Acceptance Criteria

1. WHEN the Execution_Engine attempts to resolve the next node and no transition exists with `sourceNodeId` matching the current node's `id`, THE Execution_Engine SHALL mark the Chat_Session status as `completed` and update the `updated_at` timestamp.
2. WHEN the Chat_Session is marked as `completed`, THE Execution_Engine SHALL send a final response for the last node in the standard WebSocket response format (containing `node`, `response`, and `sessionId` fields) with an additional `completed` field set to `true`, and stop further node processing for that session.
3. IF the user sends a message referencing a Chat_Session whose status is `completed`, THEN THE Execution_Engine SHALL send an error response indicating the session is already completed and SHALL NOT modify the Chat_Session record.

### Requirement 8: WebSocket Response Format

**User Story:** As a frontend developer, I want a consistent response format for all workflow messages, so that I can reliably render chat messages in the UI.

#### Acceptance Criteria

1. THE Execution_Engine SHALL format each response as a JSON object containing a `node` field (the full Node JSON object from the workflow), a `response` field (the node `name` with Placeholder substitution applied), and a `sessionId` field (the session identifier as a string).
2. THE Execution_Engine SHALL send each response to the STOMP destination `/topic/chat/{sessionId}`, where `{sessionId}` is replaced with the active session identifier.
3. IF a Placeholder in the node `name` cannot be resolved, THEN THE Execution_Engine SHALL include the original Placeholder token unmodified in the `response` field string.
4. THE Execution_Engine SHALL serialize the `node` field as a JSON object containing all properties of the Node as stored in the workflow definition.

### Requirement 9: Extensible Node Processing via Strategy Pattern (SOLID Principles)

**User Story:** As a developer, I want node processing logic to be organized using a strategy pattern following SOLID principles, so that new node types can be added without modifying existing processing code and each class has a single clear responsibility.

#### Acceptance Criteria

1. THE Execution_Engine SHALL define a `NodeProcessor` interface with a `canHandle(node)` method that determines whether the processor supports a given node, and a `process(node, session)` method that executes the node logic (Interface Segregation / Dependency Inversion).
2. THE Execution_Engine SHALL provide a `MessageNodeProcessor` implementation that handles nodes with `type: "state"` and no `config.nodeType` (config is null or nodeType is absent). This class SHALL contain only message-node-related logic (Single Responsibility).
3. THE Execution_Engine SHALL provide an `InputNodeProcessor` implementation that handles nodes with `type: "state"` and `config.nodeType: "input"`. This class SHALL contain only input-node-related logic (Single Responsibility).
4. THE Execution_Engine SHALL maintain a registry of all available `NodeProcessor` implementations and SHALL iterate through them to find the first processor where `canHandle` returns true for the current node.
5. IF no registered `NodeProcessor` can handle a given node, THEN THE Execution_Engine SHALL treat the node as a Message_Node (send node name as response and auto-advance to the next node).
6. THE Execution_Engine SHALL allow new `NodeProcessor` implementations to be registered without modifying existing processor classes or the dispatch logic (Open/Closed Principle).
7. THE Execution_Engine SHALL depend on the `NodeProcessor` interface abstraction rather than concrete processor implementations (Dependency Inversion Principle).
8. All service classes SHALL accept dependencies via constructor injection rather than creating them internally, enabling testability and loose coupling (Dependency Inversion Principle).
9. THE Execution_Engine SHALL separate concerns into distinct layers: controller (WebSocket handling), service (orchestration logic), processor (node-specific logic), and repository (data access), where each layer communicates only through interfaces (Single Responsibility + Interface Segregation).
