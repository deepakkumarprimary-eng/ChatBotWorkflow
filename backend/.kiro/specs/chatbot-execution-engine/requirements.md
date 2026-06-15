# Requirements Document

## Introduction

Phase 2 of the configurable chatbot workflow execution engine. This phase covers session management, the workflow execution engine (walking the graph node-by-node), WebSocket real-time communication, conversation history persistence, and node type extensibility. Built on top of the Phase 1 workflow CRUD foundation.

## Glossary

- **Workflow**: A directed graph of nodes and transitions that defines a chatbot conversation flow, stored as a single JSONB payload.
- **Node**: A single step in a workflow. Has a type (Text, Button, or Input) and a JSON configuration payload.
- **Transition**: A directed edge connecting a source node to a destination node, optionally with a condition.
- **Start_Node**: The node in a workflow that has no incoming transitions. Derived at runtime, not explicitly marked.
- **Execution_Engine**: The backend component that walks a workflow graph node-by-node for a given session.
- **Session**: A stateful execution context for one anonymous user interacting with one workflow. Tracks the current node position.
- **Session_Token**: A unique identifier issued to an anonymous user to identify their session.
- **Text_Node**: A node type that displays a message to the user and auto-advances to the next node without requiring user input.
- **Button_Node**: A node type that presents button options to the user and waits for a selection before advancing.
- **Input_Node**: A node type that prompts the user for free-text input, captures the response, and advances to the next node.
- **Conversation_History**: The persisted record of all messages exchanged between the bot and the user within a session.
- **WebSocket_Connection**: A persistent, bidirectional communication channel between the chatbot UI and the backend.

## Requirements

### Requirement 1: Session Initialization

**User Story:** As a chatbot user, I want to start a conversation session on a workflow, so that I can interact with the chatbot.

#### Acceptance Criteria

1. WHEN a user initiates a session for an existing workflow, THE Execution_Engine SHALL load the workflow's JSON payload, parse its nodes and transitions, create a new session with a unique Session_Token, and set the current position to the Start_Node.
2. WHEN a user initiates a session for a non-existent workflow, THE Execution_Engine SHALL return an error indicating the workflow does not exist.
3. THE Execution_Engine SHALL derive the Start_Node by parsing the transitions array from the JSONB payload and finding the node with no incoming transitions.

### Requirement 2: Start Node Resolution

**User Story:** As a system operator, I want the engine to reliably identify the start node, so that every session begins at the correct entry point.

#### Acceptance Criteria

1. THE Execution_Engine SHALL identify the Start_Node by parsing the workflow's JSONB payload and finding the node whose identifier does not appear as a destination in any transition.
2. IF the parsed workflow contains zero nodes with no incoming transitions, THEN THE Execution_Engine SHALL reject session creation and return an error indicating no start node can be determined.
3. IF the parsed workflow contains more than one node with no incoming transitions, THEN THE Execution_Engine SHALL reject session creation and return an error indicating the workflow is ambiguous.

### Requirement 3: Text Node Execution

**User Story:** As a chatbot user, I want to receive text messages from the bot, so that I can read information presented by the workflow.

#### Acceptance Criteria

1. WHEN the Execution_Engine reaches a Text_Node, THE Execution_Engine SHALL read the message from the node's configuration in the parsed JSONB payload and send it to the user via the WebSocket_Connection.
2. WHEN the Execution_Engine reaches a Text_Node, THE Execution_Engine SHALL automatically advance to the next node by resolving the outgoing transition from the parsed transitions array without waiting for user input.
3. WHEN the Execution_Engine reaches a Text_Node with no outgoing transition in the transitions array, THE Execution_Engine SHALL loop back to the Start_Node of the workflow.

### Requirement 4: Input Node Execution

**User Story:** As a chatbot user, I want to provide free-text responses to the bot, so that the workflow can capture my input and continue.

#### Acceptance Criteria

1. WHEN the Execution_Engine reaches an Input_Node, THE Execution_Engine SHALL read the prompt from the node's configuration in the parsed JSONB payload and send it to the user via the WebSocket_Connection, then wait for a user response.
2. WHEN the user submits a text response to an active Input_Node, THE Execution_Engine SHALL store the response in the Conversation_History and advance to the next node by resolving the outgoing transition from the parsed transitions array.
3. WHEN the Execution_Engine reaches an Input_Node with no outgoing transition in the transitions array after receiving user input, THE Execution_Engine SHALL loop back to the Start_Node of the workflow.

### Requirement 5: Button Node Execution

**User Story:** As a chatbot user, I want to select from presented button options, so that I can direct the conversation flow.

#### Acceptance Criteria

1. WHEN the Execution_Engine reaches a Button_Node, THE Execution_Engine SHALL read the button options from the node's configuration in the parsed JSONB payload and send them to the user via the WebSocket_Connection, then wait for a selection.
2. WHEN the user selects a button option, THE Execution_Engine SHALL resolve the matching outgoing transition from the parsed transitions array based on the condition field and advance to the destination node.
3. WHEN the user selects a button option and no matching outgoing transition exists in the transitions array, THE Execution_Engine SHALL loop back to the Start_Node of the workflow.
4. WHEN the user submits a selection that does not match any configured button option, THE Execution_Engine SHALL send an error message to the user and re-present the button options.

### Requirement 6: WebSocket Communication

**User Story:** As a chatbot user, I want a persistent real-time connection to the backend, so that bot messages and my responses are delivered instantly.

#### Acceptance Criteria

1. WHEN a user opens a WebSocket_Connection, THE WebSocket_Handler SHALL accept the connection and maintain it for bidirectional messaging.
2. THE WebSocket_Handler SHALL deliver bot messages (node content) from the Execution_Engine to the connected user in real time.
3. THE WebSocket_Handler SHALL deliver user messages (responses and selections) to the Execution_Engine for processing.
4. WHEN the workflow execution reaches the end and loops back to the Start_Node, THE WebSocket_Handler SHALL keep the WebSocket_Connection open.

### Requirement 7: Session Concurrency

**User Story:** As a system operator, I want multiple users to interact with the same workflow simultaneously, so that the chatbot can serve many users at once.

#### Acceptance Criteria

1. THE Execution_Engine SHALL maintain independent session state for each Session_Token, allowing multiple concurrent sessions on the same workflow.
2. WHEN two sessions execute the same workflow concurrently, THE Execution_Engine SHALL ensure that one session's state changes do not affect the other session's state.

### Requirement 8: Conversation History Persistence

**User Story:** As a system operator, I want all conversation messages persisted, so that interaction data is available for future use.

#### Acceptance Criteria

1. WHEN the Execution_Engine sends a bot message to the user, THE Execution_Engine SHALL persist the message content, timestamp, session identifier, and direction (bot-to-user) to PostgreSQL.
2. WHEN the Execution_Engine receives a user response, THE Execution_Engine SHALL persist the message content, timestamp, session identifier, and direction (user-to-bot) to PostgreSQL.

### Requirement 9: Node Type Extensibility

**User Story:** As a developer, I want the node execution logic to be extensible, so that new node types can be added in the future without modifying existing code.

#### Acceptance Criteria

1. THE Execution_Engine SHALL use a strategy-based design where each node type is handled by a dedicated executor component.
2. WHEN a new node type needs to be supported, THE Execution_Engine SHALL allow adding a new executor component without modifying existing executor components.
