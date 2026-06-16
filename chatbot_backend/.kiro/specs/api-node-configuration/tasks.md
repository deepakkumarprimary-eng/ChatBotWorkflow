# Implementation Plan: API Node Configuration

## Overview

Implement a complete CRUD system for managing reusable API configurations. This adds a new `ApiConfig` domain with child entities (`ApiHeader`, `ApiPayload`, `ApiResponseMapping`) following the project's existing layered architecture (Controller → Service → Repository → Entity). The implementation uses JPA with PostgreSQL, Lombok, and constructor injection matching existing project conventions.

## Tasks

- [x] 1. Database schema and entity layer
  - [x] 1.1 Add API configuration tables to schema.sql
    - Append DDL for `api_config`, `api_header`, `api_payload`, and `api_response_mapping` tables
    - Include CHECK constraints, UNIQUE constraint on `api_config.name`, foreign keys with ON DELETE CASCADE
    - Add indexes on foreign key columns (`api_id`)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 7.1, 7.3_

  - [x] 1.2 Create ApiConfig entity
    - Create `entity/ApiConfig.java` with JPA annotations, Lombok `@Data`/`@NoArgsConstructor`/`@AllArgsConstructor`
    - Define `@OneToMany` for headers and response mappings, `@OneToOne` for payload with cascade ALL and orphanRemoval
    - Add `@PrePersist`/`@PreUpdate` lifecycle callbacks for timestamps
    - _Requirements: 5.1, 1.1_

  - [x] 1.3 Create ApiHeader entity
    - Create `entity/ApiHeader.java` with `@ManyToOne(fetch = LAZY)` back-reference to ApiConfig
    - Map columns: `id`, `api_id`, `header_name`, `header_value`
    - _Requirements: 5.2_

  - [x] 1.4 Create ApiPayload entity
    - Create `entity/ApiPayload.java` with `@OneToOne(fetch = LAZY)` back-reference to ApiConfig
    - Use `@Type(JsonType.class)` for the `payload_template` JSONB column
    - Add `@PrePersist`/`@PreUpdate` for timestamps
    - _Requirements: 5.3, 7.1_

  - [x] 1.5 Create ApiResponseMapping entity
    - Create `entity/ApiResponseMapping.java` with `@ManyToOne(fetch = LAZY)` back-reference to ApiConfig
    - Map columns: `id`, `api_id`, `response_path`, `created_at`
    - _Requirements: 5.4_

- [x] 2. Repository and DTO layer
  - [x] 2.1 Create repository interfaces
    - Create `repository/ApiConfigRepository.java` extending `JpaRepository<ApiConfig, Long>`
    - Add `boolean existsByName(String name)` and `boolean existsByNameAndIdNot(String name, Long id)` query methods
    - Create `repository/ApiHeaderRepository.java` with `void deleteAllByApiConfig(ApiConfig apiConfig)`
    - Create `repository/ApiPayloadRepository.java` with `Optional<ApiPayload> findByApiConfig(ApiConfig apiConfig)` and `void deleteByApiConfig(ApiConfig apiConfig)`
    - Create `repository/ApiResponseMappingRepository.java` with `void deleteAllByApiConfig(ApiConfig apiConfig)`
    - _Requirements: 1.1, 1.7, 3.2, 3.5, 4.1_

  - [x] 2.2 Create DTO classes
    - Create `dto/ApiConfigRequest.java` with fields: name, url, method, timeoutMs, retryCount, username, password, clientId, headers (List<ApiHeaderDto>), payloadTemplate (Object), responseMappings (List<ApiResponseMappingDto>)
    - Create `dto/ApiHeaderDto.java` with headerName and headerValue
    - Create `dto/ApiResponseMappingDto.java` with responsePath
    - Create `dto/ApiConfigResponse.java` with all fields plus id, createdAt, updatedAt
    - Use Lombok `@Data` for all DTOs
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1_

- [x] 3. Exception and validation layer
  - [x] 3.1 Create custom exception classes
    - Create `exception/ApiConfigNotFoundException.java` extending RuntimeException, holding the requested ID
    - Create `exception/DuplicateApiConfigNameException.java` extending RuntimeException, holding the conflicting name
    - Create `exception/InvalidMethodException.java` extending RuntimeException, holding the invalid method value
    - _Requirements: 1.6, 1.7, 2.2, 3.8, 6.3_

  - [x] 3.2 Register exception handlers in GlobalExceptionHandler
    - Add `@ExceptionHandler` for `ApiConfigNotFoundException` returning 404 with `{"error": "ApiConfig not found", "id": ...}`
    - Add `@ExceptionHandler` for `DuplicateApiConfigNameException` returning 409 with `{"error": "Conflict", "message": ...}`
    - Add `@ExceptionHandler` for `InvalidMethodException` returning 400 with `{"error": "Validation failed", "message": ...}`
    - _Requirements: 1.6, 1.7, 1.8, 2.2, 2.4, 3.8, 3.9, 4.3, 4.4, 6.3, 6.4_

- [x] 4. Checkpoint - Ensure schema and foundation layers compile
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Service layer implementation
  - [x] 5.1 Create ApiConfigService interface
    - Define methods: `create(ApiConfigRequest)`, `getById(Long)`, `listAll()`, `update(Long, ApiConfigRequest)`, `delete(Long)`
    - All methods return `ApiConfigResponse` except `delete` (void)
    - _Requirements: 1.1, 2.1, 2.3, 3.1, 4.1_

  - [x] 5.2 Implement ApiConfigServiceImpl - create and retrieve
    - Implement `create`: validate required fields (name, url, method), validate numeric ranges, normalize method to uppercase, check duplicate name, persist parent + children, return mapped response
    - Implement `getById`: find by ID or throw `ApiConfigNotFoundException`, map to response including children
    - Implement `listAll`: fetch all configs with children, map to response list
    - Apply defaults: timeoutMs=5000 when null, retryCount=1 when null
    - Enforce max 50 headers and max 50 response mappings
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 2.1, 2.2, 2.3, 2.4, 6.1, 6.2, 6.3, 6.4_

  - [x] 5.3 Implement ApiConfigServiceImpl - update and delete
    - Implement `update`: find existing or throw 404, validate required fields, normalize method, check duplicate name (excluding self), replace-all strategy for headers/responseMappings when present in request, retain when absent, replace payload when present
    - Implement `delete`: find by ID or throw 404, delete entity (cascade handles children)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 4.1, 4.2, 4.3, 4.4, 7.2_

  - [ ]* 5.4 Write property test: Create Round-Trip (Property 1)
    - **Property 1: Create Round-Trip**
    - Generate random valid ApiConfigRequest objects and verify that create + getById returns matching fields with correct defaults
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 2.1**

  - [ ]* 5.5 Write property test: Update Replace (Property 2)
    - **Property 2: Update Replace**
    - Create a config, then update with new headers/responseMappings/payload, verify only new children present
    - **Validates: Requirements 3.1, 3.2, 3.4, 3.5**

  - [ ]* 5.6 Write property test: Update Retention (Property 3)
    - **Property 3: Update Retention**
    - Create a config with children, update omitting headers/responseMappings fields, verify children unchanged
    - **Validates: Requirements 3.3, 3.6**

  - [ ]* 5.7 Write property test: Required Field Validation (Property 4)
    - **Property 4: Required Field Validation**
    - Generate requests missing name, url, or method and verify rejection with 400 status
    - **Validates: Requirements 1.6, 3.9, 6.4**

  - [ ]* 5.8 Write property test: Numeric Range Validation (Property 5)
    - **Property 5: Numeric Range Validation**
    - Generate requests with timeoutMs outside [1, 300000] or retryCount outside [0, 10] and verify rejection
    - **Validates: Requirements 1.8**

  - [ ]* 5.9 Write property test: Method Normalization (Property 7)
    - **Property 7: Method Normalization**
    - Generate valid method strings in random case combinations and verify persisted/returned value is uppercase
    - **Validates: Requirements 6.1, 6.2**

  - [ ]* 5.10 Write property test: Invalid Method Rejection (Property 8)
    - **Property 8: Invalid Method Rejection**
    - Generate non-GET/POST/PUT/DELETE strings and verify rejection with 400
    - **Validates: Requirements 6.3**

- [x] 6. Checkpoint - Ensure service layer compiles and property tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Controller layer
  - [x] 7.1 Create ApiConfigController
    - Implement REST endpoints at `/api/api-configs`: POST (201), GET by ID (200), GET all (200), PUT (200), DELETE (204)
    - Use constructor injection for ApiConfigService
    - Follow same patterns as existing WorkflowController
    - _Requirements: 1.5, 2.1, 2.3, 3.7, 4.2_

  - [ ]* 7.2 Write property test: Cascade Delete (Property 6)
    - **Property 6: Cascade Delete**
    - Create configs with children, delete parent, verify 404 on get and no orphaned children
    - **Validates: Requirements 4.1, 5.5**

  - [ ]* 7.3 Write property test: List Count Invariant (Property 9)
    - **Property 9: List Count Invariant**
    - Create N configs with unique names, verify listAll contains at least N items with all created IDs
    - **Validates: Requirements 2.3**

  - [ ]* 7.4 Write property test: Payload Uniqueness (Property 10)
    - **Property 10: Payload Uniqueness**
    - Create/update configs with payloads multiple times, verify at most one payload per config
    - **Validates: Requirements 7.1, 7.2**

- [x] 8. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties defined in the design document
- Unit tests validate specific examples and edge cases
- The project uses jqwik 1.8.2 for property-based testing (already in pom.xml)
- All property tests should go in `src/test/java/com/xpressbees/chatbot/service/ApiConfigServicePropertyTest.java`
- Follow constructor injection and Lombok conventions matching existing code

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.2", "3.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "1.4", "1.5"] },
    { "id": 2, "tasks": ["2.1", "3.2"] },
    { "id": 3, "tasks": ["5.1"] },
    { "id": 4, "tasks": ["5.2", "5.3"] },
    { "id": 5, "tasks": ["5.4", "5.5", "5.6", "5.7", "5.8", "5.9", "5.10"] },
    { "id": 6, "tasks": ["7.1"] },
    { "id": 7, "tasks": ["7.2", "7.3", "7.4"] }
  ]
}
```
