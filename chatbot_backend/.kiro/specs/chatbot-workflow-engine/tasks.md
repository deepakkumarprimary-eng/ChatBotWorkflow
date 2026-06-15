# Implementation Plan: Chatbot Workflow Engine (Phase 1)

## Overview

Implements a Spring Boot REST API for CRUD operations on chatbot workflow definitions. The workflow graph (nodes + transitions) is stored as a single JSONB column in PostgreSQL. The architecture follows a layered pattern (Controller → Service → Repository) with SOLID principles applied throughout.

## Tasks

- [x] 1. Set up Spring Boot project structure and configuration
  - [x] 1.1 Create Maven project with pom.xml and required dependencies
    - Create `pom.xml` with Spring Boot 3.x parent, Java 17 compiler settings
    - Add dependencies: spring-boot-starter-web, spring-boot-starter-data-jpa, postgresql driver, lombok, hypersistence-utils (for JSONB mapping)
    - Add test dependencies: spring-boot-starter-test, testcontainers-postgresql, jqwik
    - _Requirements: 5.1, 5.4_

  - [x] 1.2 Create application.properties with PostgreSQL configuration
    - Configure datasource URL, username, password for PostgreSQL
    - Set `spring.jpa.hibernate.ddl-auto=none` (schema managed by SQL script)
    - Configure Jackson serialization settings (dates as ISO strings)
    - _Requirements: 5.1_

  - [x] 1.3 Create main application class
    - Create `com.xpressbees.chatbot.ChatbotApplication.java` with `@SpringBootApplication`
    - _Requirements: 5.4_

- [x] 2. Create database schema
  - [x] 2.1 Create SQL migration script for the workflow table
    - Create `src/main/resources/schema.sql` with the workflow table DDL
    - Define columns: `id BIGSERIAL PRIMARY KEY`, `name VARCHAR(255) NOT NULL`, `workflow_json JSONB`, `created_at TIMESTAMP NOT NULL DEFAULT NOW()`, `updated_at TIMESTAMP NOT NULL DEFAULT NOW()`
    - Configure Spring Boot to run the schema script on startup (`spring.sql.init.mode=always`)
    - _Requirements: 5.1_

- [x] 3. Implement entity and repository layers
  - [x] 3.1 Create Workflow JPA entity with JSONB mapping
    - Create `com.xpressbees.chatbot.entity.Workflow.java`
    - Map `workflow_json` column using `@Type(JsonType.class)` from hypersistence-utils
    - Add `@PrePersist` and `@PreUpdate` lifecycle callbacks for timestamps
    - Use Lombok `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`
    - _Requirements: 5.1, 5.4_

  - [x] 3.2 Create WorkflowRepository interface
    - Create `com.xpressbees.chatbot.repository.WorkflowRepository.java`
    - Extend `JpaRepository<Workflow, Long>` — no custom methods needed
    - _Requirements: 5.1, 5.4_

- [x] 4. Implement DTOs and exception handling
  - [x] 4.1 Create WorkflowRequest and WorkflowResponse DTOs
    - Create `com.xpressbees.chatbot.dto.WorkflowRequest.java` with fields: `name` (String), `workflowJson` (Object)
    - Create `com.xpressbees.chatbot.dto.WorkflowResponse.java` with fields: `id` (Long), `name` (String), `workflowJson` (Object), `createdAt` (LocalDateTime), `updatedAt` (LocalDateTime)
    - Use Lombok `@Data` for both
    - _Requirements: 1.1, 2.1, 2.3, 3.1_

  - [x] 4.2 Create WorkflowNotFoundException and GlobalExceptionHandler
    - Create `com.xpressbees.chatbot.exception.WorkflowNotFoundException.java` that extends `RuntimeException` and holds the workflow ID
    - Create `com.xpressbees.chatbot.exception.GlobalExceptionHandler.java` with `@RestControllerAdvice`
    - Handle `WorkflowNotFoundException` → return 404 with structured JSON body `{ "error": "Workflow not found", "id": "<id>" }`
    - _Requirements: 2.2, 3.2, 4.2_

- [x] 5. Implement service layer
  - [x] 5.1 Create WorkflowService interface
    - Create `com.xpressbees.chatbot.service.WorkflowService.java`
    - Define methods: `create(WorkflowRequest)`, `getById(Long)`, `listAll()`, `update(Long, WorkflowRequest)`, `delete(Long)`
    - _Requirements: 1.1, 2.1, 2.3, 3.1, 4.1_

  - [x] 5.2 Create WorkflowServiceImpl
    - Create `com.xpressbees.chatbot.service.WorkflowServiceImpl.java` with `@Service`
    - Inject `WorkflowRepository` via constructor injection
    - Implement `create`: map DTO to entity, save, map entity to response DTO
    - Implement `getById`: find by ID, throw `WorkflowNotFoundException` if not found, map to response
    - Implement `listAll`: find all, map each to response DTO
    - Implement `update`: find by ID (throw if missing), update fields, save, map to response
    - Implement `delete`: find by ID (throw if missing), delete entity
    - _Requirements: 1.1, 2.1, 2.2, 2.3, 3.1, 3.2, 4.1, 4.2_

- [x] 6. Checkpoint - Verify compilation and service layer
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement controller layer
  - [x] 7.1 Create WorkflowController with all CRUD endpoints
    - Create `com.xpressbees.chatbot.controller.WorkflowController.java` with `@RestController` and `@RequestMapping("/api/workflows")`
    - Inject `WorkflowService` interface via constructor
    - `POST /` → call `service.create()`, return 201 Created with response body
    - `GET /{id}` → call `service.getById()`, return 200 OK
    - `GET /` → call `service.listAll()`, return 200 OK
    - `PUT /{id}` → call `service.update()`, return 200 OK
    - `DELETE /{id}` → call `service.delete()`, return 204 No Content
    - _Requirements: 1.1, 2.1, 2.2, 2.3, 3.1, 3.2, 4.1, 4.2_

- [ ] 8. Write unit tests
  - [ ]* 8.1 Write unit tests for WorkflowServiceImpl
    - Mock `WorkflowRepository` using Mockito
    - Test create: verify entity is saved and response mapped correctly
    - Test getById: verify found case returns response, missing case throws `WorkflowNotFoundException`
    - Test listAll: verify all entities are mapped to responses
    - Test update: verify fields are replaced on existing entity, missing case throws exception
    - Test delete: verify repository delete is called, missing case throws exception
    - _Requirements: 1.1, 2.1, 2.2, 2.3, 3.1, 3.2, 4.1, 4.2_

  - [ ]* 8.2 Write unit tests for WorkflowController using @WebMvcTest
    - Mock `WorkflowService`
    - Test POST returns 201 with correct response body
    - Test GET /{id} returns 200 with workflow data
    - Test GET / returns 200 with list
    - Test PUT /{id} returns 200 with updated data
    - Test DELETE /{id} returns 204 with no body
    - Test GET/PUT/DELETE with non-existent ID returns 404
    - _Requirements: 1.1, 2.1, 2.2, 2.3, 3.1, 3.2, 4.1, 4.2_

- [ ] 9. Write property-based tests with jqwik
  - [ ]* 9.1 Write property test for workflow persistence round-trip
    - **Property 1: Workflow persistence round-trip**
    - Use jqwik with `@SpringBootTest` and Testcontainers PostgreSQL
    - Generate random workflow JSON payloads (random nodes array, random transitions array)
    - POST the payload, then GET by returned ID, assert `workflowJson` is deeply equal
    - **Validates: Requirements 1.1, 2.1, 5.1, 5.2, 5.3**

  - [ ]* 9.2 Write property test for update replaces payload completely
    - **Property 2: Update replaces payload completely**
    - Generate two random payloads, create workflow with first, PUT with second
    - GET and assert response matches second payload, not first
    - **Validates: Requirements 3.1**

  - [ ]* 9.3 Write property test for delete removes record
    - **Property 3: Delete removes record**
    - Create workflow with random payload, DELETE it, GET should return 404
    - **Validates: Requirements 4.1**

  - [ ]* 9.4 Write property test for list returns all created workflows
    - **Property 4: List returns all created workflows**
    - Create N workflows (random N ≥ 0), GET list, assert all IDs present with correct payloads
    - **Validates: Requirements 2.3**

- [x] 10. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests use jqwik with Testcontainers for full-stack verification against a real PostgreSQL instance
- Unit tests use Mockito and @WebMvcTest for fast, isolated testing
- The hypersistence-utils library (io.hypersistence:hypersistence-utils-hibernate-63) provides the `JsonType` for JSONB mapping

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["2.1", "3.1"] },
    { "id": 3, "tasks": ["3.2", "4.1", "4.2"] },
    { "id": 4, "tasks": ["5.1"] },
    { "id": 5, "tasks": ["5.2"] },
    { "id": 6, "tasks": ["7.1"] },
    { "id": 7, "tasks": ["8.1", "8.2"] },
    { "id": 8, "tasks": ["9.1", "9.2", "9.3", "9.4"] }
  ]
}
```
