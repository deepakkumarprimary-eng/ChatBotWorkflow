# Implementation Plan: Chatbot Workflow Builder

## Overview

This implementation plan builds the Chatbot Workflow Builder in incremental steps, starting with shared data models and database schema, then the backend execution engine and REST API, followed by the React frontend canvas, validation, and execution monitoring. Each task builds on previous work to ensure no orphaned code.

## Tasks

- [x] 1. Set up project structure, database schema, and core interfaces
  - [x] 1.1 Create PostgreSQL database schema
    - Create migration file with all tables: `workflows`, `workflow_versions`, `executions`, `execution_history`, `retry_attempts`
    - Include all indexes, constraints, and CHECK clauses as specified in the design
    - _Requirements: 3.1, 3.5, 5.1, 8.1, 8.2, 9.5_

  - [x] 1.2 Create Java domain models and enums
    - Create `StateType`, `ExecutionStatus`, `StateOutcome` enums
    - Create `WorkflowDefinition`, `StateDefinition`, `TransitionDefinition`, `RetryPolicy`, `ContextVariable`, `Execution`, `WorkflowMetadata`, `Position` classes
    - Include Jackson annotations for JSON serialization/deserialization
    - _Requirements: 2.1, 7.1, 7.2_

  - [x] 1.3 Create TypeScript interfaces and types for the frontend
    - Create `CanvasState`, `WorkflowState`, `Transition`, `StateType`, `StateConfig` (union type), `CanvasOperation`, `ContextVariable`, `RetryPolicy` interfaces
    - Create API response/request types for workflow CRUD and execution endpoints
    - _Requirements: 1.1, 2.1, 7.1, 7.2_

  - [x] 1.4 Set up backend project structure with Spring Boot configuration
    - Configure Spring Boot application with PostgreSQL datasource
    - Set up Maven/Gradle dependencies (Spring Web, Spring Data JPA, Jackson, jqwik for testing)
    - Create package structure: controller, service, engine, model, repository
    - _Requirements: 3.1_

  - [x] 1.5 Set up React frontend project structure
    - Initialize React project with TypeScript (Node 18)
    - Install dependencies: React Flow (canvas library), fast-check (property testing), testing library
    - Create directory structure: components, hooks, services, types, utils
    - _Requirements: 1.1_

- [x] 2. Implement backend workflow CRUD and versioning
  - [x] 2.1 Implement Workflow repository and service layer
    - Create JPA entities mapping to `workflows` and `workflow_versions` tables
    - Implement `WorkflowService` with create, read, update, delete (soft delete), and list operations
    - Implement versioning logic: monotonically incrementing version starting at 1
    - Implement pagination for listing (max 50 per page, sorted by last_modified_at DESC)
    - _Requirements: 3.1, 3.3, 3.4, 3.5_

  - [x] 2.2 Implement Workflow REST controller
    - Create `WorkflowController` with endpoints: POST /api/workflows, GET /api/workflows, GET /api/workflows/{id}, PUT /api/workflows/{id}, DELETE /api/workflows/{id}
    - Implement request validation (workflow name 1-100 chars)
    - Implement proper error responses (404 for not found, 400 for invalid input)
    - _Requirements: 3.1, 3.3, 3.4, 3.6_

  - [x] 2.3 Implement workflow export and import endpoints
    - Create GET /api/workflows/{id}/export endpoint returning JSON file download
    - Create POST /api/workflows/import endpoint with file size validation (max 5 MB)
    - Validate imported JSON structure: required fields, valid state types
    - Return detailed error messages for invalid imports
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

  - [x] 2.4 Write property tests for workflow persistence (Java/jqwik)
    - **Property 9: Workflow name length validation**
    - **Property 10: Workflow listing pagination and sorting**
    - **Property 11: Version number monotonic increment**
    - **Validates: Requirements 3.1, 3.3, 3.5**

  - [x] 2.5 Write property tests for serialization and import (Java/jqwik)
    - **Property 24: Workflow serialization round-trip**
    - **Property 25: Import validation rejects malformed definitions**
    - **Validates: Requirements 7.1, 7.2, 7.4, 7.6**

- [x] 3. Implement backend validation engine
  - [x] 3.1 Implement graph-based workflow validation
    - Implement `ValidationEngine` service with BFS/DFS graph traversal
    - Validate single start state (no incoming transitions)
    - Validate all non-End states have outgoing transitions
    - Validate Condition_State has exactly two transitions (true/false)
    - Validate all states reachable from start state
    - Validate required configuration fields per state type
    - Validate empty workflow check
    - Collect and return all errors simultaneously
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8_

  - [x] 3.2 Implement validation REST endpoint
    - Create POST /api/workflows/{id}/validate endpoint
    - Return validation results with per-state error details
    - _Requirements: 4.6, 4.7_

  - [x] 3.3 Write property tests for validation logic (Java/jqwik)
    - **Property 13: Single start state validation**
    - **Property 14: Non-End states require outgoing transitions**
    - **Property 15: Condition state transition structure**
    - **Property 16: All states reachable from start**
    - **Property 17: Required configuration fields validation**
    - **Property 18: Complete validation error reporting**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6**

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement backend workflow execution engine
  - [x] 5.1 Implement core execution engine and state processor framework
    - Create `WorkflowEngine` service that initializes execution with context variable defaults
    - Create `StateProcessor` interface with implementations for each state type
    - Implement execution state machine: start → process states → complete/fail/pause
    - Persist execution records and context variable snapshots at each transition
    - Implement execution-level timeout (default 3600s)
    - _Requirements: 5.1, 5.10, 5.11, 8.1, 8.2_

  - [x] 5.2 Implement API_Call state processor
    - Make HTTP requests with configured method, URL, headers, body
    - Support template variable interpolation in URL, headers, and body using `{{variableName}}` syntax
    - Apply configurable timeout (1-120s, default 30s)
    - Map response fields to context variables (null for missing fields)
    - Handle non-2xx responses: record status/body, follow error transition or halt
    - Handle timeout/network errors: follow timeout transition or halt
    - _Requirements: 5.2, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

  - [x] 5.3 Implement Condition state processor
    - Evaluate boolean expressions with comparison operators (==, !=, <, >, <=, >=) and logical operators (AND, OR, NOT)
    - Resolve context variable references in expressions
    - Follow true/false transition based on evaluation result
    - Treat undefined variables as null
    - _Requirements: 5.3, 2.3_

  - [x] 5.4 Implement Response state processor
    - Interpolate context variables into message templates using `{{variableName}}` syntax
    - Handle undefined variables as null representation
    - Deliver message to the chatbot user's active session
    - _Requirements: 5.4, 2.4_

  - [x] 5.5 Implement Input state processor
    - Pause execution until user input received or timeout (default 300s)
    - Store received input in specified context variable
    - Follow timeout transition if configured, or halt with timeout status
    - _Requirements: 5.5, 5.6_

  - [x] 5.6 Implement Wait state processor
    - Pause execution for configured duration (1-86400 seconds)
    - Resume and proceed to next state after duration elapses
    - _Requirements: 5.7, 2.6_

  - [x] 5.7 Implement Parallel state processor
    - Execute 2-10 branches concurrently using thread pool
    - Merge branch context variable outputs in branch-definition order
    - Wait for all branches to complete before proceeding
    - Cancel remaining branches on failure, follow error transition or halt
    - _Requirements: 5.8, 5.9, 2.7_

  - [x] 5.8 Write property tests for execution engine (Java/jqwik)
    - **Property 5: Condition expression evaluation correctness**
    - **Property 6: Template variable interpolation**
    - **Property 19: Execution initialization with defaults**
    - **Property 20: Parallel branch merge ordering**
    - **Property 21: End_State completes execution**
    - **Property 22: API call timeout range validation**
    - **Property 23: Response mapping with null for missing fields**
    - **Validates: Requirements 5.1, 5.3, 5.4, 5.8, 5.10, 6.2, 6.3, 6.6**

- [x] 6. Implement retry logic and error handling
  - [x] 6.1 Implement RetryManager with exponential backoff
    - Implement retry formula: `delay = baseInterval × 2^(attemptNumber - 1)`
    - Validate retry policy: maxRetries 0-10, backoffInterval 1-300 seconds
    - Record each retry attempt (attempt number, timestamp, error message) in `retry_attempts` table
    - After exhaustion: follow error transition → fallback transition → halt execution (priority order)
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [x] 6.2 Write property tests for retry logic (Java/jqwik)
    - **Property 29: Retry policy range validation**
    - **Property 30: Exponential backoff calculation**
    - **Property 31: Retry attempt recording**
    - **Validates: Requirements 9.1, 9.2, 9.5**

- [x] 7. Implement context variable management
  - [x] 7.1 Implement context variable service
    - Validate variable names: `^[a-zA-Z0-9_]{1,64}$` pattern, max 100 variables per workflow
    - Write output to context variables via state output mapping, overwriting existing values
    - Make context variables readable by subsequent states
    - Treat undefined variable references as null with warning log (include variable name and state ID)
    - _Requirements: 10.1, 10.2, 10.3, 10.5, 10.6_

  - [x] 7.2 Write property tests for context variables (Java/jqwik)
    - **Property 32: Context variable name validation**
    - **Property 33: Context variable propagation**
    - **Property 34: Undefined variable returns null with warning**
    - **Validates: Requirements 10.1, 10.2, 10.3, 10.5**

- [x] 8. Implement execution monitoring REST API
  - [x] 8.1 Implement execution controller and query endpoints
    - Create POST /api/workflows/{id}/execute endpoint (returns 202 Accepted with execution_id)
    - Create GET /api/executions endpoint with pagination (default 20, max 100)
    - Create GET /api/executions/{id} endpoint returning status, current state, elapsed time, history
    - Return 404 for non-existent execution
    - Truncate error stack traces to 5000 characters
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

  - [x] 8.2 Write property tests for execution monitoring (Java/jqwik)
    - **Property 26: Execution history completeness**
    - **Property 27: Stack trace truncation**
    - **Property 28: Execution listing pagination**
    - **Validates: Requirements 8.1, 8.2, 8.4, 8.6**

- [x] 9. Checkpoint - Ensure all backend tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement React canvas and drag-and-drop editor
  - [x] 10.1 Implement Canvas component with React Flow
    - Render workflow states as typed nodes with visual differentiation per state type
    - Implement zoom controls (25%-400%) and pan controls
    - Handle node positioning and movement
    - Maintain canvas state using CanvasState interface
    - _Requirements: 1.1, 1.6_

  - [x] 10.2 Implement Component Palette with drag-and-drop
    - List all 7 state types: API_Call, Condition, Response, Input, Wait, Parallel, End
    - Handle drag from palette to canvas to create new state instances at drop position
    - Assign unique IDs and default configurations per state type
    - _Requirements: 1.2, 2.1_

  - [x] 10.3 Implement transition drawing and validation
    - Handle edge drawing between states to create transitions
    - Reject self-loops and duplicate transitions with error messages
    - Support condition labels on transitions (true/false/error/timeout/fallback)
    - _Requirements: 1.3, 1.4_

  - [x] 10.4 Implement undo/redo system
    - Create undo/redo stack tracking all canvas operations (add/delete state, move state, add/delete transition, update config)
    - Retain at least 50 operations in undo history
    - Wire undo/redo to keyboard shortcuts (Ctrl+Z, Ctrl+Shift+Z)
    - _Requirements: 1.8_

  - [x] 10.5 Write property tests for canvas operations (TypeScript/fast-check)
    - **Property 1: Self-loop and duplicate transition rejection**
    - **Property 2: Zoom level clamping**
    - **Property 3: State deletion removes exactly associated transitions**
    - **Property 4: Undo reverses canvas operations**
    - **Validates: Requirements 1.4, 1.6, 1.7, 1.8**

- [x] 11. Implement Property Panel and state configuration UI
  - [x] 11.1 Implement dynamic Property Panel
    - Display configuration panel when a state is selected
    - Render different fields based on state type (API_Call: method/URL/headers/body/mapping; Condition: expression; Response: template; Input: prompt/variable; Wait: duration; Parallel: branches)
    - Implement inline field validation (variable name format, URL format, duration range, branch count)
    - Support retry policy configuration (maxRetries 0-10, backoffInterval 1-300s)
    - Support fallback transition configuration
    - _Requirements: 1.5, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 9.1, 9.4_

  - [x] 11.2 Implement context variable management UI
    - Allow defining up to 100 workflow-level context variables with names (1-64 alphanumeric/underscore) and default values
    - Provide autocomplete suggestions for variable references in state configuration
    - Validate variable name format inline
    - _Requirements: 10.1, 10.4, 10.6_

  - [x] 11.3 Implement state deletion with confirmation
    - Show confirmation prompt indicating the state and number of associated transitions to be removed
    - Upon confirmation, remove state and all associated transitions
    - _Requirements: 1.7_

  - [x] 11.4 Write property tests for range validations (TypeScript/fast-check)
    - **Property 7: Wait duration range validation**
    - **Property 8: Parallel branch count validation**
    - **Validates: Requirements 2.6, 2.7**

- [x] 12. Implement frontend validation service
  - [x] 12.1 Implement client-side workflow validation
    - Validate single start state (no incoming transitions)
    - Validate all non-End states have outgoing transitions
    - Validate Condition_State has exactly two transitions (true/false)
    - Validate reachability from start state
    - Validate required fields populated per state type
    - Validate empty workflow
    - Display all errors simultaneously with per-state indicators
    - Display success message when validation passes
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8_

- [x] 13. Implement workflow persistence UI and API integration
  - [x] 13.1 Implement save/load workflow functionality
    - Serialize canvas to WorkflowDefinition JSON (including positions) and POST/PUT to backend
    - Load workflow: GET from backend and render on canvas
    - Display workflow list with pagination (up to 50 items), name, description, dates, sorted by last modified
    - Implement delete with confirmation prompt
    - Handle errors gracefully: display toast messages, preserve canvas state on failure
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 13.2 Implement export and import functionality
    - Export: download JSON file with filename derived from workflow name
    - Import: file upload with size validation (max 5 MB), structure validation, render on success
    - Display specific error messages for import failures (missing fields, invalid types, file too large)
    - Preserve canvas state on import failure
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

  - [x] 13.3 Write property test for canvas state preservation (TypeScript/fast-check)
    - **Property 12: Canvas state preserved on failed operations**
    - **Validates: Requirements 3.6, 7.5**

- [x] 14. Implement execution monitoring UI
  - [x] 14.1 Implement Execution Monitor component
    - Display real-time execution status (running/completed/failed/paused)
    - Highlight current/last active state on canvas with distinct visual indicator
    - Show execution history timeline with state entry/exit times and outcomes
    - Display elapsed time and context variable values
    - List executions with pagination (default 20, max 100)
    - Handle 404 for non-existent executions
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

  - [x] 14.2 Implement workflow execution trigger
    - Add "Execute" button that calls POST /api/workflows/{id}/execute
    - Handle 202 Accepted response and navigate to execution monitor
    - Poll GET /api/executions/{id} for status updates
    - _Requirements: 5.1, 8.5_

- [x] 15. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties (using fast-check for TypeScript, jqwik for Java)
- Unit tests validate specific examples and edge cases
- Backend tasks (1-9) can be developed in parallel with frontend tasks (10-14) after shared types (1.2, 1.3) are complete
- The frontend uses React Flow for canvas rendering; the backend uses Spring Boot with JPA

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "1.4", "1.5"] },
    { "id": 1, "tasks": ["2.1", "10.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "10.2", "10.3", "10.4"] },
    { "id": 3, "tasks": ["2.4", "2.5", "3.1", "10.5"] },
    { "id": 4, "tasks": ["3.2", "3.3", "11.1", "11.2", "11.3"] },
    { "id": 5, "tasks": ["5.1", "7.1", "11.4", "12.1"] },
    { "id": 6, "tasks": ["5.2", "5.3", "5.4", "5.5", "5.6", "5.7", "7.2"] },
    { "id": 7, "tasks": ["5.8", "6.1"] },
    { "id": 8, "tasks": ["6.2", "8.1"] },
    { "id": 9, "tasks": ["8.2", "13.1", "13.2"] },
    { "id": 10, "tasks": ["13.3", "14.1", "14.2"] }
  ]
}
```
