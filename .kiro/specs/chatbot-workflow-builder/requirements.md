# Requirements Document

## Introduction

This document defines the requirements for a Chatbot Workflow Builder — a visual, drag-and-drop tool for designing chatbot conversation flows using a state machine architecture inspired by AWS Step Functions. Users create workflows composed of discrete steps (states) that define chatbot behavior, including API calls, conditional branching, user input handling, and response generation. The system uses a React frontend for the visual builder and a Java backend with PostgreSQL for persistence and workflow execution.

## Glossary

- **Workflow**: A directed graph of states that defines a chatbot conversation flow from start to finish
- **State**: A single step within a workflow that performs an action (e.g., make an API call, send a message, evaluate a condition)
- **Canvas**: The visual drag-and-drop area in the frontend where users design workflows
- **Transition**: A directional connection between two states defining the flow of execution
- **State_Type**: A category of state that determines its behavior (e.g., API_Call, Condition, Response, Input, Wait, Parallel, End)
- **Workflow_Engine**: The backend service responsible for executing a workflow by processing states in order
- **Workflow_Builder**: The frontend visual editor where users create and edit workflows
- **API_Call_State**: A state type that makes an HTTP request to an external or internal API endpoint
- **Condition_State**: A state type that evaluates an expression and branches to different states based on the result
- **Response_State**: A state type that sends a message or response to the chatbot user
- **Input_State**: A state type that waits for and captures user input
- **Wait_State**: A state type that pauses execution for a specified duration
- **Parallel_State**: A state type that executes multiple branches concurrently
- **End_State**: A terminal state that marks the completion of a workflow
- **Workflow_Definition**: The JSON representation of a workflow including all states and transitions
- **Execution**: A single runtime instance of a workflow processing a conversation
- **Context_Variables**: Data passed between states during an execution

## Requirements

### Requirement 1: Workflow Visual Canvas

**User Story:** As a workflow designer, I want a visual drag-and-drop canvas, so that I can design chatbot conversation flows intuitively.

#### Acceptance Criteria

1. THE Workflow_Builder SHALL render a Canvas where users can place, move, and connect states visually
2. WHEN a user drags a State_Type from the component palette onto the Canvas, THE Workflow_Builder SHALL create a new state instance at the drop position
3. WHEN a user draws a connection from one state to another, THE Workflow_Builder SHALL create a Transition between those states
4. IF a user attempts to create a Transition from a state to itself or a duplicate Transition between the same two states in the same direction, THEN THE Workflow_Builder SHALL reject the connection and display an error message indicating the reason
5. WHEN a user selects a state on the Canvas, THE Workflow_Builder SHALL display a configuration panel for editing that state's properties
6. THE Workflow_Builder SHALL support zoom controls on the Canvas with a minimum zoom level of 25% and a maximum zoom level of 400%, and pan controls to navigate the Canvas in any direction
7. WHEN a user requests deletion of a state from the Canvas, THE Workflow_Builder SHALL present a confirmation prompt indicating the state and the number of associated Transitions that will be removed, and upon user confirmation SHALL remove the state and all associated Transitions
8. THE Workflow_Builder SHALL support undo and redo actions for all Canvas operations including state creation, state deletion, state movement, Transition creation, and Transition deletion, retaining at least 50 operations in the undo history

### Requirement 2: State Types

**User Story:** As a workflow designer, I want multiple state types available, so that I can model different chatbot behaviors within a workflow.

#### Acceptance Criteria

1. THE Workflow_Builder SHALL provide the following State_Types in the component palette: API_Call_State, Condition_State, Response_State, Input_State, Wait_State, Parallel_State, and End_State
2. WHEN a user configures an API_Call_State, THE Workflow_Builder SHALL allow specifying the HTTP method, URL, headers, request body, and response mapping
3. WHEN a user configures a Condition_State, THE Workflow_Builder SHALL allow defining a boolean expression referencing Context_Variables using comparison operators (==, !=, <, >, <=, >=) and logical operators (AND, OR, NOT), and SHALL display two outgoing Transition labels: one for the true branch and one for the false branch
4. WHEN a user configures a Response_State, THE Workflow_Builder SHALL allow defining message templates with variable interpolation from Context_Variables
5. WHEN a user configures an Input_State, THE Workflow_Builder SHALL allow specifying a prompt message and the Context_Variable name to store the user's response
6. WHEN a user configures a Wait_State, THE Workflow_Builder SHALL allow specifying a duration between 1 and 86400 seconds
7. WHEN a user configures a Parallel_State, THE Workflow_Builder SHALL allow defining between 2 and 10 parallel execution branches

### Requirement 3: Workflow Persistence

**User Story:** As a workflow designer, I want to save, load, and manage my workflows, so that I can iterate on designs over time.

#### Acceptance Criteria

1. WHEN a user saves a workflow, THE Workflow_Builder SHALL require a workflow name of 1 to 100 characters and serialize the Canvas into a Workflow_Definition and send it to the backend for storage in PostgreSQL
2. WHEN a user opens an existing workflow, THE Workflow_Builder SHALL retrieve the Workflow_Definition from the backend and render it on the Canvas
3. THE Workflow_Builder SHALL support listing saved workflows in pages of up to 50 items, displaying name, description, creation date, and last modified date, sorted by last modified date descending
4. WHEN a user requests to delete a workflow, THE Workflow_Builder SHALL prompt the user for confirmation before removing the Workflow_Definition from PostgreSQL
5. WHEN a user saves a workflow, THE Workflow_Builder SHALL assign a monotonically incrementing integer version number starting at 1 for the initial save and incrementing by 1 on each subsequent save of the same workflow
6. IF a save, load, or delete operation fails due to a backend or network error, THEN THE Workflow_Builder SHALL display an error message indicating the nature of the failure and preserve the current Canvas state without data loss

### Requirement 4: Workflow Validation

**User Story:** As a workflow designer, I want the system to validate my workflow, so that I can catch errors before execution.

#### Acceptance Criteria

1. WHEN a user triggers validation, THE Workflow_Builder SHALL verify that the workflow contains exactly one start state (defined as the state with no incoming Transitions)
2. WHEN a user triggers validation, THE Workflow_Builder SHALL verify that every non-End_State has at least one outgoing Transition
3. WHEN a user triggers validation on a Condition_State, THE Workflow_Builder SHALL verify that the Condition_State has exactly two outgoing Transitions labeled true and false
4. WHEN a user triggers validation, THE Workflow_Builder SHALL verify that no state is unreachable from the start state using graph traversal
5. WHEN a user triggers validation, THE Workflow_Builder SHALL verify that all required configuration fields for each state are populated
6. IF validation fails, THEN THE Workflow_Builder SHALL display all validation errors simultaneously, indicating which states have issues and the nature of each issue
7. WHEN validation succeeds, THE Workflow_Builder SHALL indicate that the workflow is ready for execution
8. IF the workflow contains zero states, THEN THE Workflow_Builder SHALL report a validation error indicating the workflow is empty

### Requirement 5: Workflow Execution

**User Story:** As a system operator, I want the backend to execute workflows, so that chatbot conversations follow the designed flow.

#### Acceptance Criteria

1. WHEN a workflow Execution is started, THE Workflow_Engine SHALL initialize Context_Variables with the workflow-level default values and begin processing at the start state
2. WHEN the Workflow_Engine processes an API_Call_State, THE Workflow_Engine SHALL make the configured HTTP request and store the response body in Context_Variables according to the state's response mapping
3. WHEN the Workflow_Engine processes a Condition_State, THE Workflow_Engine SHALL evaluate the boolean expression and follow the corresponding Transition; IF the expression references an undefined Context_Variable, THEN THE Workflow_Engine SHALL treat the variable value as null for evaluation purposes
4. WHEN the Workflow_Engine processes a Response_State, THE Workflow_Engine SHALL interpolate Context_Variables into the message template and deliver the resulting message to the chatbot user's active conversation session
5. WHEN the Workflow_Engine processes an Input_State, THE Workflow_Engine SHALL pause execution until user input is received or a configured timeout elapses (default: 300 seconds), and store the received input in the specified Context_Variable
6. IF an Input_State timeout elapses without receiving user input, THEN THE Workflow_Engine SHALL follow the designated timeout Transition if configured, or halt the Execution with a timeout status
7. WHEN the Workflow_Engine processes a Wait_State, THE Workflow_Engine SHALL pause execution for the configured duration (maximum: 86400 seconds) before proceeding to the next state
8. WHEN the Workflow_Engine processes a Parallel_State, THE Workflow_Engine SHALL execute all branches concurrently, merge each branch's Context_Variable outputs sequentially in branch-definition order, and wait for all branches to complete before proceeding
9. IF any branch within a Parallel_State fails, THEN THE Workflow_Engine SHALL cancel all remaining branches and follow the designated error Transition if configured, or halt the Execution with a failure status
10. WHEN the Workflow_Engine reaches an End_State, THE Workflow_Engine SHALL mark the Execution as completed and persist the final Context_Variables in the execution history
11. IF the total Execution duration exceeds a configurable maximum (default: 3600 seconds), THEN THE Workflow_Engine SHALL halt the Execution with a timeout status

### Requirement 6: API Call Configuration and Execution

**User Story:** As a workflow designer, I want robust API call capabilities in my workflows, so that the chatbot can integrate with external services.

#### Acceptance Criteria

1. WHEN configuring an API_Call_State, THE Workflow_Builder SHALL support GET, POST, PUT, PATCH, and DELETE HTTP methods
2. WHEN configuring an API_Call_State, THE Workflow_Builder SHALL allow referencing Context_Variables in the URL, headers, and request body using double-curly-brace template syntax (e.g., `{{variableName}}`)
3. WHEN the Workflow_Engine executes an API_Call_State, THE Workflow_Engine SHALL apply a configurable timeout between 1 and 120 seconds, with a default of 30 seconds
4. IF an API call returns a non-2xx status code, THEN THE Workflow_Engine SHALL record the status code and response body in the Execution context, and follow a designated error Transition if configured, or halt the Execution with an error status
5. IF an API call times out, THEN THE Workflow_Engine SHALL follow a designated timeout Transition if configured, or halt the Execution with a timeout error status
6. WHEN an API call succeeds, THE Workflow_Engine SHALL map fields from the response body to Context_Variables according to the configured response mapping, and IF a mapped field is not present in the response, THEN THE Workflow_Engine SHALL set the corresponding Context_Variable to null
7. IF an API call fails due to a network-level error (e.g., DNS resolution failure, connection refused, or connection reset), THEN THE Workflow_Engine SHALL treat the failure identically to a timeout and follow the designated timeout Transition if configured, or halt the Execution with an error status indicating the connection failure reason

### Requirement 7: Workflow Definition Serialization

**User Story:** As a developer, I want workflows stored as structured JSON, so that they can be versioned, exported, and imported programmatically.

#### Acceptance Criteria

1. THE Workflow_Builder SHALL serialize workflows into a JSON-based Workflow_Definition format containing all states (including type and configuration), transitions (including source, target, and condition), and metadata (workflow name, version, description, creation date, and last modified date)
2. THE Workflow_Builder SHALL serialize each state's Canvas position (x and y coordinates) within the Workflow_Definition so that the visual layout is preserved on deserialization
3. WHEN a user exports a workflow, THE Workflow_Builder SHALL produce a downloadable JSON file containing the Workflow_Definition with a filename derived from the workflow name
4. WHEN a user imports a JSON file, THE Workflow_Builder SHALL validate that the file contains well-formed JSON, includes all required Workflow_Definition fields (states, transitions, metadata), and that each state has a valid State_Type before rendering the workflow on the Canvas
5. IF a user imports a JSON file that fails validation, THEN THE Workflow_Builder SHALL display an error message indicating the reason for rejection and SHALL NOT modify the current Canvas
6. THE Workflow_Builder SHALL ensure that serializing a workflow and then deserializing the resulting Workflow_Definition produces a Canvas with the same states, transitions, state configurations, and layout positions as the original (round-trip equivalence)
7. WHEN a user imports a JSON file larger than 5 MB, THE Workflow_Builder SHALL reject the file and display an error message indicating the maximum allowed file size

### Requirement 8: Execution Monitoring

**User Story:** As a system operator, I want to monitor workflow executions, so that I can troubleshoot issues and understand chatbot behavior.

#### Acceptance Criteria

1. WHILE an Execution is in progress, THE Workflow_Engine SHALL record the current state identifier, ISO 8601 timestamp, and a snapshot of Context_Variables at each state transition
2. THE Workflow_Engine SHALL persist execution history including state entry time, exit time, and outcome (one of: succeeded, failed, skipped, timed_out) for each processed state
3. WHEN a user views an Execution, THE Workflow_Builder SHALL visually distinguish the current or last active state on the Canvas from all other states using a distinct visual indicator
4. IF an Execution encounters an error, THEN THE Workflow_Engine SHALL record the error details including state identifier, error message, and stack trace, with the stack trace truncated to a maximum of 5000 characters
5. THE Workflow_Engine SHALL expose execution status via a REST API endpoint returning status (one of: running, completed, failed, paused), current state identifier, and elapsed time in milliseconds
6. WHEN a user requests a list of Executions, THE Workflow_Engine SHALL return a paginated list of executions including execution identifier, workflow name, status, start time, and end time, with a default page size of 20 and a maximum page size of 100
7. IF a user queries an Execution that does not exist, THEN THE Workflow_Engine SHALL return an error response indicating that the specified execution was not found

### Requirement 9: Error Handling and Retry

**User Story:** As a workflow designer, I want to configure error handling and retries, so that workflows can recover from transient failures.

#### Acceptance Criteria

1. WHEN configuring any state, THE Workflow_Builder SHALL allow specifying a retry policy with maximum retry count (between 0 and 10) and backoff interval (between 1 and 300 seconds)
2. IF a state execution fails due to a runtime exception, network error, or timeout and a retry policy is configured, THEN THE Workflow_Engine SHALL retry the state using exponential backoff (interval doubles on each retry) up to the configured maximum retry count
3. IF all retries are exhausted, THEN THE Workflow_Engine SHALL follow the designated error Transition if configured, or halt the Execution with a failure status and record the final error details
4. WHEN configuring any state, THE Workflow_Builder SHALL allow specifying a fallback Transition that is followed only when the error Transition is not configured and the state fails after all retries are exhausted
5. THE Workflow_Engine SHALL record each retry attempt including the attempt number, timestamp, and error message in the execution history

### Requirement 10: Context Variable Management

**User Story:** As a workflow designer, I want to define and manage variables that pass data between states, so that workflows can maintain conversational context.

#### Acceptance Criteria

1. THE Workflow_Builder SHALL allow defining up to 100 initial Context_Variables at the workflow level, each with a name of 1 to 64 alphanumeric or underscore characters and a default value
2. WHEN a state produces output, THE Workflow_Engine SHALL write the output into Context_Variables according to the state's output mapping configuration, overwriting any existing variable with the same name
3. THE Workflow_Engine SHALL make the current Context_Variables readable by all subsequent states in the execution path, where each state receives the variable values as they exist at the time of that state's execution
4. WHEN configuring a state, THE Workflow_Builder SHALL provide autocomplete suggestions listing all Context_Variables defined at the workflow level and those produced by preceding states in the execution path
5. IF a state references a Context_Variable that does not exist, THEN THE Workflow_Engine SHALL treat the variable value as null and log a warning that includes the variable name and the state identifier
6. IF a state's output mapping specifies a variable name that does not conform to the naming constraint of 1 to 64 alphanumeric or underscore characters, THEN THE Workflow_Builder SHALL display a validation error indicating the invalid variable name
