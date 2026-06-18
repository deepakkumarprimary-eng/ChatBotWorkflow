# Requirements Document

## Introduction

This feature adds a `context_variable_name` column to the `api_response_mapping` table. The column stores the variable name under which an extracted API response value (identified by `response_path`) will eventually be persisted in the chat session context. This change covers the schema extension, entity update, DTO update, validation, and CRUD handling only.

## Glossary

- **System**: The Chatbot Workflow Engine backend application
- **ApiResponseMapping**: A database entity representing a single extraction rule that maps a JSON path in an API response to a named context variable
- **Context_Variable_Name**: The alphanumeric-and-underscore identifier under which an extracted value will be stored in the session context
- **ApiConfig**: A reusable API call configuration (URL, method, headers, payload, response mappings)
- **Validator**: The component responsible for enforcing naming and uniqueness rules on input data

## Requirements

### Requirement 1: Store context variable name in response mapping

**User Story:** As a workflow designer, I want each API response mapping to specify a context variable name, so that extracted response values are stored under meaningful keys in the session context.

#### Acceptance Criteria

1. THE ApiResponseMapping entity SHALL include a `context_variable_name` field of type VARCHAR(255) that is NOT NULL
2. WHEN a new ApiResponseMapping is persisted, THE System SHALL store the `context_variable_name` value in the `context_variable_name` column of the `api_response_mapping` table
3. WHEN an ApiConfig is retrieved, THE System SHALL return the `context_variable_name` for each associated response mapping in the API response DTO
4. IF a create or update request supplies a `context_variable_name` that is null, blank, or exceeds 255 characters, THEN THE System SHALL reject the request with a validation error message indicating the field constraint violated

### Requirement 2: Validate context variable name format

**User Story:** As a workflow designer, I want the system to reject invalid variable names, so that only safe alphanumeric identifiers are used as context keys.

#### Acceptance Criteria

1. WHEN a `context_variable_name` is provided during creation or update of an ApiResponseMapping, THE Validator SHALL accept the value only if it matches the pattern `^[a-zA-Z_][a-zA-Z0-9_]*$` (starts with a letter or underscore, followed by alphanumeric characters or underscores) and does not exceed 255 characters in length
2. IF a `context_variable_name` does not match the allowed pattern or exceeds 255 characters, THEN THE System SHALL reject the request with an HTTP 400 response containing an error message indicating that the value must start with a letter or underscore and contain only alphanumeric characters and underscores
3. IF a `context_variable_name` is null, empty, or contains only whitespace, THEN THE System SHALL reject the request with an HTTP 400 response containing an error message indicating that the field is required

### Requirement 3: Enforce uniqueness of context variable name per API configuration

**User Story:** As a workflow designer, I want the system to prevent duplicate context variable names within a single API configuration, so that each mapping writes to a distinct context key.

#### Acceptance Criteria

1. THE System SHALL enforce a UNIQUE constraint on the combination of (`api_id`, `context_variable_name`) in the `api_response_mapping` table
2. IF a request to create an API configuration contains two or more response mappings with the same `context_variable_name` (compared case-sensitively), THEN THE System SHALL reject the request with an error message indicating which `context_variable_name` value is duplicated
3. IF a request to update an existing API configuration contains two or more response mappings with the same `context_variable_name` (compared case-sensitively), THEN THE System SHALL reject the request with an error message indicating which `context_variable_name` value is duplicated
4. WHEN updating response mappings for an existing ApiConfig, THE System SHALL validate uniqueness only against the new set of mappings being submitted (replace-all strategy), not against previously persisted mappings
5. IF a database-level unique constraint violation occurs on (`api_id`, `context_variable_name`) during persistence, THEN THE System SHALL return an error message indicating the duplicate context variable name and SHALL NOT partially persist the set of mappings

### Requirement 4: Schema migration for context variable name column

**User Story:** As a developer, I want the database schema updated with the new column and constraints, so that the application runs correctly on startup.

#### Acceptance Criteria

1. THE schema.sql SHALL define the `context_variable_name` column as `VARCHAR(255) NOT NULL` in the `api_response_mapping` table within the existing `CREATE TABLE IF NOT EXISTS` statement
2. THE schema.sql SHALL include a CHECK constraint ensuring `context_variable_name` matches the pattern `^[a-zA-Z_][a-zA-Z0-9_]*$` (minimum 1 character, maximum 255 characters)
3. THE schema.sql SHALL include a UNIQUE constraint on the combination of (`api_id`, `context_variable_name`) in the `api_response_mapping` table
4. WHEN the application starts against an existing database where `api_response_mapping` already exists without the `context_variable_name` column, THE schema.sql SHALL include an `ALTER TABLE` statement that adds the column, CHECK constraint, and UNIQUE constraint, using a `DO` block or equivalent guard to skip execution if the column already exists
