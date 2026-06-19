# Input Node Variable Name Fix — Bugfix Design

## Overview

The `handleInputNodeResume()` method in `WorkflowExecutionServiceImpl` hardcodes the context key `"mobile_no"` when storing user replies from input nodes. This prevents workflows with multiple input nodes from collecting distinct fields. The fix adopts the same temporary-context-key pattern used by `ApiNodeProcessor` (`_displayVariable`, `_buttonOptions`): `InputNodeProcessor.process()` resolves the target variable name and stores it under `"_inputVariableName"` before pausing, and `handleInputNodeResume()` reads that key to store the reply correctly and then removes it.

## Glossary

- **Bug_Condition (C)**: Any input node resume where the configured `variableName` differs from `"mobile_no"` (or where multiple input nodes exist) — the reply ends up under the wrong key
- **Property (P)**: The user's reply is stored in session context under the key matching the node's `config.variableName` (or the node id as fallback)
- **Preservation**: Existing pause/resume flow, session state management, API node temporary key handling, and workflows that only use `"mobile_no"` must remain unchanged
- **InputNodeProcessor**: The Spring component in `processor/InputNodeProcessor.java` that handles input-type nodes — sets session state and returns PAUSE
- **WorkflowExecutionServiceImpl**: The service in `service/WorkflowExecutionServiceImpl.java` containing `handleInputNodeResume()` which processes user replies to paused input nodes
- **_inputVariableName**: The temporary context key (prefixed with `_`) used to pass the resolved variable name from the processor to the resume handler — follows the `_displayVariable` / `_buttonOptions` convention
- **config.variableName**: The field on an input node's config map that specifies which context key the collected value should be stored under

## Bug Details

### Bug Condition

The bug manifests when any input node with a `config.variableName` other than `"mobile_no"` pauses and the user replies. The `handleInputNodeResume()` method unconditionally executes `context.put("mobile_no", message)`, ignoring the node's configured variable name entirely.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type { node: InputNode, userReply: String }
  OUTPUT: boolean

  LET variableName = node.config.variableName
  LET effectiveKey = IF variableName IS NOT NULL AND variableName.trim() IS NOT EMPTY
                     THEN variableName
                     ELSE node.id

  RETURN effectiveKey != "mobile_no"
         OR workflowContainsMultipleInputNodes(node.workflowId)
END FUNCTION
```

### Examples

- **Email node**: Input node with `config.variableName = "email"`, user types `"user@example.com"` → stored under `"mobile_no"` instead of `"email"`. Downstream `{{email}}` placeholder resolves to null.
- **Order ID node**: Input node with `config.variableName = "order_id"`, user types `"ORD-12345"` → stored under `"mobile_no"` instead of `"order_id"`. Previous mobile_no value is overwritten.
- **Multi-input workflow**: Three input nodes (`"mobile_no"`, `"email"`, `"name"`) in sequence. After all three replies, context only contains `{"mobile_no": "<last reply>"}` — first two values are lost.
- **No variableName configured**: Input node with no `config.variableName` set, user types `"hello"` → should fall back to storing under node id, but currently stores under `"mobile_no"`.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Mouse/keyboard interactions with WebSocket messaging continue to work as before
- Input node PAUSE mechanics (setting `currentNodeType`, `currentNodeId`, returning PAUSE action with prompt) remain identical
- Post-resume flow (load workflow, resolve next node, persist session, continue processing) remains identical
- API node `_displayVariable` and `_buttonOptions` temporary key handling is untouched
- Workflows where the only input node uses `variableName = "mobile_no"` produce the same context state as before

**Scope:**
All inputs that do NOT involve the context key storage logic in `handleInputNodeResume()` should be completely unaffected by this fix. This includes:
- WebSocket connection/session initialization
- Workflow start logic
- Message node processing
- API node processing (HTTP execution, response extraction, conditional branching)
- Placeholder resolution service
- Session persistence and status management

## Hypothesized Root Cause

Based on the code analysis, the root cause is straightforward:

1. **Hardcoded key in `handleInputNodeResume()`**: Line `context.put("mobile_no", message)` uses a literal string instead of reading the target key from context or the node config. This was likely written when the system only had a single input node collecting a mobile number.

2. **Missing variable name propagation in `InputNodeProcessor.process()`**: The processor does not extract `config.variableName` from the node or store it anywhere before pausing. The resume handler has no way to know which key to use.

3. **Unused `getInputVariableName()` method**: `WorkflowExecutionServiceImpl` already contains a `getInputVariableName(nodeId, workflowJson)` helper that correctly extracts the variable name from node config — but it is never called. This suggests the fix was partially implemented but never completed.

4. **No fallback logic**: There is no fallback to node id when `config.variableName` is absent, leaving such nodes unable to store replies under any meaningful key.

## Correctness Properties

Property 1: Bug Condition — Reply Stored Under Configured Variable Name

_For any_ input node resume where the node has a non-empty `config.variableName`, the fixed system SHALL store the user's reply in session context under a key equal to that `config.variableName` value, and the `"_inputVariableName"` temporary key SHALL be removed from context after storage.

**Validates: Requirements 2.1, 2.3, 2.4**

Property 2: Bug Condition — Fallback to Node ID

_For any_ input node resume where the node has no `config.variableName` or it is empty, the fixed system SHALL store the user's reply in session context under a key equal to the node's `id`, and the `"_inputVariableName"` temporary key SHALL be removed from context after storage.

**Validates: Requirements 2.2, 2.3, 2.4**

Property 3: Preservation — Non-Input-Key Context and Flow Unchanged

_For any_ input node resume, the fixed system SHALL continue to load the workflow, resolve the next node, persist session state, and process subsequent nodes identically to the original code. All context keys other than the storage target key and `"_inputVariableName"` SHALL remain unchanged.

**Validates: Requirements 3.2, 3.3, 3.4**

Property 4: Preservation — Multiple Input Nodes Preserve All Values

_For any_ workflow with N input nodes collecting distinct fields, after all N replies are processed, the session context SHALL contain N distinct keys with their respective user reply values — no value is overwritten by a subsequent input node.

**Validates: Requirements 2.5, 3.1**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `src/main/java/com/xpressbees/chatbot/processor/InputNodeProcessor.java`

**Function**: `process()`

**Specific Changes**:
1. **Extract variable name from config**: Read `config.variableName` from the node's config map
2. **Apply fallback logic**: If `variableName` is null or empty after trimming, use `node.get("id")` as the fallback
3. **Store in context as temporary key**: Put the resolved variable name into `session.getContext()` under key `"_inputVariableName"` before returning PAUSE

---

**File**: `src/main/java/com/xpressbees/chatbot/service/WorkflowExecutionServiceImpl.java`

**Function**: `handleInputNodeResume()`

**Specific Changes**:
1. **Read the target key**: Replace `context.put("mobile_no", message)` with reading `"_inputVariableName"` from context
2. **Fallback safety**: If `"_inputVariableName"` is missing from context (defensive case), fall back to the node id using the existing `getInputVariableName()` helper or a sensible default
3. **Store reply under correct key**: Execute `context.put(variableName, message)` using the resolved key
4. **Clean up temporary key**: Execute `context.remove("_inputVariableName")` after storing the reply

---

**File**: `src/main/java/com/xpressbees/chatbot/service/WorkflowExecutionServiceImpl.java`

**Function**: `getInputVariableName()` (existing unused method)

**Specific Changes**:
5. **Verify alignment**: Ensure the existing helper uses `node.id` as fallback (currently falls back to `"userInput"` — update to fall back to node id for consistency with requirement 2.2)

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm that `handleInputNodeResume()` always stores under `"mobile_no"` regardless of node config.

**Test Plan**: Write unit tests that create a `ChatSession` with context containing `"_inputVariableName" = "email"`, invoke `handleInputNodeResume()`, and assert the reply is stored under `"email"`. Run on UNFIXED code to observe failures.

**Test Cases**:
1. **Email variable test**: Input node with `variableName = "email"`, reply `"user@test.com"` — assert context has key `"email"` (will fail on unfixed code, stored under `"mobile_no"`)
2. **Order ID variable test**: Input node with `variableName = "order_id"`, reply `"ORD-999"` — assert context has key `"order_id"` (will fail on unfixed code)
3. **No variableName test**: Input node with no `variableName`, node id = `"node-abc"` — assert context has key `"node-abc"` (will fail on unfixed code)
4. **Multi-node sequential test**: Two input nodes in sequence — assert both values preserved (will fail on unfixed code)

**Expected Counterexamples**:
- All replies stored under `"mobile_no"` regardless of configured variable name
- Second input node overwrites first node's value in context

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  // Setup: InputNodeProcessor stores _inputVariableName in context
  result := handleInputNodeResume_fixed(session, sessionId, message)
  ASSERT session.context.get(input.expectedKey) == message
  ASSERT session.context.get("_inputVariableName") == NULL
  ASSERT session.context.get("mobile_no") != message  // unless expectedKey IS "mobile_no"
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  // When variableName IS "mobile_no", behavior is identical
  ASSERT handleInputNodeResume_original(input) = handleInputNodeResume_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code for input nodes with `variableName = "mobile_no"`, then write property-based tests confirming the fixed code produces identical context state for those inputs.

**Test Cases**:
1. **mobile_no preservation**: Input node with `variableName = "mobile_no"` stores reply under `"mobile_no"` — same as before
2. **Pause mechanics preservation**: Verify `currentNodeType`, `currentNodeId`, PAUSE action, and prompt message are unchanged by the fix
3. **Resume flow preservation**: Verify workflow loading, next node resolution, session persistence, and continuation are unchanged
4. **API node key preservation**: Verify `_displayVariable` and `_buttonOptions` handling in `handleApiNodeResume()` is unaffected

### Unit Tests

- Test `InputNodeProcessor.process()` stores `_inputVariableName` in context with correct value from `config.variableName`
- Test `InputNodeProcessor.process()` uses node id as fallback when `config.variableName` is null/empty
- Test `handleInputNodeResume()` reads `_inputVariableName` and stores reply under that key
- Test `handleInputNodeResume()` removes `_inputVariableName` from context after storage
- Test `handleInputNodeResume()` fallback when `_inputVariableName` is missing from context

### Property-Based Tests

- Generate random variable names (non-empty strings) and verify reply is always stored under the generated name
- Generate random sequences of input node configs and verify all values are preserved in context after processing
- Generate inputs with `variableName = "mobile_no"` and verify behavior matches original implementation exactly

### Integration Tests

- Test full workflow with three input nodes (`mobile_no`, `email`, `order_id`) collecting distinct values
- Test downstream message node resolving `{{email}}` placeholder after input node collects email
- Test downstream API node using `{{order_id}}` in URL placeholder after input node collects order ID
