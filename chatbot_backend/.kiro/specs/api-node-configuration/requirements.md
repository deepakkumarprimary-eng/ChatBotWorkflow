# Requirements Document

## Introduction

This feature adds a data model and CRUD REST APIs for managing reusable API configurations. These configurations define external HTTP API calls (URL, method, headers, payload templates, and response mappings) that can later be referenced by workflow API nodes. The scope is strictly limited to data persistence and management endpoints — no processor logic, no HTTP execution, and no workflow integration.

## Glossary

- **API_Config_Service**: The backend service layer responsible for managing API configuration entities and their child records (headers, payload, response mappings).
- **API_Config_Controller**: The REST controller that exposes CRUD endpoints for API configuration management.
- **Api_Config**: The master database entity representing a reusable API definition with URL, method, timeout, retry, and auth credentials.
- **Api_Header**: A child entity storing a single HTTP header key-value pair associated with an Api_Config.
- **Api_Payload**: A child entity storing the JSON request body template (with `<<placeholder>>` markers) for an Api_Config.
- **Api_Response_Mapping**: A child entity defining a dot-notation path to extract from the API response for an Api_Config.
- **Payload_Template**: A JSONB structure containing a mix of literal values and `<<placeholder>>` markers that will be substituted at runtime.
- **Response_Path**: A dot-notation string (e.g., `"data.status"`) indicating which field to extract from an API response.

## Requirements

### Requirement 1: Create API Configuration

**User Story:** As a workflow designer, I want to create a new API configuration with all its associated data, so that I can define reusable external API call definitions.

#### Acceptance Criteria

1. WHEN a valid create request is received, THE API_Config_Service SHALL persist an Api_Config record with name (maximum 255 characters), url (maximum 1024 characters), method, timeout_ms (integer between 1 and 300000, defaulting to 5000 if not provided), retry_count (integer between 0 and 10, defaulting to 1 if not provided), username, password, and client_id fields.
2. WHEN the create request includes headers, THE API_Config_Service SHALL persist each header as a separate Api_Header record linked to the created Api_Config, up to a maximum of 50 headers.
3. WHEN the create request includes a payload template, THE API_Config_Service SHALL persist it as an Api_Payload record linked to the created Api_Config.
4. WHEN the create request includes response mappings, THE API_Config_Service SHALL persist each mapping as a separate Api_Response_Mapping record linked to the created Api_Config, up to a maximum of 50 mappings.
5. WHEN the Api_Config is successfully created, THE API_Config_Controller SHALL return the complete configuration (including headers, payload, and response mappings) with HTTP status 201.
6. IF the create request is missing required fields (name, url, or method), THEN THE API_Config_Controller SHALL return HTTP status 400 with an error message indicating which required fields are missing.
7. IF the create request contains a name that already exists in the system, THEN THE API_Config_Controller SHALL return HTTP status 409 with an error message indicating the name is already in use.
8. IF the create request contains a timeout_ms value less than 1 or greater than 300000, or a retry_count value less than 0 or greater than 10, THEN THE API_Config_Controller SHALL return HTTP status 400 with an error message indicating the valid range for the field.

### Requirement 2: Retrieve API Configuration

**User Story:** As a workflow designer, I want to retrieve API configurations, so that I can view and verify the configured external API definitions.

#### Acceptance Criteria

1. WHEN a get-by-id request is received with a valid id, THE API_Config_Controller SHALL return the complete Api_Config including its associated headers, payload, and response mappings with HTTP status 200.
2. IF a get-by-id request is received with an id that does not exist, THEN THE API_Config_Controller SHALL return HTTP status 404 with an error message indicating the resource type and the requested id.
3. WHEN a list-all request is received, THE API_Config_Controller SHALL return all Api_Config records with their associated headers, payload, and response mappings with HTTP status 200, or an empty list if no records exist.
4. IF a get-by-id request is received with an id that is not a positive integer, THEN THE API_Config_Controller SHALL return HTTP status 400 with an error message indicating the expected id format.

### Requirement 3: Update API Configuration

**User Story:** As a workflow designer, I want to update an existing API configuration, so that I can modify API definitions as external services change.

#### Acceptance Criteria

1. WHEN a valid update request is received, THE API_Config_Service SHALL update the Api_Config record's name, url, method, timeout_ms, retry_count, username, password, and client_id fields and set the updated_at timestamp to the current time.
2. WHEN the update request includes headers, THE API_Config_Service SHALL delete all existing Api_Header records for the Api_Config and persist the new set of headers as replacement Api_Header records.
3. WHEN the update request does not include headers, THE API_Config_Service SHALL retain the existing Api_Header records for the Api_Config unchanged.
4. WHEN the update request includes a payload template, THE API_Config_Service SHALL replace the existing Api_Payload record for the Api_Config with the new payload.
5. WHEN the update request includes response mappings, THE API_Config_Service SHALL delete all existing Api_Response_Mapping records for the Api_Config and persist the new set of mappings as replacement Api_Response_Mapping records.
6. WHEN the update request does not include response mappings, THE API_Config_Service SHALL retain the existing Api_Response_Mapping records for the Api_Config unchanged.
7. WHEN the Api_Config is successfully updated, THE API_Config_Controller SHALL return the updated Api_Config including its associated headers, payload, and response mappings with HTTP status 200.
8. IF an update request is received for an id that does not exist, THEN THE API_Config_Controller SHALL return HTTP status 404 with an error message indicating that the specified Api_Config was not found.
9. IF an update request is missing required fields (name, url, or method), THEN THE API_Config_Controller SHALL return HTTP status 400 with an error message indicating which required fields are missing.

### Requirement 4: Delete API Configuration

**User Story:** As a workflow designer, I want to delete an API configuration, so that I can remove API definitions that are no longer needed.

#### Acceptance Criteria

1. WHEN a delete request is received with a valid id that exists, THE API_Config_Service SHALL delete the Api_Config record and all associated Api_Header, Api_Payload, and Api_Response_Mapping records in a single atomic operation.
2. WHEN deletion is successful, THE API_Config_Controller SHALL return HTTP status 204 with no body.
3. IF a delete request is received for an id that does not exist, THEN THE API_Config_Controller SHALL return HTTP status 404 with an error message indicating no Api_Config was found for the given id.
4. IF a delete request is received with an id that is not a positive integer, THEN THE API_Config_Controller SHALL return HTTP status 400 with an error message indicating the id format is invalid.

### Requirement 5: Database Schema for API Configuration

**User Story:** As a developer, I want the database schema properly defined, so that the API configuration data is stored with correct types, constraints, and relationships.

#### Acceptance Criteria

1. THE api_config table SHALL have columns: id (BIGSERIAL PK), name (VARCHAR 255 NOT NULL UNIQUE), url (VARCHAR 1024 NOT NULL), method (VARCHAR 10 NOT NULL CHECK IN ('GET','POST','PUT','DELETE')), timeout_ms (INTEGER DEFAULT 5000 CHECK >= 1), retry_count (INTEGER DEFAULT 1 CHECK >= 0 AND <= 10), username (VARCHAR 255), password (VARCHAR 255), client_id (VARCHAR 255), created_at (TIMESTAMP NOT NULL DEFAULT NOW), updated_at (TIMESTAMP NOT NULL DEFAULT NOW).
2. THE api_header table SHALL have columns: id (BIGSERIAL PK), api_id (BIGINT NOT NULL FK referencing api_config.id ON DELETE CASCADE), header_name (VARCHAR 255 NOT NULL), header_value (VARCHAR 1024 NOT NULL).
3. THE api_payload table SHALL have columns: id (BIGSERIAL PK), api_id (BIGINT NOT NULL FK referencing api_config.id ON DELETE CASCADE UNIQUE), payload_template (JSONB NOT NULL), created_at (TIMESTAMP NOT NULL DEFAULT NOW), updated_at (TIMESTAMP NOT NULL DEFAULT NOW).
4. THE api_response_mapping table SHALL have columns: id (BIGSERIAL PK), api_id (BIGINT NOT NULL FK referencing api_config.id ON DELETE CASCADE), response_path (VARCHAR 512 NOT NULL), created_at (TIMESTAMP NOT NULL DEFAULT NOW).
5. WHEN an Api_Config record is deleted, THE Database SHALL cascade the deletion to all associated api_header, api_payload, and api_response_mapping records.

### Requirement 6: HTTP Method Validation

**User Story:** As a workflow designer, I want the system to validate the HTTP method, so that only supported methods are stored in configurations.

#### Acceptance Criteria

1. THE API_Config_Service SHALL accept only the following method values (case-insensitive): GET, POST, PUT, DELETE.
2. THE API_Config_Service SHALL normalize the method value to uppercase before persisting.
3. IF a create or update request contains a method value other than GET, POST, PUT, or DELETE (after case normalization), THEN THE API_Config_Controller SHALL return HTTP status 400 with an error message listing the allowed methods: GET, POST, PUT, DELETE.
4. IF a create or update request contains a null or empty method value, THEN THE API_Config_Controller SHALL return HTTP status 400 with an error message indicating that method is required.

### Requirement 7: Payload Template Constraint

**User Story:** As a workflow designer, I want the system to enforce one payload template per API configuration, so that there is no ambiguity in the request body definition.

#### Acceptance Criteria

1. THE api_payload table SHALL enforce at most one record per api_id using a UNIQUE constraint on the api_id column.
2. IF a create or update request would result in more than one Api_Payload for a single Api_Config, THEN THE API_Config_Service SHALL replace the existing payload rather than create a duplicate.
3. IF an insert into the api_payload table violates the unique constraint on api_id, THEN THE Database SHALL reject the insert and return a constraint-violation error.
