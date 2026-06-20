# Requirements Document

## Introduction

This feature adds per-node validation rules to input nodes in the chatbot workflow engine. Currently, input nodes accept any user reply without validation, storing it immediately and advancing the workflow. This feature enables workflow designers to define validation constraints (regex pattern, min/max length, numeric-only, required) on each input node's configuration. When user input fails validation, the backend re-prompts the user with a per-rule error message instead of storing the invalid value and continuing execution.

This is a backend-only implementation. Validation is enforced in `handleInputNodeResume()` within `WorkflowExecutionServiceImpl` before storing user input in the session context. The validation configuration lives inside the node's `config` map in the workflow JSONB — no separate database table is required. The backend reads validation rules from the workflow definition at runtime.

## Glossary

- **Input_Node**: A workflow node of type `state` with config `nodeType: "input"` that pauses execution and waits for user reply via WebSocket
- **Validation_Rule**: A constraint defined in an input node's `validation` object within its config map that specifies how user input must be validated before acceptance
- **Validation_Service**: The backend component responsible for evaluating user input against the validation rules defined on an input node
- **Workflow_Engine**: The `WorkflowExecutionServiceImpl` service that orchestrates node processing, session state management, and message routing
- **Session_Context**: The JSONB map stored on the `ChatSession` entity that holds workflow variables and user-provided values
- **Error_Messages_Object**: An optional object within the `validation` configuration where keys correspond to validation rule names and values are custom error message strings for each rule
- **Node_Config**: The `config` map within a workflow node's JSON definition, containing node-specific settings like `variableName`, `nodeType`, and `validation`

## Requirements

### Requirement 1: Validation Rule Definition

**User Story:** As a workflow designer, I want to define validation rules on input nodes, so that user input is constrained to acceptable values.

#### Acceptance Criteria

1. THE Input_Node config map SHALL support an optional `validation` object containing validation rule properties
2. WHEN a `validation` object is present in the Node_Config, THE Workflow_Engine SHALL recognize the following rule properties: `required` (boolean), `minLength` (integer, range 0 to 10000), `maxLength` (integer, range 1 to 10000, must be greater than or equal to `minLength` when both are present), `pattern` (regex string, maximum 500 characters), `numericOnly` (boolean)
3. WHEN a `validation` object is present in the Node_Config, THE Workflow_Engine SHALL recognize an optional `errorMessages` object where each key corresponds to a validation rule name (`required`, `minLength`, `maxLength`, `numericOnly`, `pattern`) and each value is a custom error message string (maximum 500 characters) for that specific rule
4. WHERE the `validation` object is absent from the Node_Config, THE Input_Node SHALL accept all non-empty user input without validation (preserving current behavior)
5. WHEN a user submits input to an Input_Node that has a `validation` object, THE Workflow_Engine SHALL evaluate all specified rule properties against the input (combined with AND logic — all rules must pass) and store the input in the session context only if every rule passes
6. IF user input fails one or more validation rules on an Input_Node, THEN THE Workflow_Engine SHALL send an error response to the user via WebSocket containing the per-rule error message from the `errorMessages` object if defined for the failing rule, or a hardcoded default message for that rule if the key is missing or the `errorMessages` object is absent, and SHALL remain paused on the same Input_Node awaiting a new submission
7. THE Workflow_Engine SHALL read the `validation` object from the workflow JSONB node config at runtime without requiring a separate database table or schema migration

### Requirement 2: Required Field Validation

**User Story:** As a workflow designer, I want to mark input nodes as required, so that users cannot submit empty or whitespace-only replies.

#### Acceptance Criteria

1. WHEN `required` is set to `true` in the input node's Validation_Rule AND the user submits a reply that is null, empty (zero-length), or contains only whitespace characters, THEN THE Validation_Service SHALL reject the input and send a ChatErrorResponse to `/topic/chat/{sessionId}` containing the `errorMessages.required` message if defined or the default message "This field is required", and the session shall remain paused at the current input node without storing the reply in Session_Context
2. WHEN `required` is set to `true` in the input node's Validation_Rule AND the user submits a reply containing at least one non-whitespace character, THEN THE Validation_Service SHALL accept the input and allow subsequent validation rules to be evaluated
3. WHERE `required` is not specified or is set to `false` in the input node's Validation_Rule, THE Validation_Service SHALL accept the reply regardless of content (including empty or whitespace-only) and allow subsequent validation rules to be evaluated
4. IF the Validation_Rule object is present in the input node config but contains an unrecognized or malformed `required` field value (neither boolean `true` nor `false`), THEN THE Validation_Service SHALL treat the field as not required and accept the input

### Requirement 3: Length Validation

**User Story:** As a workflow designer, I want to enforce minimum and maximum length constraints on user input, so that input conforms to expected size boundaries.

#### Acceptance Criteria

1. IF `minLength` is defined in the Validation_Rule AND the trimmed user input character count is less than the `minLength` value, THEN THE Validation_Service SHALL reject the input and return the `errorMessages.minLength` message if defined or a default message indicating the input is too short and specifying the minimum required length
2. IF `maxLength` is defined in the Validation_Rule AND the trimmed user input character count is greater than the `maxLength` value, THEN THE Validation_Service SHALL reject the input and return the `errorMessages.maxLength` message if defined or a default message indicating the input is too long and specifying the maximum allowed length
3. IF `minLength` is defined in the Validation_Rule AND the trimmed user input character count is equal to or greater than the `minLength` value, THEN THE Validation_Service SHALL accept the input as passing the minimum length check
4. IF `maxLength` is defined in the Validation_Rule AND the trimmed user input character count is equal to or less than the `maxLength` value, THEN THE Validation_Service SHALL accept the input as passing the maximum length check
5. WHERE both `minLength` and `maxLength` are defined, THE Validation_Service SHALL evaluate both constraints against the trimmed user input and reject the input if either constraint is violated
6. IF neither `minLength` nor `maxLength` is defined in the Validation_Rule, THEN THE Validation_Service SHALL accept the input without applying length constraints

### Requirement 4: Pattern Validation

**User Story:** As a workflow designer, I want to enforce a regex pattern on user input, so that input matches a specific format (e.g., phone numbers, email addresses).

#### Acceptance Criteria

1. WHEN `pattern` is defined in the Validation_Rule, THE Validation_Service SHALL compile the pattern as a Java regular expression using `java.util.regex.Pattern` and apply `Matcher.matches()` against the complete, untrimmed user input string
2. IF the user input does not match the defined `pattern`, THEN THE Validation_Service SHALL reject the input by sending the `errorMessages.pattern` message if defined or a default message indicating the expected format, SHALL NOT store the input in the session context, and SHALL keep the session paused on the current input node awaiting a new input
3. IF the `pattern` value is not a valid Java regular expression (i.e., `Pattern.compile()` throws `PatternSyntaxException`), THEN THE Validation_Service SHALL log a warning containing the invalid pattern string and the node identifier, accept the input, and continue workflow execution (fail-open behavior)

### Requirement 5: Numeric-Only Validation

**User Story:** As a workflow designer, I want to restrict input to numeric characters only, so that users provide valid numeric data (e.g., phone numbers, IDs).

#### Acceptance Criteria

1. WHEN `numericOnly` is set to `true` in the Validation_Rule AND the user input contains any character that is not an ASCII digit (0-9), THEN THE Validation_Service SHALL reject the input and return the `errorMessages.numericOnly` message if defined or a default message indicating only numeric characters are allowed
2. THE Validation_Service SHALL treat only ASCII digits 0-9 as valid numeric characters when `numericOnly` is `true` — decimal points, negative signs, and Unicode digit characters SHALL be treated as invalid
3. WHEN `numericOnly` is set to `true` AND the trimmed user input is empty, THEN THE Validation_Service SHALL accept the input (empty input is handled by the `required` rule, not `numericOnly`)
4. WHERE `numericOnly` is not specified or is set to `false`, THE Validation_Service SHALL not apply numeric validation

### Requirement 6: Validation Error Response

**User Story:** As a chatbot user, I want to receive a clear error message when my input is invalid, so that I know what to correct and can try again.

#### Acceptance Criteria

1. WHEN user input fails one or more Validation_Rules defined on the current Input_Node, THE Workflow_Engine SHALL send a ChatErrorResponse to the user via the WebSocket topic `/topic/chat/{sessionId}` within 2 seconds of receiving the input
2. WHEN the `errorMessages` object is present in the `validation` configuration AND contains a key matching the failing rule name, THE Workflow_Engine SHALL use that key's value as the `error` field in the ChatErrorResponse
3. IF the `errorMessages` object is absent from the `validation` configuration OR does not contain a key for the failing rule, THEN THE Workflow_Engine SHALL use a hardcoded default error message specific to the rule type (e.g., "This field is required", "Input must be at least N characters", "Input must not exceed N characters", "Only numeric characters are allowed", "Input must match the required format")
4. WHEN user input fails validation, THE Workflow_Engine SHALL keep the session `status` as its current value, retain `currentNodeId` at the current Input_Node, and retain `currentNodeType` as "input", allowing the user to re-submit without losing previously collected session context
5. IF multiple Validation_Rules are defined on the current Input_Node, THEN THE Workflow_Engine SHALL evaluate them in the fixed order and return the error message for the first failing rule only (short-circuit evaluation)

### Requirement 7: Validation Rule Composition

**User Story:** As a workflow designer, I want to define multiple validation rules on a single input node, so that input must satisfy all constraints simultaneously.

#### Acceptance Criteria

1. WHEN multiple validation rules are defined on an Input_Node, THE Validation_Service SHALL evaluate all applicable rules against the user input in the fixed order: required, minLength, maxLength, numericOnly, pattern
2. WHEN user input fails a validation rule, THE Validation_Service SHALL stop evaluation immediately (short-circuit) and report that failed rule's error message to the user without evaluating subsequent rules
3. WHEN user input passes all defined validation rules for the Input_Node, THE Validation_Service SHALL accept the input and allow workflow execution to proceed to the next node
4. IF a validation rule property is absent from the Input_Node's validation configuration, THEN THE Validation_Service SHALL skip that rule and continue evaluating the next rule in the defined order
5. THE Validation_Service SHALL support all 5 validation rules simultaneously on a single Input_Node (required, minLength, maxLength, numericOnly, pattern) — the `numericOnly` and `pattern` rules CAN coexist and both SHALL be evaluated when both are present
6. THE Workflow_Engine SHALL NOT reject or invalidate workflow definitions that define both `numericOnly` and `pattern` on the same Input_Node — all rule combinations are permitted
