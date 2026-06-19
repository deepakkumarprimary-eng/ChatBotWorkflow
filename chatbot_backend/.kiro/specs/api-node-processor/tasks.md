# Implementation Plan: API Node Processor

## Overview

This plan implements the API Node Processor feature by building components incrementally: starting with the dependency and DTO foundation, then refactoring PlaceholderService, building the core processing components (HttpExecutor, ResponseExtractor, ConditionEvaluator), assembling the ApiNodeProcessor, modifying the workflow engine for new behaviors (conditional branching, interactive selection, button nodes), and wiring everything together with integration tests.

## Tasks

- [x] 1. Add JsonPath dependency and create foundation DTOs
  - [x] 1.1 Add Jayway JsonPath dependency to pom.xml
    - Add `com.jayway.jsonpath:json-path:2.9.0` to the `<dependencies>` section in `pom.xml`
    - _Requirements: 5.1_

  - [x] 1.2 Create HttpExecutionResult DTO
    - Create `src/main/java/com/xpressbees/chatbot/dto/HttpExecutionResult.java`
    - Fields: `boolean success`, `int statusCode`, `String responseBody`, `String errorMessage`
    - Use Lombok `@Data` and `@AllArgsConstructor`
    - _Requirements: 4.6, 4.7_

  - [x] 1.3 Create ExtractionResult DTO
    - Create `src/main/java/com/xpressbees/chatbot/dto/ExtractionResult.java`
    - Fields: `boolean success`, `Map<String, String> extractedValues`, `String errorMessage`
    - Use Lombok `@Data` and `@AllArgsConstructor`
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 2. Refactor PlaceholderService to regex-based substitution
  - [x] 2.1 Implement regex-based placeholder resolution
    - Refactor `PlaceholderService.resolve()` to use `Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}")` regex matching
    - Replace all `{{variable}}` tokens whose variable name exists in context with `String.valueOf()` of the context value
    - Leave tokens unchanged if the variable name is not in the context
    - Maintain null-safety for template and context parameters
    - _Requirements: 3.1, 3.4, 3.5_

  - [x] 2.2 Add resolvePayload method to PlaceholderService
    - Add `Map<String, Object> resolvePayload(Map<String, Object> payloadTemplate, Map<String, Object> context)` method
    - Recursively traverse the payload map; for every String value, apply the same regex-based substitution
    - Return a new map with substituted values (do not mutate input)
    - _Requirements: 3.2_

  - [x] 2.3 Write property tests for PlaceholderService (Properties 2 and 3)
    - **Property 2: Placeholder Substitution Correctness** — For any template with `{{var}}` tokens and any context map, tokens with matching keys are replaced with `String.valueOf()` of the value, tokens without matching keys remain unchanged
    - **Property 3: Placeholder Pattern Strictness** — Only tokens matching `{{[a-zA-Z0-9_]+}}` are substituted; invalid patterns like `{{a-b}}`, `{{}}`, `{{ x }}` are not substituted
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

- [x] 3. Checkpoint - Ensure placeholder refactoring compiles and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement HttpExecutor
  - [x] 4.1 Create HttpExecutor component
    - Create `src/main/java/com/xpressbees/chatbot/service/HttpExecutor.java`
    - Annotate with `@Component`
    - Implement `execute(ApiConfig config, String resolvedUrl, Map<String, String> resolvedHeaders, String resolvedBody)` method
    - Use Spring's `RestClient` to make HTTP calls with the method from ApiConfig (GET, POST, PUT, DELETE)
    - Apply `timeoutMs` from ApiConfig for connection and read timeouts
    - Include all resolved headers on the request
    - Send resolved body for POST/PUT requests
    - Return `HttpExecutionResult` with success/failure info
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [x] 4.2 Add retry logic to HttpExecutor
    - When a request fails with timeout, connection error, or HTTP 5xx, retry up to `retryCount` times with 1000ms fixed delay
    - Do NOT retry on 4xx errors or JSON parse failures
    - After all retries exhausted, return failure result with error message
    - _Requirements: 4.5, 4.6_

  - [x] 4.3 Write property test for HttpExecutor retry logic (Property 4)
    - **Property 4: Retry Count Correctness** — For any retryCount N (0-5) and always-failing requests, exactly N+1 total attempts are made
    - **Validates: Requirements 4.5**

- [x] 5. Implement ResponseExtractor
  - [x] 5.1 Create ResponseExtractor component
    - Create `src/main/java/com/xpressbees/chatbot/service/ResponseExtractor.java`
    - Annotate with `@Component`
    - Implement `extract(String jsonBody, List<ApiResponseMapping> mappings)` method
    - Use Jayway JsonPath to evaluate each mapping's `responsePath` against the JSON body
    - For primitives (String, Number, Boolean): store `String.valueOf()` in result map
    - For arrays: join non-null elements with `\n`, store in result map
    - If path is valid but yields null/empty: skip mapping, continue to next
    - If JsonPath throws exception: return failure with path and variable name in error message
    - If body is not valid JSON: return failure with parse error message
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x] 5.2 Write property tests for ResponseExtractor (Properties 5 and 6)
    - **Property 5: JsonPath Primitive Extraction** — For any valid JSON with a primitive at a known path, the extracted value equals `String.valueOf()` of that primitive
    - **Property 6: JsonPath Array Extraction** — For any valid JSON array at a known path, the extracted value equals non-null elements joined by `\n`
    - **Validates: Requirements 5.1, 5.2, 5.3**

- [x] 6. Implement ConditionEvaluator
  - [x] 6.1 Create ConditionEvaluator component
    - Create `src/main/java/com/xpressbees/chatbot/service/ConditionEvaluator.java`
    - Annotate with `@Component`
    - Implement `evaluate(String expression, Map<String, Object> context)` method
    - Parse simple expressions: split by whitespace into 3 tokens (variable, operator, value)
    - Support operators: `==`, `!=`, `<`, `>`, `<=`, `>=`
    - For `==`/`!=`: case-sensitive string comparison between `String.valueOf(context.get(variable))` and literal
    - For `<`/`>`/`<=`/`>=`: parse both as doubles; if either fails, return false
    - If variable not in context, return false
    - If expression doesn't have exactly 3 tokens (for simple), treat as false
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.9_

  - [x] 6.2 Add compound condition support (and/or with precedence)
    - Split expression on ` or ` boundaries first to get OR groups
    - Split each OR group on ` and ` boundaries to get AND sub-conditions
    - Evaluate: `and` binds tighter than `or` (i.e., `A or B and C` = `A or (B and C)`)
    - Return true if any OR group is fully true
    - _Requirements: 8.5, 8.6, 8.7, 8.8_

  - [x] 6.3 Write property tests for ConditionEvaluator (Properties 7 and 8)
    - **Property 7: Condition Expression Evaluation Correctness** — For any simple condition and context, string operators use case-sensitive comparison, numeric operators parse as doubles, missing variables yield false
    - **Property 8: Compound Condition Precedence** — `and` binds tighter than `or` in all compound expressions
    - **Validates: Requirements 7.2, 7.3, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9**

- [x] 7. Checkpoint - Ensure all core components compile and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement ApiNodeProcessor
  - [x] 8.1 Create ApiNodeProcessor with canHandle logic
    - Create `src/main/java/com/xpressbees/chatbot/processor/ApiNodeProcessor.java`
    - Annotate with `@Component` and `@Order(3)`
    - Implement `canHandle()`: return true if and only if `node.get("type")` equals `"api"` (case-sensitive)
    - Return false for null type, absent key, or any other type value
    - Use constructor injection for: `ApiConfigRepository`, `HttpExecutor`, `ResponseExtractor`, `ConditionEvaluator`, `SimpMessagingTemplate`
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 8.2 Implement ApiNodeProcessor.process() - config loading and request preparation
    - Extract `apiConfigId` from `node.config` map; handle missing/null/unparseable cases with error responses
    - Load `ApiConfig` from repository; handle not-found case
    - Use PlaceholderService to resolve URL, header values, and payload template from session context
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3_

  - [x] 8.3 Implement ApiNodeProcessor.process() - HTTP execution and response extraction
    - Call `HttpExecutor.execute()` with resolved URL, headers, and body
    - On failure: return appropriate error response (timeout vs. status code)
    - On success: call `ResponseExtractor.extract()` with response body and ApiConfig's response mappings
    - On extraction success: store all extracted values in session context
    - On extraction failure: send error via ChatErrorResponse
    - _Requirements: 4.1, 4.7, 5.1, 5.4, 5.6, 10.1, 10.2, 10.3, 10.4_

  - [x] 8.4 Implement ApiNodeProcessor.process() - behavior inference and routing
    - Implement behavior inference logic:
      - If node has `displayVariable`: Type 3 (interactive) → PAUSE
      - If multiple transitions with conditions: Type 2 (conditional) → evaluate conditions, CONTINUE to first match
      - If single transition without condition: Type 1 (auto-advance) → CONTINUE
      - If multiple transitions without conditions: Button node → PAUSE with button options
    - For Type 2: use ConditionEvaluator on each transition's condition in order; first-match-wins
    - For Type 2 no-match: return error response
    - For Type 3: send newline-separated array values, set session state, return PAUSE
    - For button node: collect target node names, send as options, return PAUSE
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 7.1, 7.4, 7.5, 7.6, 7.7, 9.1, 9.2, 9.3, 9.4, 9.8, 9.9, 12.1, 12.2, 12.3_

  - [x] 8.5 Write property tests for ApiNodeProcessor canHandle (Property 1)
    - **Property 1: canHandle Type Equivalence** — canHandle returns true iff node type is exactly `"api"`, false for all other values including null/absent
    - **Validates: Requirements 1.1, 1.2, 1.3**

  - [x] 8.6 Write property tests for first-match-wins and selection validation (Properties 9, 10, 11)
    - **Property 9: First-Match-Wins for Conditional Transitions** — When multiple conditions are true, the lowest-index transition is selected
    - **Property 10: Interactive Selection Validation** — Selection accepted iff reply exactly matches an array element (case-sensitive)
    - **Property 11: Button Node Routing Correctness** — Routes to target iff reply exactly matches a target node name
    - **Validates: Requirements 7.1, 7.4, 9.5, 9.7, 12.4, 12.5**

- [x] 9. Modify WorkflowExecutionServiceImpl for API node support
  - [x] 9.1 Extend handleUserInput for API node resume (Type 3 selection and button nodes)
    - When `currentNodeType == "api"`: retrieve `displayVariable` from session context, validate user reply against stored options
    - For interactive selection (Type 3): validate reply matches one of the newline-separated values, store selected value under displayVariable key, resolve next node, continue processing
    - For button nodes: validate reply matches a target node name, advance to matching node
    - For invalid selections: send error message, remain paused
    - _Requirements: 9.5, 9.6, 9.7, 11.3, 12.4, 12.5_

  - [x] 9.2 Enhance resolveNextNode to support targeted routing
    - Add an overloaded `resolveNextNode(String currentNodeId, String targetNodeId, Map<String, Object> workflowJson)` method
    - When `targetNodeId` is provided (from conditional branching), return the node matching that specific ID
    - Used by ApiNodeProcessor's Type 2 behavior to route to the condition-matched target
    - _Requirements: 7.5, 6.2_

  - [x] 9.3 Add session state persistence for paused API nodes
    - When ApiNodeProcessor returns PAUSE: set `currentNodeId`, `currentNodeType = "api"` on session
    - Store `displayVariable` value in session context under a known key (e.g., `_displayVariable`) for resume
    - Persist session with updated context map
    - Handle completion when no outgoing transition exists from a paused API node
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

- [x] 10. Checkpoint - Ensure all components compile and unit tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Integration tests and final wiring
  - [x] 11.1 Write integration tests for full API node processing flows
    - Test Type 1 (auto-advance): mock external API, verify context updated and CONTINUE returned
    - Test Type 2 (conditional): mock API, verify correct branch taken based on extracted values
    - Test Type 3 (interactive): mock API, verify PAUSE, then resume with valid/invalid selection
    - Test button node: verify names sent as options, valid/invalid selection handling
    - Test session persistence: verify `currentNodeId`, `currentNodeType`, and context saved on PAUSE
    - Test error scenarios: missing config, HTTP failure, invalid JSON, no matching condition
    - _Requirements: 6.1, 6.2, 7.1, 7.5, 9.1, 9.5, 9.7, 10.1, 10.2, 10.3, 11.1, 11.3, 12.1, 12.4_

- [x] 12. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The project uses Java 17 with Spring Boot 3.3.5 and jqwik 1.8.2 for property-based testing
- Spring's `RestClient` is already available (Spring Framework 6.1+ bundled with Boot 3.3.5)
- The existing `PlaceholderService` with hardcoded `<mobile_no>` replacement must be refactored before other components depend on it

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["2.1", "2.2"] },
    { "id": 2, "tasks": ["2.3", "4.1"] },
    { "id": 3, "tasks": ["4.2", "5.1"] },
    { "id": 4, "tasks": ["4.3", "5.2", "6.1"] },
    { "id": 5, "tasks": ["6.2"] },
    { "id": 6, "tasks": ["6.3", "8.1"] },
    { "id": 7, "tasks": ["8.2"] },
    { "id": 8, "tasks": ["8.3"] },
    { "id": 9, "tasks": ["8.4"] },
    { "id": 10, "tasks": ["8.5", "8.6", "9.1", "9.2"] },
    { "id": 11, "tasks": ["9.3"] },
    { "id": 12, "tasks": ["11.1"] }
  ]
}
```
