# Implementation Plan

## Overview

Fix the bug where `handleInputNodeResume()` in `WorkflowExecutionServiceImpl` always stores user input under the hardcoded key `"mobile_no"` instead of using the configured `config.variableName` from the input node. Uses the temporary-context-key pattern (`_inputVariableName`) to propagate the resolved variable name from `InputNodeProcessor.process()` to the resume handler.

## Tasks

- [ ] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Input Node Stores Reply Under Hardcoded "mobile_no" Key
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the bug exists
  - **Scoped PBT Approach**: Use jqwik to generate arbitrary non-empty variable names (excluding "mobile_no") and arbitrary user reply strings
  - **Test class**: `src/test/java/com/xpressbees/chatbot/service/InputNodeVariableNamePropertyTest.java`
  - **Setup**: Create a `ChatSession` with context containing `"_inputVariableName"` set to the generated variable name. Mock `WorkflowRepository` and `ChatSessionRepository` to support `handleInputNodeResume()` flow
  - **Property**: For all generated `variableName` where `variableName != "mobile_no"` and `variableName` is non-empty, after calling `handleInputNodeResume(session, sessionId, message)`:
    - Assert `session.getContext().get(variableName)` equals `message` (from Expected Behavior in design)
    - Assert `session.getContext().get("_inputVariableName")` is null (temporary key cleaned up)
    - Assert `session.getContext().get("mobile_no")` does NOT equal `message` (unless variableName was "mobile_no")
  - **Additional deterministic cases**:
    - `variableName = "email"`, reply = `"user@test.com"` → assert context has key `"email"`
    - `variableName = "order_id"`, reply = `"ORD-999"` → assert context has key `"order_id"`
    - No `_inputVariableName` in context (fallback case) with node id `"node-abc"` → assert context has key `"node-abc"`
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - proves the bug exists because `handleInputNodeResume()` always stores under `"mobile_no"`)
  - Document counterexamples found (e.g., "For variableName='email', reply stored under 'mobile_no' instead of 'email'")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 2.1, 2.2, 2.4_

- [ ] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Mobile_no Variable Name and Pause/Resume Flow Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - **Test class**: `src/test/java/com/xpressbees/chatbot/service/InputNodePreservationPropertyTest.java`
  - **Observe on UNFIXED code**:
    - Input node with `variableName = "mobile_no"`, reply = "9876543210" → stores under `"mobile_no"` in context
    - Input node pause sets `currentNodeType = "input"`, `currentNodeId = node.id`, returns PAUSE with prompt
    - After resume: workflow is loaded, next node is resolved, session is persisted, processing continues
    - API node `_displayVariable` and `_buttonOptions` keys are handled by `handleApiNodeResume()` and not touched by input node logic
  - **Property-based tests using jqwik**:
    - Property: For all generated user reply strings, when `_inputVariableName = "mobile_no"` is in context, `handleInputNodeResume()` stores reply under `"mobile_no"` (same as current behavior)
    - Property: For all input nodes, `InputNodeProcessor.process()` sets `currentNodeType` to `"input"`, `currentNodeId` to node id, and returns `PAUSE` action
    - Property: For all input node resumes, the method loads the workflow, resolves next node, and persists session (verify mock interactions)
  - **Deterministic preservation cases**:
    - Verify `handleApiNodeResume()` still reads `_displayVariable` and `_buttonOptions` from context without interference
    - Verify existing context keys (other than storage target) are not modified during input node resume
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [ ] 3. Fix for input node variable name hardcoded to "mobile_no"

  - [ ] 3.1 Modify `InputNodeProcessor.process()` to store `_inputVariableName` in context
    - Extract `config.variableName` from the node's config map
    - If `variableName` is null or empty after trimming, fall back to `node.get("id")`
    - Store the resolved variable name in `session.getContext()` under key `"_inputVariableName"` before returning PAUSE
    - Ensure existing pause behavior (setting `currentNodeType`, `currentNodeId`, returning PAUSE with prompt) is unchanged
    - _Bug_Condition: isBugCondition(input) where config.variableName != "mobile_no" or is absent_
    - _Expected_Behavior: context contains "_inputVariableName" = resolved variable name before PAUSE_
    - _Preservation: Pause mechanics (currentNodeType, currentNodeId, PAUSE action, prompt) remain identical_
    - _Requirements: 2.3_

  - [ ] 3.2 Modify `handleInputNodeResume()` to use `_inputVariableName` from context
    - Read `"_inputVariableName"` from session context
    - If present: use its value as the key for `context.put(variableName, message)`
    - If absent (defensive fallback): use `getInputVariableName(session.getCurrentNodeId(), workflowJson)` to resolve the key from the workflow JSON
    - Remove `"_inputVariableName"` from context after storing the reply
    - Remove the hardcoded `context.put("mobile_no", message)` line
    - _Bug_Condition: isBugCondition(input) where effectiveKey != "mobile_no"_
    - _Expected_Behavior: context.get(effectiveKey) == message AND context.get("_inputVariableName") == null_
    - _Preservation: Post-resume flow (load workflow, resolve next node, persist session, continue) unchanged_
    - _Requirements: 2.1, 2.4_

  - [ ] 3.3 Update `getInputVariableName()` fallback to use node id instead of "userInput"
    - Change the default return value from `"userInput"` to the `nodeId` parameter
    - This aligns the fallback behavior with requirement 2.2 (node id as fallback when config.variableName is absent)
    - _Bug_Condition: isBugCondition(input) where config.variableName is null/empty_
    - _Expected_Behavior: fallback returns nodeId instead of "userInput"_
    - _Preservation: Method signature and lookup logic unchanged_
    - _Requirements: 2.2_

  - [ ] 3.4 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Input Node Stores Reply Under Configured Variable Name
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior (reply stored under config.variableName)
    - When this test passes, it confirms the expected behavior is satisfied
    - Run bug condition exploration test from step 1: `mvn test -pl . -Dtest=InputNodeVariableNamePropertyTest`
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.1, 2.2, 2.4_

  - [ ] 3.5 Verify preservation tests still pass
    - **Property 2: Preservation** - Mobile_no Variable Name and Pause/Resume Flow Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2: `mvn test -pl . -Dtest=InputNodePreservationPropertyTest`
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all preservation tests still pass after fix (no regressions)
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [ ] 3.6 Write unit tests for `InputNodeProcessor.process()` variable name resolution
    - **Test class**: `src/test/java/com/xpressbees/chatbot/processor/InputNodeProcessorTest.java`
    - Test: when `config.variableName = "email"` → context contains `"_inputVariableName" = "email"`
    - Test: when `config.variableName = "order_id"` → context contains `"_inputVariableName" = "order_id"`
    - Test: when `config.variableName` is null → context contains `"_inputVariableName" = nodeId`
    - Test: when `config.variableName` is empty string → context contains `"_inputVariableName" = nodeId`
    - Test: when `config.variableName` is whitespace only → context contains `"_inputVariableName" = nodeId`
    - Test: existing PAUSE behavior (currentNodeType, currentNodeId, action, response) remains unchanged
    - _Requirements: 2.1, 2.2, 2.3_

  - [ ] 3.7 Write unit tests for `handleInputNodeResume()` variable name usage
    - **Test class**: `src/test/java/com/xpressbees/chatbot/service/HandleInputNodeResumeTest.java`
    - Test: when context has `"_inputVariableName" = "email"` and user replies `"a@b.com"` → context has `"email" = "a@b.com"` and no `"_inputVariableName"` key
    - Test: when context has `"_inputVariableName" = "mobile_no"` and user replies `"1234"` → context has `"mobile_no" = "1234"` (preserves original behavior for this case)
    - Test: when context has no `"_inputVariableName"` key → falls back to `getInputVariableName()` helper
    - Test: after storing reply, `"_inputVariableName"` is removed from context
    - Test: other context keys are not modified during resume
    - _Requirements: 2.1, 2.4, 3.1_

  - [ ] 3.8 Write integration test for multi-input-node workflow
    - **Test class**: `src/test/java/com/xpressbees/chatbot/service/MultiInputNodeIntegrationTest.java`
    - Setup: Create a workflow with three input nodes in sequence: `variableName = "mobile_no"`, `variableName = "email"`, `variableName = "order_id"`
    - Execute: Start workflow, reply "9876543210", reply "user@test.com", reply "ORD-12345"
    - Assert: Final context contains `{"mobile_no": "9876543210", "email": "user@test.com", "order_id": "ORD-12345"}` — all values preserved
    - Assert: Downstream message node resolving `{{email}}` produces "user@test.com"
    - Assert: Downstream API node using `{{order_id}}` in URL resolves correctly
    - _Requirements: 2.1, 2.5, 3.3_

- [ ] 4. Checkpoint - Ensure all tests pass
  - Run full test suite: `mvn test`
  - Ensure all property-based tests (exploration + preservation) pass
  - Ensure all unit tests pass
  - Ensure all integration tests pass
  - Verify no regressions in existing test suite
  - Ask the user if questions arise

## Task Dependency Graph

```json
{
  "waves": [
    ["1", "2"],
    ["3.1", "3.2", "3.3"],
    ["3.4", "3.5", "3.6", "3.7", "3.8"],
    ["4"]
  ]
}
```

## Notes

- Tasks 1 and 2 are independent and can be worked on in parallel
- Task 1 (exploration test) is expected to FAIL on unfixed code — this confirms the bug exists
- Task 2 (preservation test) is expected to PASS on unfixed code — this captures baseline behavior
- Tasks 3.1-3.3 implement the actual fix and depend on the tests being written first
- Tasks 3.4-3.5 re-run the same tests from tasks 1-2 to verify the fix works without regressions
- jqwik 1.8.2 is already available as a project dependency for property-based testing
- The fix follows the existing temporary-context-key pattern used by `ApiNodeProcessor` (`_displayVariable`, `_buttonOptions`)
