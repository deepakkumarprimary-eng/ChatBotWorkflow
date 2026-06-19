# Requirements Document

## Introduction

This feature adds an "api" node type to the chatbot workflow engine. The API Node Processor executes external HTTP calls during workflow execution, extracts response values using JsonPath, stores extracted values in the session context, and determines next-step behavior (auto-advance, conditional branching, or interactive selection) based on the transition structure of the workflow graph.

## Glossary

- **API_Node_Processor**: The NodeProcessor implementation that handles nodes of type "api" within the workflow engine
- **ApiConfig**: An existing entity containing the external API call configuration (URL, method, headers, payload template, response mappings, timeout, retry count, credentials)
- **ApiResponseMapping**: An existing entity that maps a JsonPath expression (responsePath) to a context variable name (contextVariableName)
- **Session_Context**: A key-value map stored on the ChatSession entity that holds variables accumulated during workflow execution
- **JsonPath**: A query language for JSON documents used to extract values from API responses (library: `com.jayway.jsonpath:json-path`)
- **Workflow_Engine**: The processNodes loop in WorkflowExecutionServiceImpl that iterates through nodes, calling processors and following transitions
- **Transition**: A directed edge in the workflow graph connecting a source node to a target node, optionally carrying a condition expression
- **Condition_Expression**: A simple comparison string in the format `"variable_name operator literal_value"` used on transitions for conditional branching
- **Display_Variable**: A field on the API node JSON (`displayVariable`) that indicates the node uses interactive array selection behavior
- **Placeholder_Service**: The existing service that substitutes `{{variable}}` tokens in strings using Session_Context values

## Design Decisions & Notes

1. **JsonPath library**: Use `com.jayway.jsonpath:json-path` Maven dependency for response extraction. No custom path parsing.
2. **Array delimiter**: Array values joined with newline (`\n`) for display in chat UI. Not comma.
3. **Null handling in arrays**: Null values are skipped (not included as "null" text).
4. **No nested object extraction**: Paths always resolve to leaf/primitive values. Nested object responses are not expected.
5. **Node behavior inference**: No explicit `responseType` field needed anywhere. The presence of `displayVariable` on the node JSON indicates array/interactive behavior. Transition structure (conditions vs. no conditions) determines Type 1 vs Type 2.
6. **Button node detection**: Multiple transitions without conditions from a source = button node. Button labels come from target node `name` fields.
7. **Condition format**: Simple string expression `"variable operator value"`. No structured JSON object for conditions.
8. **No default fallback for conditions (yet)**: If no condition matches in Type 2, error out. Default transition support to be added later.
9. **User selection (Type 3)**: User replies with exact value (case-sensitive match). The selected value overwrites the newline-separated string in context.
10. **Composability**: All API node types can be followed by any node type. API nodes can chain together. The engine loop stays generic.
11. **Dependency on existing spec**: The `api-response-context-variable` spec (context_variable_name column on api_response_mapping) must be implemented first as this feature depends on it.

## Requirements

### Requirement 1: API Node Recognition

**User Story:** As a workflow engine, I want to identify API nodes in the workflow graph, so that the correct processor handles them.

#### Acceptance Criteria

1. WHEN a node with `type` equal to "api" (case-sensitive) is encountered, THE API_Node_Processor SHALL return `true` from `canHandle`
2. WHEN a node with `type` not equal to "api" is encountered, THE API_Node_Processor SHALL return `false` from `canHandle`
3. IF the node has no `type` key or the `type` value is null, THEN THE API_Node_Processor SHALL return `false` from `canHandle`

### Requirement 2: API Configuration Loading

**User Story:** As a workflow engine, I want to load the API configuration referenced by an API node, so that the processor knows which external endpoint to call.

#### Acceptance Criteria

1. WHEN the API_Node_Processor processes a node whose `config` map contains a `nodeType` value of `"api"`, THE API_Node_Processor SHALL extract the `apiConfigId` field from the node's `config` map, parse it as a Long, and retrieve the corresponding ApiConfig entity from the ApiConfigRepository
2. IF the node's `config` map does not contain an `apiConfigId` field or the field value is null, THEN THE API_Node_Processor SHALL return a NodeProcessingResult with Action CONTINUE and a ChatResponse containing an error message indicating that the API configuration reference is missing from the node
3. IF the ApiConfig entity for the given `apiConfigId` does not exist in the database, THEN THE API_Node_Processor SHALL return a NodeProcessingResult with Action CONTINUE and a ChatResponse containing an error message indicating that no API configuration was found for the specified identifier
4. IF the `apiConfigId` field value cannot be parsed as a Long, THEN THE API_Node_Processor SHALL return a NodeProcessingResult with Action CONTINUE and a ChatResponse containing an error message indicating that the API configuration identifier is invalid

### Requirement 3: Placeholder Substitution in API Request

**User Story:** As a workflow engine, I want context variables substituted into the API request URL and payload before execution, so that API calls can use data collected earlier in the conversation.

#### Acceptance Criteria

1. WHEN the API_Node_Processor prepares an API request, THE Placeholder_Service SHALL replace all `{{variable}}` tokens in the URL string with the `String.valueOf()` representation of their corresponding Session_Context values
2. WHEN the API_Node_Processor prepares an API request with a payload template, THE Placeholder_Service SHALL replace all `{{variable}}` tokens found in string values within the payload Map (including nested string values) with the `String.valueOf()` representation of their corresponding Session_Context values
3. WHEN the API_Node_Processor prepares an API request that has headers containing `{{variable}}` tokens, THE Placeholder_Service SHALL replace all `{{variable}}` tokens in each header value with the `String.valueOf()` representation of their corresponding Session_Context values
4. IF a `{{variable}}` token in the URL, payload, or header values does not have a corresponding key in the Session_Context, THEN THE Placeholder_Service SHALL leave the token unchanged in the output string
5. WHEN substitution is performed, THE Placeholder_Service SHALL match tokens using the exact pattern `{{` + variable_name + `}}` where variable_name consists of one or more characters matching `[a-zA-Z0-9_]`

### Requirement 4: External API Execution

**User Story:** As a workflow engine, I want to make HTTP calls to external APIs, so that the chatbot can retrieve real-time data during conversations.

#### Acceptance Criteria

1. WHEN the API_Node_Processor executes an API call, THE API_Node_Processor SHALL use the HTTP method specified in the ApiConfig (one of GET, POST, PUT, or DELETE)
2. WHEN the API_Node_Processor executes an API call, THE API_Node_Processor SHALL include all headers defined in the ApiConfig as HTTP request headers using each ApiHeader's `headerName` as the header name and `headerValue` as the header value
3. WHEN the API_Node_Processor executes an API call with a POST or PUT method and the ApiConfig has an associated ApiPayload, THE API_Node_Processor SHALL send the payload template with all placeholders substituted from the session context as the request body
4. WHEN the API_Node_Processor executes an API call, THE API_Node_Processor SHALL enforce the timeout specified by the `timeoutMs` field in the ApiConfig (default 5000 milliseconds) for both connection establishment and response reading
5. IF the API call fails due to a timeout, a connection error, or an HTTP response status code of 500 or above, and the `retryCount` in the ApiConfig is greater than zero, THEN THE API_Node_Processor SHALL retry the call up to `retryCount` times (default 1) with a fixed delay of 1000 milliseconds between each attempt
6. IF all retry attempts are exhausted and the API call still fails, THEN THE API_Node_Processor SHALL return a NodeProcessingResult with CONTINUE action and send an error message to the session indicating that the external API call failed
7. WHEN the API_Node_Processor receives a successful HTTP response (status code 2xx), THE API_Node_Processor SHALL extract values from the response body using each ApiResponseMapping's `responsePath` and store them in the session context under the corresponding `contextVariableName`

### Requirement 5: Response Extraction via JsonPath

**User Story:** As a workflow engine, I want to extract values from API responses using JsonPath, so that response data can be stored and used later in the conversation.

#### Acceptance Criteria

1. WHEN a successful API response (HTTP status 2xx) is received with a valid JSON body, THE API_Node_Processor SHALL evaluate each ApiResponseMapping's `responsePath` against the response JSON body using the com.jayway.jsonpath library
2. WHEN a JsonPath expression resolves to a single primitive value (String, Number, or Boolean), THE API_Node_Processor SHALL store the value converted to its string representation in the Session_Context under the mapping's `contextVariableName`
3. WHEN a JsonPath expression resolves to an array of values, THE API_Node_Processor SHALL join the non-null elements with a newline character (`\n`) and store the resulting string in the Session_Context under the mapping's `contextVariableName`
4. IF a JsonPath expression fails to evaluate against the response due to invalid path syntax or a library exception, THEN THE API_Node_Processor SHALL stop processing remaining mappings and produce an error response with a message indicating the failed `responsePath` and the `contextVariableName` it was mapped to
5. IF a JsonPath expression resolves to no match (path is valid but yields null or an empty result), THEN THE API_Node_Processor SHALL skip that mapping without storing a value and continue processing the remaining mappings
6. IF the API response body is not valid JSON, THEN THE API_Node_Processor SHALL produce an error response with a message indicating that the response body could not be parsed as JSON

### Requirement 6: Auto-Advance Behavior (Type 1)

**User Story:** As a workflow engine, I want API nodes with a single unconditional outgoing transition to auto-advance after execution, so that the conversation continues without user interaction.

#### Acceptance Criteria

1. WHEN an API node has exactly one outgoing transition whose `condition` field is absent or null, and the node does not contain a `displayVariable` field, and the API call completes successfully, THE API_Node_Processor SHALL store each extracted value in Session_Context under its corresponding `context_variable_name` key and return a CONTINUE action
2. WHEN the API_Node_Processor returns a CONTINUE action, THE Workflow_Engine SHALL advance to the target node specified by the `targetNodeId` of the single outgoing transition without sending a user-visible message or waiting for user input
3. IF the API call for a Type 1 API node fails (network error, non-2xx HTTP status, or response parsing error), THEN THE API_Node_Processor SHALL not store any extracted values in Session_Context and SHALL return a PAUSE action with an error response indicating that the API call failed
4. IF a Type 1 API node has no response mappings defined, THEN THE API_Node_Processor SHALL skip the extraction step and return a CONTINUE action

### Requirement 7: Conditional Branching Behavior (Type 2)

**User Story:** As a workflow engine, I want API nodes with multiple conditional transitions to evaluate conditions and branch accordingly, so that conversation flow can vary based on API response data.

#### Acceptance Criteria

1. WHEN the API node has multiple outgoing transitions each carrying a condition expression, THE API_Node_Processor SHALL evaluate each condition against the Session_Context in the order the transitions appear in the workflow JSON transitions array
2. THE API_Node_Processor SHALL support the comparison operators: `==`, `!=`, `<`, `>`, `<=`, `>=`
3. WHEN evaluating a condition expression, THE API_Node_Processor SHALL treat the left side as a context variable name and the right side as a literal value
4. WHEN multiple conditions are evaluated, THE API_Node_Processor SHALL select the first transition whose condition evaluates to true (first-match-wins based on array order)
5. WHEN a matching condition is found, THE API_Node_Processor SHALL return a CONTINUE action and THE Workflow_Engine SHALL advance to the target node of the matched transition
6. IF no condition evaluates to true, THEN THE API_Node_Processor SHALL produce an error response indicating no matching transition was found
7. IF the context variable referenced in a condition expression does not exist in the Session_Context, THEN THE API_Node_Processor SHALL treat the condition as false and continue evaluating subsequent transitions

### Requirement 8: Condition Expression Parsing

**User Story:** As a workflow engine, I want condition expressions parsed consistently, so that branching decisions are deterministic and correct.

#### Acceptance Criteria

1. THE API_Node_Processor SHALL parse simple condition expressions in the format `"variable_name operator literal_value"` where tokens are separated by one or more whitespace characters
2. WHEN the operator is `==` or `!=`, THE API_Node_Processor SHALL compare the context variable value and the literal as strings using case-sensitive equality
3. WHEN the operator is `<`, `>`, `<=`, or `>=`, THE API_Node_Processor SHALL attempt numeric comparison by parsing both the context variable value and the literal as doubles
4. IF a numeric comparison is attempted and either value is not a valid number, THEN THE API_Node_Processor SHALL treat the comparison result as false
5. THE API_Node_Processor SHALL support compound condition expressions using the logical connectors `and` and `or` (e.g., `"status == active and amount > 500"`, `"status == inactive or retry_count < 3"`)
6. WHEN a compound expression contains `and`, THE API_Node_Processor SHALL evaluate all sub-conditions and return true only if every sub-condition evaluates to true
7. WHEN a compound expression contains `or`, THE API_Node_Processor SHALL evaluate all sub-conditions and return true if at least one sub-condition evaluates to true
8. IF a condition expression contains both `and` and `or`, THE API_Node_Processor SHALL evaluate `and` with higher precedence than `or` (i.e., `A or B and C` is evaluated as `A or (B and C)`)
9. IF a simple condition expression does not contain exactly three tokens (variable, operator, value), THEN THE API_Node_Processor SHALL treat the condition as false

### Requirement 9: Interactive Array Selection Behavior (Type 3)

**User Story:** As a workflow engine, I want API nodes with a `displayVariable` field to present extracted array values to the user and pause for selection, so that users can choose from API-provided options.

#### Acceptance Criteria

1. WHEN the API node JSON contains a `displayVariable` field, THE API_Node_Processor SHALL treat the node as an interactive selection node by extracting and displaying the array values before pausing for user input
2. WHEN processing an interactive selection node, THE API_Node_Processor SHALL extract the array values using the response mapping whose `contextVariableName` matches the `displayVariable`
3. WHEN extracted values are available and the array contains at least one element, THE API_Node_Processor SHALL send the newline-separated list of values to the chat UI as a message
4. WHEN the list is sent, THE API_Node_Processor SHALL return a PAUSE action to wait for user selection
5. WHEN the user replies with a value that exactly matches one of the displayed array values (compared case-sensitively), THE API_Node_Processor SHALL store the selected value in the Session_Context under the `displayVariable` name, replacing the newline-separated string previously stored
6. WHEN the selected value is stored, THE Workflow_Engine SHALL advance to the next node following the outgoing transition
7. IF the user replies with a value that does not exactly match any of the displayed array values, THEN THE API_Node_Processor SHALL send an error message indicating the value is not in the list and SHALL remain paused on the same node awaiting a valid selection
8. IF no response mapping with a `contextVariableName` matching the `displayVariable` is found, THEN THE API_Node_Processor SHALL send an error message indicating the missing mapping and SHALL NOT pause for user selection
9. IF the extracted values for the `displayVariable` result in an empty array, THEN THE API_Node_Processor SHALL send an error message indicating no options are available and SHALL advance to the next node following the outgoing transition

### Requirement 10: API Call Error Handling

**User Story:** As a workflow engine, I want API call failures handled gracefully, so that users receive informative feedback instead of silent failures.

#### Acceptance Criteria

1. IF the external API returns an HTTP status code outside the 200-299 range after all retries (as defined by the `retryCount` field on the associated ApiConfig) are exhausted, THEN THE API_Node_Processor SHALL send a ChatErrorResponse to `/topic/chat/{sessionId}` containing an error message that includes the HTTP status code received
2. IF a network error or timeout (as defined by the `timeoutMs` field on the associated ApiConfig) occurs after all retries are exhausted, THEN THE API_Node_Processor SHALL send a ChatErrorResponse to `/topic/chat/{sessionId}` containing an error message indicating the API is unreachable
3. IF the API response body cannot be parsed as valid JSON, THEN THE API_Node_Processor SHALL treat the response as a failure without additional retries and SHALL send a ChatErrorResponse to `/topic/chat/{sessionId}` containing an error message indicating an invalid response format was received
4. IF the API_Node_Processor produces an error for any of the above failure conditions, THEN THE API_Node_Processor SHALL halt workflow execution at the current node and SHALL NOT advance to the next node in the workflow

### Requirement 11: Session State Persistence for Paused API Nodes

**User Story:** As a workflow engine, I want session state saved when an API node pauses for user selection, so that execution resumes correctly after the user responds.

#### Acceptance Criteria

1. WHEN the API_Node_Processor returns a PAUSE action, THE Workflow_Engine SHALL set `currentNodeId` to the API node's `id`, `currentNodeType` to `"api"` on the ChatSession entity, and persist the session including the current context map
2. WHEN the API_Node_Processor returns a PAUSE action, THE Workflow_Engine SHALL include the node's `displayVariable` value in the persisted session context so that the resume handler can identify the target context key for the user's selection
3. WHEN the user sends a message and the loaded ChatSession has `currentNodeType` equal to `"api"`, THE Workflow_Engine SHALL store the user's reply in the Session_Context under the key identified by the persisted `displayVariable` value, resolve the next node from the outgoing transition of `currentNodeId`, and continue processing from that next node
4. IF the user sends a message to a session with `currentNodeType` equal to `"api"` and no outgoing transition exists from `currentNodeId`, THEN THE Workflow_Engine SHALL mark the session status as `"completed"` and send a completion response to the chat topic

### Requirement 12: Button Node Behavior

**User Story:** As a workflow engine, I want nodes with multiple unconditional outgoing transitions to present buttons to the user, so that users can choose their conversation path.

#### Acceptance Criteria

1. WHEN a source node has two or more outgoing transitions and none of those transitions contain a `condition` field, THE Workflow_Engine SHALL treat the node as a button node
2. WHEN a button node is detected, THE Workflow_Engine SHALL collect the `name` field from each target node referenced in the transitions and send them to the chat UI as button options alongside the source node's message
3. WHEN button options are sent, THE Workflow_Engine SHALL return a PAUSE action to wait for user selection
4. WHEN the user replies with a value that exactly matches one of the target node names (case-sensitive comparison), THE Workflow_Engine SHALL advance to the matching target node and continue processing
5. IF the user replies with a value that does not match any target node name, THEN THE Workflow_Engine SHALL send an error message indicating the selection is invalid and SHALL remain paused on the same node awaiting a valid selection
