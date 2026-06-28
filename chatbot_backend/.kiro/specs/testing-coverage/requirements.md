# Requirements Document

## Introduction

This feature addresses critical testing gaps in the Chatbot Workflow Engine by adding integration tests with a real PostgreSQL database (via TestContainers), REST controller tests, WebSocket STOMP integration tests, ApiConfigServiceImpl validation unit tests, concurrent message handling tests, and extended property-based tests for existing services.

## Glossary

- **Test_Suite**: The complete set of automated tests for the Chatbot Workflow Engine
- **Integration_Test**: A test that exercises multiple layers of the application with real infrastructure (database, HTTP, WebSocket)
- **Controller_Test**: A test annotated with @WebMvcTest that verifies REST endpoint behavior in isolation from the full application context
- **WebSocket_Test**: A test that connects via STOMP over WebSocket and verifies real-time chat message flows
- **TestContainer**: A Docker-based PostgreSQL container managed by the Testcontainers library for integration testing
- **Property_Test**: A test using jqwik that verifies properties hold across many randomly generated inputs
- **Session**: A ChatSession entity representing an active user conversation with workflow state
- **Workflow_Engine**: The WorkflowExecutionServiceImpl that processes nodes, manages session state, and sends WebSocket responses
- **ApiConfig_Service**: The ApiConfigServiceImpl that handles CRUD operations and validation for API configurations
- **Pending_Sessions_Map**: The ConcurrentHashMap in ChatWebSocketHandler that stores session IDs between chat.init and chat.start

## Requirements

### Requirement 1: Database Integration Tests with TestContainers

**User Story:** As a developer, I want integration tests that run against a real PostgreSQL database, so that I can verify JPA mappings, schema.sql execution, and end-to-end workflow persistence are correct.

#### Acceptance Criteria

1. WHEN the Integration_Test suite starts, THE TestContainer SHALL provision a PostgreSQL container and apply the schema.sql DDL
2. WHEN a workflow is persisted via the repository layer, THE Integration_Test SHALL verify the JSONB workflow_json column stores and retrieves the complete node and transition structure
3. WHEN a ChatSession is saved with context data, THE Integration_Test SHALL verify all fields including the JSONB context column round-trip correctly through JPA
4. WHEN a full workflow execution completes (start → process nodes → complete), THE Integration_Test SHALL verify the session status transitions from "active" to "completed" in the database
5. WHEN an ApiConfig with headers, payload, and response mappings is created, THE Integration_Test SHALL verify cascade persistence stores all child entities correctly
6. WHEN an ApiConfig is deleted, THE Integration_Test SHALL verify cascade delete removes all associated headers, payload, and response mapping records

### Requirement 2: REST Controller Tests for WorkflowController

**User Story:** As a developer, I want controller-layer tests for the Workflow REST API, so that I can verify HTTP status codes, request/response serialization, and error handling without starting the full application.

#### Acceptance Criteria

1. WHEN a valid WorkflowRequest is POSTed to /api/workflows, THE Controller_Test SHALL verify a 201 Created response with the persisted workflow data
2. WHEN a GET request is sent to /api/workflows/{id} with a valid ID, THE Controller_Test SHALL verify a 200 OK response with the correct workflow JSON
3. WHEN a GET request is sent to /api/workflows/{id} with a non-existent ID, THE Controller_Test SHALL verify a 404 Not Found response
4. WHEN a valid PUT request is sent to /api/workflows/{id}, THE Controller_Test SHALL verify a 200 OK response with the updated workflow data
5. WHEN a DELETE request is sent to /api/workflows/{id} with a valid ID, THE Controller_Test SHALL verify a 204 No Content response
6. WHEN a GET request is sent to /api/workflows, THE Controller_Test SHALL verify a 200 OK response containing a JSON array of all workflows

### Requirement 3: REST Controller Tests for ApiConfigController

**User Story:** As a developer, I want controller-layer tests for the ApiConfig REST API, so that I can verify input validation, error mapping, and CRUD response behavior.

#### Acceptance Criteria

1. WHEN a valid ApiConfigRequest is POSTed to /api/api-configs, THE Controller_Test SHALL verify a 201 Created response with the persisted configuration
2. WHEN a POST request contains a missing required field (name, url, or method), THE Controller_Test SHALL verify a 400 Bad Request response with a descriptive error message
3. WHEN a POST request contains a duplicate name, THE Controller_Test SHALL verify a 409 Conflict response
4. WHEN a POST request contains an invalid HTTP method value, THE Controller_Test SHALL verify a 400 Bad Request response indicating the invalid method
5. WHEN a GET request is sent to /api/api-configs/{id} with a non-numeric ID, THE Controller_Test SHALL verify a 400 Bad Request response indicating the ID must be a positive integer
6. WHEN a GET request is sent to /api/api-configs/{id} with a non-existent numeric ID, THE Controller_Test SHALL verify a 404 Not Found response
7. WHEN a DELETE request is sent to /api/api-configs/{id} with a valid ID, THE Controller_Test SHALL verify a 204 No Content response

### Requirement 4: WebSocket STOMP Integration Tests

**User Story:** As a developer, I want integration tests for the WebSocket chat flow, so that I can verify the real-time conversation lifecycle from connection through completion.

#### Acceptance Criteria

1. WHEN a client subscribes to /app/chat.init, THE WebSocket_Test SHALL verify the response contains a non-null sessionId and a workflows array
2. WHEN a client sends a chat.start message with a valid sessionId and workflowId, THE WebSocket_Test SHALL verify responses arrive on /topic/chat/{sessionId} containing the first node content
3. WHEN a client sends a chat.message with user input during an active session, THE WebSocket_Test SHALL verify the workflow advances and the next node response is received
4. WHEN a client sends a chat.start message with an invalid sessionId, THE WebSocket_Test SHALL verify a ChatErrorResponse is pushed to the client topic
5. WHEN a workflow execution completes all nodes, THE WebSocket_Test SHALL verify the final response has the completion flag set to true
6. WHEN an error occurs during workflow processing, THE WebSocket_Test SHALL verify a ChatErrorResponse with a descriptive message is pushed to the session topic

### Requirement 5: ApiConfigServiceImpl Validation Unit Tests

**User Story:** As a developer, I want comprehensive unit tests for ApiConfigServiceImpl validation logic, so that I can verify all input validation rules are enforced correctly.

#### Acceptance Criteria

1. WHEN a method value is provided in lowercase (e.g., "post"), THE ApiConfig_Service SHALL normalize the value to uppercase before validation
2. WHEN a method value is not one of GET, POST, PUT, or DELETE, THE ApiConfig_Service SHALL throw an InvalidMethodException
3. WHEN timeoutMs is less than 1 or greater than 300000, THE ApiConfig_Service SHALL throw an IllegalArgumentException
4. WHEN retryCount is less than 0 or greater than 10, THE ApiConfig_Service SHALL throw an IllegalArgumentException
5. WHEN the headers list exceeds 50 entries, THE ApiConfig_Service SHALL throw an IllegalArgumentException
6. WHEN the response mappings list exceeds 50 entries, THE ApiConfig_Service SHALL throw an IllegalArgumentException
7. WHEN a response mapping has a context_variable_name that does not match the pattern ^[a-zA-Z_][a-zA-Z0-9_]*$, THE ApiConfig_Service SHALL throw an IllegalArgumentException
8. WHEN two response mappings share the same context_variable_name, THE ApiConfig_Service SHALL throw an IllegalArgumentException indicating the duplicate
9. WHEN a context_variable_name exceeds 255 characters, THE ApiConfig_Service SHALL throw an IllegalArgumentException

### Requirement 6: Concurrent Message Handling Tests

**User Story:** As a developer, I want tests that verify thread safety of the chat system, so that I can confirm no race conditions exist when multiple messages arrive simultaneously.

#### Acceptance Criteria

1. WHEN multiple chat.message requests arrive for the same sessionId concurrently, THE Workflow_Engine SHALL process each message without corrupting the session context
2. WHEN multiple chat.init requests arrive concurrently, THE Pending_Sessions_Map SHALL store each generated sessionId without overwriting others
3. WHEN a chat.start and a chat.message arrive nearly simultaneously for the same session, THE Workflow_Engine SHALL handle the ordering correctly without throwing exceptions
4. WHEN concurrent threads call consumePendingSession with the same sessionId, THE Pending_Sessions_Map SHALL return true for exactly one caller and false for all others

### Requirement 7: Extended Property-Based Tests for PlaceholderService

**User Story:** As a developer, I want additional property-based tests for PlaceholderService covering nested and edge-case placeholder resolution, so that I can verify correctness across a wide range of inputs.

#### Acceptance Criteria

1. FOR ALL context maps with nested placeholder references (value of one placeholder contains another placeholder pattern), THE PlaceholderService SHALL resolve placeholders to a maximum depth without infinite recursion
2. FOR ALL strings without placeholder syntax (no "{{" and "}}" pairs), THE PlaceholderService SHALL return the input string unchanged (idempotence)
3. FOR ALL context maps where a placeholder key references itself, THE PlaceholderService SHALL terminate resolution without infinite loops
4. FOR ALL resolved outputs, THE PlaceholderService SHALL produce a result containing no unresolved "{{...}}" patterns when all referenced keys exist in the context

### Requirement 8: Extended Property-Based Tests for ConditionEvaluator

**User Story:** As a developer, I want additional property-based tests for ConditionEvaluator covering compound conditions and boundary values, so that I can verify logical correctness across diverse inputs.

#### Acceptance Criteria

1. FOR ALL single comparison expressions (var == value, var != value, var < value, var > value, var <= value, var >= value), THE ConditionEvaluator SHALL produce a result consistent with Java comparison semantics on the context value
2. FOR ALL compound conditions joined by "and", THE ConditionEvaluator SHALL return true only when every sub-condition evaluates to true (conjunction property)
3. FOR ALL compound conditions joined by "or", THE ConditionEvaluator SHALL return true when at least one sub-condition evaluates to true (disjunction property)
4. FOR ALL conditions referencing a variable not present in the context, THE ConditionEvaluator SHALL evaluate the condition as false without throwing an exception

### Requirement 9: URL Validation Property Tests

**User Story:** As a developer, I want property-based tests that validate URL handling for SSRF protection readiness, so that future URL validation logic can be verified against diverse inputs.

#### Acceptance Criteria

1. FOR ALL URLs containing private/internal IP ranges (10.x.x.x, 172.16-31.x.x, 192.168.x.x, 127.x.x.x), THE Test_Suite SHALL generate test cases that can validate rejection when SSRF protection is implemented
2. FOR ALL URLs with valid public HTTP/HTTPS schemes and hosts, THE Test_Suite SHALL verify they are accepted as valid API endpoint targets
3. FOR ALL URLs containing non-HTTP schemes (file://, ftp://, gopher://), THE Test_Suite SHALL generate test cases that can validate rejection
