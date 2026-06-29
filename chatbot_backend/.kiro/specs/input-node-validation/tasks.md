# Implementation Plan: Input Node Validation

## Overview

Add server-side input validation to the chatbot workflow engine's input nodes. This involves creating a `ValidationResult` DTO, an `InputValidationService` interface with its implementation (`InputValidationServiceImpl`), and integrating validation into the existing `WorkflowExecutionServiceImpl.handleInputNodeResume()` method. The validation service evaluates user input against rules defined in the node's `config.validation` object using short-circuit evaluation, returning the first failing rule's error message.

## Tasks

- [x] 1. Create ValidationResult DTO and InputValidationService interface
  - [x] 1.1 Create `ValidationResult` DTO class
    - Create `src/main/java/com/xpressbees/chatbot/dto/ValidationResult.java`
    - Include `valid` (boolean) and `errorMessage` (String) fields
    - Add static factory methods: `success()` and `failure(String errorMessage)`
    - Use Lombok `@Data` and `@AllArgsConstructor`
    - _Requirements: 1.5, 1.6_

  - [x] 1.2 Create `InputValidationService` interface
    - Create `src/main/java/com/xpressbees/chatbot/service/InputValidationService.java`
    - Define single method: `ValidationResult validate(String input, Map<String, Object> validationConfig)`
    - _Requirements: 1.1, 1.2_

- [x] 2. Implement InputValidationServiceImpl with all validation rules
  - [x] 2.1 Create `InputValidationServiceImpl` with required rule and helper methods
    - Create `src/main/java/com/xpressbees/chatbot/service/InputValidationServiceImpl.java`
    - Annotate with `@Service`, use SLF4J logger
    - Implement `validate()` method with null/empty config early-return (success)
    - Implement `extractErrorMessages()` helper to extract the `errorMessages` map from config
    - Implement `validateRequired()`: reject if `required == true` and input is null/empty/whitespace-only; treat non-boolean `required` as false (fail-open)
    - Define hardcoded default messages as constants
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 6.2, 6.3_

  - [x] 2.2 Implement minLength and maxLength validation rules
    - Add `validateMinLength()`: compare trimmed input length against `minLength` integer; skip if non-integer or negative (log warning)
    - Add `validateMaxLength()`: compare trimmed input length against `maxLength` integer; skip if non-integer or negative (log warning)
    - Use custom error message from `errorMessages.minLength`/`errorMessages.maxLength` if present, otherwise format default with the limit value
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 6.2, 6.3_

  - [x] 2.3 Implement numericOnly validation rule
    - Add `validateNumericOnly()`: if `numericOnly == true`, check that trimmed input is empty OR every character is ASCII digit (0-9)
    - Reject decimal points, negative signs, and Unicode digit characters
    - Accept empty trimmed input (empty check is handled by `required` rule)
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [x] 2.4 Implement pattern validation rule
    - Add `validatePattern()`: compile pattern using `Pattern.compile()`, apply `Matcher.matches()` against untrimmed input
    - Catch `PatternSyntaxException`: log warning with pattern string and accept input (fail-open)
    - Use custom error message from `errorMessages.pattern` if present, otherwise hardcoded default
    - _Requirements: 4.1, 4.2, 4.3_

  - [x] 2.5 Wire short-circuit evaluation order in `validate()` method
    - Ensure evaluation order: required → minLength → maxLength → numericOnly → pattern
    - Return immediately on first failure (short-circuit)
    - Return `ValidationResult.success()` only if all enabled rules pass
    - _Requirements: 6.5, 7.1, 7.2, 7.3, 7.4, 7.5_

  - [ ]* 2.6 Write property test: Required rule correctness (Property 1)
    - **Property 1: Required rule correctness**
    - **Validates: Requirements 2.1, 2.2, 2.3**
    - Create `src/test/java/com/xpressbees/chatbot/service/InputValidationPropertyTest.java`
    - Test that for any input string and `required` setting, validation passes iff `required != true` OR input has at least one non-whitespace char

  - [ ]* 2.7 Write property test: Length validation correctness (Property 2)
    - **Property 2: Length validation correctness**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
    - Test that for any input and valid minLength/maxLength config, length validation passes iff trimmed input length is within [minLength, maxLength]

  - [ ]* 2.8 Write property test: Pattern validation agrees with Java regex (Property 3)
    - **Property 3: Pattern validation agrees with Java regex**
    - **Validates: Requirements 4.1, 4.2**
    - Test that pattern validation produces same result as `Pattern.compile(pattern).matcher(input).matches()` on untrimmed input

  - [ ]* 2.9 Write property test: Invalid pattern fail-open (Property 4)
    - **Property 4: Invalid pattern fail-open**
    - **Validates: Requirements 4.3**
    - Test that for any non-valid Java regex string, the pattern rule always accepts input

  - [ ]* 2.10 Write property test: NumericOnly validation correctness (Property 5)
    - **Property 5: NumericOnly validation correctness**
    - **Validates: Requirements 5.1, 5.2, 5.3**
    - Test that numericOnly rule passes iff trimmed input is empty OR all chars are ASCII digits [0-9]

  - [ ]* 2.11 Write property test: Error message resolution (Property 6)
    - **Property 6: Error message resolution**
    - **Validates: Requirements 6.2, 6.3**
    - Test that returned error message equals custom message when key present, otherwise hardcoded default

  - [ ]* 2.12 Write property test: Short-circuit evaluation order (Property 7)
    - **Property 7: Short-circuit evaluation order**
    - **Validates: Requirements 6.5, 7.1, 7.2**
    - Test that when input fails multiple rules, error corresponds to earliest failing rule in order

  - [ ]* 2.13 Write property test: Composition AND logic (Property 8)
    - **Property 8: Composition AND logic**
    - **Validates: Requirements 1.5, 7.3, 7.5**
    - Test that overall validation passes iff every individually-enabled rule passes

- [x] 3. Checkpoint - Validate core service implementation
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Integrate InputValidationService into WorkflowExecutionServiceImpl
  - [x] 4.1 Inject `InputValidationService` and add validation logic to `handleInputNodeResume()`
    - Add `InputValidationService` as constructor parameter in `WorkflowExecutionServiceImpl`
    - In `handleInputNodeResume()`, load the workflow JSON and find the current node config BEFORE storing input
    - Extract `validation` object from the current node's config map
    - Call `inputValidationService.validate(message, validationConfig)` when validation config is present
    - If invalid: call `sendError(sessionId, validationResult.getErrorMessage())` and return immediately (session stays paused)
    - If valid: proceed with existing logic (store input in context, advance to next node)
    - Restructure method to load workflow earlier (before storing input) to access node config
    - _Requirements: 1.4, 1.5, 1.6, 1.7, 6.1, 6.4_

  - [ ]* 4.2 Write unit tests for integration in `WorkflowExecutionServiceImpl`
    - Create `src/test/java/com/xpressbees/chatbot/service/WorkflowExecutionServiceImplValidationTest.java`
    - Mock `InputValidationService`, `WorkflowRepository`, `ChatSessionRepository`, `SimpMessagingTemplate`
    - Test: validation fails → error sent, session unchanged, no input stored
    - Test: validation passes → input stored, workflow advances
    - Test: no validation config → existing behavior preserved (input stored immediately)
    - _Requirements: 6.4, 1.4, 1.6_

  - [ ]* 4.3 Write property test: Session state preservation on failure (Property 9)
    - **Property 9: Session state preservation on failure**
    - **Validates: Requirements 6.4**
    - Test that for any input failing validation, session's `currentNodeId`, `currentNodeType`, and `context` remain unchanged

- [x] 5. Write unit tests for InputValidationServiceImpl
  - [ ]* 5.1 Write unit tests for individual validation rules
    - Create `src/test/java/com/xpressbees/chatbot/service/InputValidationServiceImplTest.java`
    - Test required rule: null, empty, whitespace, valid input, non-boolean required value
    - Test minLength: exact boundary, off-by-one, trimmed input with spaces
    - Test maxLength: exact boundary, off-by-one, trimmed input with spaces
    - Test numericOnly: digits only, letters, special chars, Unicode digits (rejected), decimal point (rejected), empty input (accepted)
    - Test pattern: valid regex match/no-match, invalid regex (fail-open with log warning)
    - Test error messages: custom present, custom absent, mixed
    - Test full validation: null config (accept), empty config (accept), multiple rules combined
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 4.1, 4.2, 4.3, 5.1, 5.2, 5.3, 6.2, 6.3, 7.1_

- [x] 6. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (9 properties)
- Unit tests validate specific examples and edge cases
- No database schema changes are needed — validation config lives in existing JSONB workflow definition
- The implementation language is Java 17 with Spring Boot 3.3.5, jqwik 1.8.2 for property tests, JUnit 5 + Mockito for unit tests

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "2.4"] },
    { "id": 3, "tasks": ["2.5", "2.6", "2.10"] },
    { "id": 4, "tasks": ["2.7", "2.8", "2.9", "2.11", "2.12", "2.13"] },
    { "id": 5, "tasks": ["4.1"] },
    { "id": 6, "tasks": ["4.2", "4.3", "5.1"] }
  ]
}
```
