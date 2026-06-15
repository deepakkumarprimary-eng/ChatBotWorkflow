# Requirements Document

## Introduction

A configurable chatbot workflow execution engine built with Spring Boot (Java 17). The backend manages workflow definitions (created via React Flow on the frontend) and persists them in PostgreSQL. Phase 1 covers project setup, the workflow data model, and workflow CRUD REST APIs.

## Glossary

- **Workflow**: A directed graph of nodes and transitions that defines a chatbot conversation flow, stored as a single JSONB payload.
- **Node**: A single step in a workflow. Has a type (Text, Button, or Input) and a JSON configuration payload.
- **Transition**: A directed edge connecting a source node to a destination node, optionally with a condition.
- **Workflow_API**: The REST API layer for CRUD operations on workflow definitions.

## Requirements

### Requirement 1: Workflow Creation

**User Story:** As a frontend developer, I want to create a workflow via REST API, so that I can persist chatbot conversation flows designed in React Flow.

#### Acceptance Criteria

1. WHEN a workflow creation request is received, THE Workflow_API SHALL persist the workflow JSON payload (containing nodes and transitions) as a single JSONB column in PostgreSQL and return the created workflow with a database-generated numeric identifier. No validation of node types or required fields is performed at this stage.

### Requirement 2: Workflow Retrieval

**User Story:** As a frontend developer, I want to fetch workflows via REST API, so that I can display and edit existing conversation flows.

#### Acceptance Criteria

1. WHEN a request to retrieve a specific workflow by identifier is received, THE Workflow_API SHALL return the workflow record including its complete JSON payload (nodes and transitions).
2. WHEN a request to retrieve a workflow with a non-existent identifier is received, THE Workflow_API SHALL return a not-found error response.
3. WHEN a request to list all workflows is received, THE Workflow_API SHALL return a collection of all stored workflow records.

### Requirement 3: Workflow Update

**User Story:** As a frontend developer, I want to update an existing workflow via REST API, so that I can modify conversation flows after initial creation.

#### Acceptance Criteria

1. WHEN an update request for an existing workflow is received, THE Workflow_API SHALL replace the workflow's JSON payload with the provided data and persist the changes. No validation of the payload content is performed.
2. WHEN an update request targets a non-existent workflow, THE Workflow_API SHALL return a not-found error response.

### Requirement 4: Workflow Deletion

**User Story:** As a frontend developer, I want to delete a workflow via REST API, so that I can remove conversation flows that are no longer needed.

#### Acceptance Criteria

1. WHEN a deletion request for an existing workflow is received, THE Workflow_API SHALL remove the workflow record from PostgreSQL.
2. WHEN a deletion request targets a non-existent workflow, THE Workflow_API SHALL return a not-found error response.

### Requirement 5: Workflow Data Model

**User Story:** As a developer, I want a well-defined data model for workflows, so that the system stores and retrieves workflow definitions reliably.

#### Acceptance Criteria

1. THE Workflow_API SHALL store the entire workflow definition (nodes and transitions) as a single JSONB column in a PostgreSQL workflow table.
2. THE workflow JSONB payload SHALL contain a nodes array where each node has an identifier, a type, and a configuration object.
3. THE workflow JSONB payload SHALL contain a transitions array where each transition has a source node identifier, a destination node identifier, and an optional condition field.
4. THE Workflow_API SHALL use a single JPA entity for the workflow with the JSONB column mapped for storage and retrieval.
