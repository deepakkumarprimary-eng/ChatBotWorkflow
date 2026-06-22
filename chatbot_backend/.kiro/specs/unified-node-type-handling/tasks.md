# Implementation Plan

## Overview

Fix `ApiNodeProcessor.canHandle()` to use the unified `type: "state"` + `config.nodeType: "api"` pattern instead of checking `type == "api"` directly. This aligns the API node processor with `InputNodeProcessor`, `MessageNodeProcessor`, and `WorkflowNodeProcessor`.

## Tasks

- [ ] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - ApiNodeProcessor Rejects Unified Format
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate `ApiNodeProcessor.canHandle()` rejects nodes using the unified `type: "state"` + `config.nodeType: "api"` pattern
  - **Scoped PBT Approach**: Scope the property to nodes with `type: "state"` and `config.nodeType: "api"` (the unified format that all other processors use)
  - Create test class `ApiNodeProcessorUnifiedFormatBugConditionTest` in `src/test/java/com/xpressbees/chatbot/processor/`
  - Use jqwik `@Property` with `@Tag("Feature: unified-node-type-handling, Property 1: Bug Condition")`
  - Instantiate `ApiNodeProcessor` with null dependencies (only testing `canHandle()`)
  - Generate node maps with `type: "state"`, `config: {nodeType: "api"}` and optional fields (`apiConfigId`, `displayVariable`)
  - Assert `canHandle(node)` returns `true` for all generated nodes (from Expected Behavior in design: `isBugCondition(node)` inputs should produce `true` after fix)
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS because current `canHandle()` checks `"api".equals(node.get("type"))` and returns `false` for `type: "state"` nodes
  - Document counterexample: `{type: "state", config: {nodeType: "api", apiConfigId: 5}}` → `canHandle()` returns `false` instead of `true`
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 2.1, 2.3_

- [ ] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-API Nodes Correctly Rejected
  - **IMPORTANT**: Follow observation-first methodology
  - Create test class `ApiNodeProcessorPreservationPropertyTest` in `src/test/java/com/xpressbees/chatbot/processor/`
  - Use jqwik `@Property` with `@Tag("Feature: unified-node-type-handling, Property 2: Preservation")`
  - Instantiate `ApiNodeProcessor` with null dependencies (only testing `canHandle()`)
  - Observe on UNFIXED code: `canHandle({type: "state", config: null})` returns `false`
  - Observe on UNFIXED code: `canHandle({type: "state", config: {nodeType: "input"}})` returns `false`
  - Observe on UNFIXED code: `canHandle({type: "state", config: {nodeType: "workflow"}})` returns `false`
  - Observe on UNFIXED code: `canHandle({type: "state", config: {}})` returns `false`
  - Observe on UNFIXED code: `canHandle({type: "message"})` returns `false`
  - Observe on UNFIXED code: `canHandle({type: null})` returns `false`
  - Write property-based tests:
    - For all random `type` strings that are NOT `"state"` → `canHandle()` returns `false`
    - For all nodes with `type: "state"` and `config: null` → `canHandle()` returns `false`
    - For all nodes with `type: "state"` and `config.nodeType` ∈ random strings except `"api"` → `canHandle()` returns `false`
    - For all nodes with `type: "state"` and `config` without `nodeType` key → `canHandle()` returns `false`
  - Verify all tests PASS on UNFIXED code (these cases already return `false` since current code only matches `type == "api"`)
  - **EXPECTED OUTCOME**: Tests PASS (confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 2.2, 2.4, 3.1, 3.2, 3.3_

- [ ] 3. Fix for ApiNodeProcessor canHandle() unified node type handling

  - [ ] 3.1 Implement the fix
    - Modify `ApiNodeProcessor.canHandle()` in `src/main/java/com/xpressbees/chatbot/processor/ApiNodeProcessor.java`
    - Change type check from `"api".equals(type)` to `"state".equals(type)` with early return false if not state
    - Extract `config` map from node: `Map<String, Object> config = (Map<String, Object>) node.get("config")`
    - Add null-safe config check: return `false` if config is null (prevents NPE, satisfies requirement 2.4)
    - Add nodeType discrimination: return `"api".equals(config.get("nodeType"))`
    - Add `@SuppressWarnings("unchecked")` annotation for the config cast (consistent with `InputNodeProcessor` and `WorkflowNodeProcessor`)
    - Cast `node.get("type")` to `String` (consistent with other processors)
    - No changes to `@Order(3)` annotation
    - No changes to `process()` method internals
    - _Bug_Condition: isBugCondition(node) where node.type == "state" AND node.config != null AND node.config.nodeType == "api" AND canHandle(node) == false_
    - _Expected_Behavior: canHandle(node) returns true when type == "state" AND config != null AND config.nodeType == "api"_
    - _Preservation: canHandle(node) returns false for all other inputs (type != "state", config == null, config.nodeType != "api")_
    - _Requirements: 1.1, 1.2, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3_

  - [ ] 3.2 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - ApiNodeProcessor Accepts Unified Format
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior (canHandle returns true for unified format nodes)
    - Run `mvn test -pl . -Dtest=ApiNodeProcessorUnifiedFormatBugConditionTest`
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed - canHandle now returns true for `type: "state"` + `config.nodeType: "api"` nodes)
    - _Requirements: 2.1, 2.3_

  - [ ] 3.3 Verify preservation tests still pass
    - **Property 2: Preservation** - Non-API Nodes Still Rejected
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run `mvn test -pl . -Dtest=ApiNodeProcessorPreservationPropertyTest`
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions - non-API nodes still correctly rejected)
    - Confirm all preservation properties still hold after fix
    - _Requirements: 2.2, 2.4, 3.1, 3.2, 3.3_

- [ ] 4. Checkpoint - Ensure all tests pass
  - Run full test suite: `mvn test`
  - Verify `ApiNodeProcessorUnifiedFormatBugConditionTest` passes (bug condition fixed)
  - Verify `ApiNodeProcessorPreservationPropertyTest` passes (no regressions)
  - Verify existing `ApiNodeProcessorCanHandlePropertyTest` is updated or removed (it tests the OLD behavior of matching `type == "api"`)
  - Verify `NodeProcessorClassificationPropertyTest` still passes (if it exists and tests mutual exclusivity)
  - Ensure all other processor tests (`InputNodeProcessorTest`, `WorkflowNodeProcessorTest`, etc.) pass unchanged
  - Ask the user if questions arise

## Task Dependency Graph

```json
{
  "waves": [
    ["1", "2"],
    ["3.1"],
    ["3.2", "3.3"],
    ["4"]
  ]
}
```

## Notes

- Task 1 (bug condition test) and Task 2 (preservation test) can be written in parallel but both MUST be completed before Task 3.1 (implementation)
- The existing `ApiNodeProcessorCanHandlePropertyTest` tests the OLD behavior (`type == "api"`). It will need to be updated or removed as part of the checkpoint since the fix intentionally changes this behavior.
- jqwik 1.8.2 is already configured in the project's `pom.xml`
- All tests only exercise `canHandle()` which has no dependencies, so `ApiNodeProcessor` can be instantiated with null constructor args
