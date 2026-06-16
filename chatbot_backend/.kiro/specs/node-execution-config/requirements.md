# Requirements Document

## Introduction

This feature adds node execution logic and node configuration capabilities to the Chatbot Workflow Engine. Currently, the system stores workflow definitions (nodes, edges, conditions) as untyped JSONB but has no mechanism to execute a workflow, traverse nodes based on user input, or validate/enforce node configuration schemas. This feature introduces a structured node type system, per-node configuration validation, and a workflow execution engine that processes user sessions through the node graph.

## Glossary

- **Execution_Engine**: The service component responsible for traversing workflow nodes, evaluating edges, and determining the next node to execute based on user input and conditions.
- **Node**: A single step within a workflow graph. Each node has a type, a unique identifier within its workflow, and a configuration object specific to its type.
- **Edge**: A directed connection between two nodes, optionally guarded by a condition that determines whether traversal occurs.
- **Condition**: A boolean expression attached to an edge that evaluates user input or session context to determine if the edge should be followed.
- **Session**: A stateful instance of a workflow execution for a single user conversation, tracking the current node and accumulated context variables.
- **Node_Type**: A classification of node behavior (e.g., MESSAGE, QUESTION, CONDITION, API_CALL, END) that determines what configuration properties are required and how the node executes.
- **Node_Configuration**: A type-specific set of properties that defines how a particular node behaves (e.g., the message text for a MESSAGE node, the options for a QUESTION node).
- **Context_Variables**: A key-value map maintained per session that stores data collected during workflow execution (e.g., user answers, API response values).
- **Workflow_Validator**: The component responsible for verifying that a workflow definition is structurally and semantically valid before execution.

## Requirements

### Requirement 1: Node Type Definition

**User Story:** As a workflow designer, I want to define distinct node types with specific configuration schemas, so that each node in the workflow has well-defined behavior.

#### Acceptance Criteria

1. The Execution Engine supports the following node types: MESSAGE, QUESTION, CONDITION, API_CALL, and END
2. When a workflow is created or updated, the Workflow Validator verifies that each node contains a valid "type" field matching a supported node type
3. When a workflow is created or updated, the Workflow Validator verifies that each node contains an "id" field that is unique within the workflow
4. If a node has an unsupported type value, the Workflow Validator returns an error response identifying the invalid node and the unsupported type value

### Requirement 2: Node Configuration Validation

**User Story:** As a workflow designer, I want each node type to have a validated configuration schema, so that misconfigured nodes are caught before execution.

#### Acceptance Criteria

1. When a MESSAGE node is saved, the Workflow Validator requires the node configuration to contain a non-empty "text" field
2. When a QUESTION node is saved, the Workflow Validator requires the node configuration to contain a non-empty "text" field and an "options" array with at least one entry
3. When a CONDITION node is saved, the Workflow Validator requires the node configuration to contain a "variable" field and an "operator" field
4. When an API_CALL node is saved, the Workflow Validator requires the node configuration to contain a "url" field and a "method" field
5. When an END node is saved, the Workflow Validator accepts an empty node configuration or a node configuration with an optional "message" field
6. If a node's configuration does not satisfy the schema for its node type, the Workflow Validator returns an error response listing each missing or invalid field

### Requirement 3: Workflow Session Initialization

**User Story:** As a chatbot system, I want to start a new execution session for a workflow, so that a user can begin traversing the conversation flow from the start node.

#### Acceptance Criteria

1. WHEN a session start request is received with a valid workflow identifier, THE Execution_Engine SHALL create a new Session with a unique session identifier
2. WHEN a new Session is created, THE Execution_Engine SHALL set the current node to the workflow's designated start node
3. WHEN a new Session is created, THE Execution_Engine SHALL initialize an empty Context_Variables map for the session
4. WHEN a new Session is created, THE Execution_Engine SHALL return the session identifier and the output of executing the start node
5. IF the workflow identifier does not correspond to an existing workflow, THEN THE Execution_Engine SHALL return a not-found error response
6. WHEN a workflow has no node designated as the start node, THE Workflow_Validator SHALL return a validation error indicating the workflow lacks a start node

### Requirement 4: Node Execution Logic

**User Story:** As a chatbot system, I want to execute each node according to its type, so that the user receives the appropriate response at each step.

#### Acceptance Criteria

1. WHEN a MESSAGE node is executed, THE Execution_Engine SHALL return the configured text to the caller and automatically advance to the next connected node
2. WHEN a QUESTION node is executed, THE Execution_Engine SHALL return the configured text and options to the caller and wait for user input
3. WHEN a CONDITION node is executed, THE Execution_Engine SHALL evaluate the condition expression against Context_Variables and advance along the matching outgoing edge
4. WHEN an API_CALL node is executed, THE Execution_Engine SHALL invoke the configured HTTP endpoint, store the response in Context_Variables, and advance to the next connected node
5. WHEN an END node is executed, THE Execution_Engine SHALL mark the Session as completed and return the optional end message
6. IF a node has no outgoing edges and is not an END node, THEN THE Execution_Engine SHALL mark the Session as completed and return an error indicating an unexpected terminal state

### Requirement 5: User Input Processing

**User Story:** As a chatbot system, I want to process user input and advance the workflow session, so that the conversation progresses based on user responses.

#### Acceptance Criteria

1. WHEN user input is received for an active Session, THE Execution_Engine SHALL validate that the current node is a QUESTION node awaiting input
2. WHEN valid user input is received for a QUESTION node, THE Execution_Engine SHALL store the user's answer in Context_Variables using the node's configured variable name
3. WHEN valid user input is received, THE Execution_Engine SHALL evaluate outgoing edges from the current node and advance to the next matching node
4. WHEN the next node is reached after processing input, THE Execution_Engine SHALL execute the next node and return its output
5. IF user input is received for a Session that is not awaiting input, THEN THE Execution_Engine SHALL return an error indicating the session is not in an input-awaiting state
6. IF the user input does not match any configured option for the QUESTION node, THEN THE Execution_Engine SHALL return an error indicating the input is invalid and re-present the question

### Requirement 6: Edge Condition Evaluation

**User Story:** As a workflow designer, I want edges to support conditional traversal, so that the conversation branches based on user input or context.

#### Acceptance Criteria

1. WHEN multiple outgoing edges exist from a node, THE Execution_Engine SHALL evaluate each edge's condition in the order they are defined in the workflow
2. WHEN an edge has no condition defined, THE Execution_Engine SHALL treat that edge as a default (always-true) path
3. WHEN an edge condition references a Context_Variable, THE Execution_Engine SHALL resolve the variable value from the current Session's Context_Variables
4. THE Execution_Engine SHALL support the following condition operators: EQUALS, NOT_EQUALS, CONTAINS, GREATER_THAN, LESS_THAN
5. WHEN multiple edges match, THE Execution_Engine SHALL follow the first matching edge in definition order
6. IF no outgoing edge condition matches and no default edge exists, THEN THE Execution_Engine SHALL return an error indicating no valid transition from the current node

### Requirement 7: Session State Persistence

**User Story:** As a chatbot system, I want session state to persist across requests, so that the conversation can resume after interruptions.

#### Acceptance Criteria

1. THE Execution_Engine SHALL persist Session state (current node, Context_Variables, status) to the database after each state transition
2. WHEN a session advance request is received, THE Execution_Engine SHALL load the Session state from the database using the session identifier
3. IF a session advance request references a non-existent session identifier, THEN THE Execution_Engine SHALL return a not-found error response
4. IF a session advance request references a completed Session, THEN THE Execution_Engine SHALL return an error indicating the session has already ended
5. WHILE a Session is active, THE Execution_Engine SHALL maintain a timestamp of the last interaction for the session

### Requirement 8: Workflow Structure Validation

**User Story:** As a workflow designer, I want the system to validate workflow graph structure, so that I am alerted to unreachable nodes or missing connections before execution.

#### Acceptance Criteria

1. WHEN a workflow is created or updated, THE Workflow_Validator SHALL verify that exactly one node is designated as the start node
2. WHEN a workflow is created or updated, THE Workflow_Validator SHALL verify that every edge references existing source and target node identifiers within the workflow
3. WHEN a workflow is created or updated, THE Workflow_Validator SHALL verify that at least one END node exists in the workflow
4. IF the workflow contains nodes that are not reachable from the start node, THEN THE Workflow_Validator SHALL return a warning listing the unreachable node identifiers
5. IF validation fails, THEN THE Workflow_Validator SHALL return all validation errors in a single response rather than failing on the first error
