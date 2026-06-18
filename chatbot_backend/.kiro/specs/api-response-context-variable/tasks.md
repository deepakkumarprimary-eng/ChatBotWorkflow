# Implementation Plan: API Response Context Variable

## Overview

Add a `context_variable_name` column to the `api_response_mapping` table and propagate it through the entity, DTO, service validation, and schema layers. The implementation follows the existing layered architecture and replace-all update strategy.

## Tasks

- [x] 1. Update database schema and entity layer
  - [x] 1.1 Add `context_variable_name` column to `schema.sql`
    - Add `context_variable_name VARCHAR(255) NOT NULL` to the existing `CREATE TABLE IF NOT EXISTS api_response_mapping` statement
    - Add a CHECK constraint ensuring the value matches `^[a-zA-Z_][a-zA-Z0-9_]*$`
    - Add a UNIQUE constraint on `(api_id, context_variable_name)`
    - Add a guarded `DO $$ ... END $$` block that conditionally adds the column, CHECK, and UNIQUE constraints via ALTER TABLE for existing databases where the column is absent
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [x] 1.2 Add `contextVariableName` field to `ApiResponseMapping` entity
    - Add `@Column(name = "context_variable_name", nullable = false, length = 255)` field
    - Add `@UniqueConstraint(columnNames = {"api_id", "context_variable_name"})` to the `@Table` annotation
    - _Requirements: 1.1, 1.2_

  - [x] 1.3 Add `contextVariableName` field to `ApiResponseMappingDto`
    - Add `private String contextVariableName` field to the DTO class
    - _Requirements: 1.3_

- [x] 2. Implement validation logic in service layer
  - [x] 2.1 Add `validateResponseMappings` method to `ApiConfigServiceImpl`
    - Create a private method `validateResponseMappings(List<ApiResponseMappingDto> mappings)` that:
      - Checks each mapping's `contextVariableName` is not null, blank, or whitespace-only; throws `IllegalArgumentException` with message `"Field 'context_variable_name' is required"`
      - Validates each `contextVariableName` matches regex `^[a-zA-Z_][a-zA-Z0-9_]*$`; throws with message `"context_variable_name must start with a letter or underscore and contain only alphanumeric characters and underscores"`
      - Validates length ≤ 255; throws with message `"context_variable_name must not exceed 255 characters"`
      - Collects all names and detects case-sensitive duplicates; throws with message `"Duplicate context_variable_name: '{value}'"`
    - Call `validateResponseMappings` in both `create()` and `update()` methods after `validateCollectionSizes`, before persisting response mappings
    - _Requirements: 1.4, 2.1, 2.2, 2.3, 3.2, 3.3, 3.4_

  - [x] 2.2 Update `create()` and `update()` mapping logic to include `contextVariableName`
    - In the response mapping persistence block of `create()`, set `mapping.setContextVariableName(dto.getContextVariableName())` when building `ApiResponseMapping` entities
    - In the response mapping persistence block of `update()`, set `mapping.setContextVariableName(dto.getContextVariableName())` when building replacement entities
    - In `mapToResponse()`, set `dto.setContextVariableName(m.getContextVariableName())` when building `ApiResponseMappingDto` list
    - _Requirements: 1.2, 1.3, 3.4_

- [x] 3. Add `DataIntegrityViolationException` handler for database constraint violations
  - [x] 3.1 Add exception handler in `GlobalExceptionHandler`
    - Add `@ExceptionHandler(DataIntegrityViolationException.class)` method
    - Inspect the exception/constraint name to detect `uq_api_response_mapping_api_id_ctx_var` constraint violations
    - Return HTTP 400 with body `{"error": "Validation failed", "message": "Duplicate context_variable_name: '{value}' for this API configuration"}`
    - For unrecognized constraint violations, return a generic 400 with the constraint message
    - _Requirements: 3.1, 3.5_

- [x] 4. Checkpoint - Verify schema and CRUD flow
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Write tests for context variable name validation and persistence
  - [ ]* 5.1 Write property test: valid context variable names are accepted
    - **Property 2: Valid context variable names are accepted**
    - **Validates: Requirements 2.1**
    - Create test class `ApiResponseMappingValidationProperties.java` in `src/test/java/com/xpressbees/chatbot/`
    - Use jqwik to generate strings matching `[a-zA-Z_][a-zA-Z0-9_]{0,254}`
    - Assert validation passes without exceptions
    - Annotate with `@Property(tries = 100)`

  - [ ]* 5.2 Write property test: invalid context variable names are rejected
    - **Property 3: Invalid context variable names are rejected**
    - **Validates: Requirements 1.4, 2.2, 2.3**
    - Generate strings that start with digits, contain special characters, are whitespace-only, empty, or exceed 255 characters
    - Assert `IllegalArgumentException` is thrown for each case
    - Annotate with `@Property(tries = 100)`

  - [ ]* 5.3 Write property test: duplicate names within a request are rejected
    - **Property 4: Duplicate context variable names within a request are rejected**
    - **Validates: Requirements 3.1, 3.2, 3.3**
    - Generate lists of valid names with at least one duplicate inserted at random positions
    - Assert `IllegalArgumentException` is thrown with duplicate-indicating message
    - Annotate with `@Property(tries = 100)`

  - [ ]* 5.4 Write unit tests for edge cases and error messages
    - Test exact error message wording for null, blank, invalid format, length exceeded, and duplicate scenarios
    - Test that replace-all update strategy doesn't conflict with reused names across updates
    - Test `DataIntegrityViolationException` handler returns user-friendly message
    - _Requirements: 1.4, 2.2, 2.3, 3.2, 3.3, 3.5_

- [x] 6. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The implementation language is Java 17 with Spring Boot 3.3.5, consistent with the existing codebase
- jqwik 1.8.2 is already available in `pom.xml` for property-based tests

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.3"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["2.1", "3.1"] },
    { "id": 3, "tasks": ["2.2"] },
    { "id": 4, "tasks": ["5.1", "5.2", "5.3", "5.4"] }
  ]
}
```
