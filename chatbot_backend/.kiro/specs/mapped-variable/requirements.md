# Requirements Document

## Introduction

This feature adds a `mapped_variable` column to the `api_response_mapping` table. The column stores the variable name under which an extracted API response value is saved in the chat session context. Later workflow nodes can reference these variables using placeholder syntax (e.g., `{{shipmentStatus}}`). This enables dynamic data flow between API calls and subsequent conversation steps.

## Glossary

- **Api_Response_Mapping**: A database entity that defines how a value is extracted from an API response using a JSON path expression and stored for later use.
- **Mapped_Variable**: A string identifier (column in `api_response_mapping`) that serves as the context key under which the extracted response value is stored.
- **Session_Context**: A JSONB column in the `chat_session` table that holds key-value pairs accumulated during a conversation workflow execution.
- **Placeholder_Service**: The service component responsible for resolving template variables (e.g., `{{variableName}}`) by looking up values in the session context.
- **Api_Config_Service**: The service component responsible for CRUD operations on API configurations including their response mappings.
- **Variable_Name_Pattern**: The regex pattern `^[a-zA-Z_][a-zA-Z0-9_]*$` defining valid variable names.

## Requirements

### Requirement 1: Schema Extension

**User Story:** As a system administrator, I want the `api_response_mapping` table to include a `mapped_variable` column, so that each response mapping has a named variable for storing extracted values.

#### Acceptance Criteria

1. THE Api_Response_Mapping table SHALL include a `mapped_variable` column of type VARCHAR(255) with a NOT NULL constraint.
2. THE Api_Response_Mapping table SHALL enforce a composite unique constraint on the combination of `api_id` and `mapped_variable`.

### Requirement 2: Variable Name Validation

**User Story:** As a developer, I want variable names to follow a strict naming pattern, so that they are safe to use as identifiers in templates and context maps.

#### Acceptance Criteria

1. WHEN a mapped_variable value is provided, THE Api_Config_Service SHALL validate that it matches the Variable_Name_Pattern (`^[a-zA-Z_][a-zA-Z0-9_]*$`).
2. IF a mapped_variable value does not match the Variable_Name_Pattern, THEN THE Api_Config_Service SHALL reject the request with a descriptive validation error message.
3. WHEN creating or updating an Api_Response_Mapping, THE Api_Config_Service SHALL verify that the mapped_variable is unique within the same api_id scope.
4. IF a duplicate mapped_variable is detected for the same api_id, THEN THE Api_Config_Service SHALL reject the request with a descriptive error message indicating the conflict.

### Requirement 3: Context Storage

**User Story:** As a workflow engine developer, I want extracted API response values to be stored in the session context using the mapped variable name, so that downstream nodes can reference them.

#### Acceptance Criteria

1. WHEN an API response is processed, THE Workflow_Execution_Service SHALL store the extracted value in the Session_Context using the mapped_variable as the key.
2. WHEN the mapped_variable key already exists in the Session_Context, THE Workflow_Execution_Service SHALL overwrite the existing value silently without raising an error.
3. WHEN multiple response mappings exist for a single API call, THE Workflow_Execution_Service SHALL store each extracted value under its respective mapped_variable key.

### Requirement 4: Placeholder Resolution

**User Story:** As a workflow designer, I want to use `{{variableName}}` syntax to reference stored API response values in subsequent nodes, so that conversation messages can include dynamic data.

#### Acceptance Criteria

1. WHEN a template contains a placeholder matching a key in the Session_Context, THE Placeholder_Service SHALL replace the placeholder with the corresponding context value.
2. WHEN a template contains a placeholder that does not match any key in the Session_Context, THE Placeholder_Service SHALL leave the placeholder unchanged in the output.

### Requirement 5: DTO and API Contract

**User Story:** As an API consumer, I want the response mapping DTO to include the `mapped_variable` field, so that I can create and view response mappings with their variable names.

#### Acceptance Criteria

1. THE ApiResponseMappingDto SHALL include a `mappedVariable` field of type String.
2. WHEN creating an API configuration with response mappings, THE Api_Config_Service SHALL require the `mappedVariable` field in each response mapping entry.
3. WHEN returning API configuration details, THE Api_Config_Service SHALL include the `mappedVariable` value in each response mapping entry of the response.
