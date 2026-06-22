# Unified Node Type Handling Bugfix Design

## Overview

The `ApiNodeProcessor.canHandle()` method checks `node.type == "api"` directly, while all other processors (`InputNodeProcessor`, `MessageNodeProcessor`, `WorkflowNodeProcessor`) check for `node.type == "state"` and differentiate via `config.nodeType`. This inconsistency breaks the architectural pattern where all workflow nodes share a common top-level type and are discriminated by their config. The fix aligns `ApiNodeProcessor` with the established pattern: check `type == "state"` first, then `config.nodeType == "api"`.

## Glossary

- **Bug_Condition (C)**: A node with `type: "state"` and `config.nodeType: "api"` is NOT routed to `ApiNodeProcessor` because `canHandle()` only matches `type == "api"`
- **Property (P)**: `ApiNodeProcessor.canHandle()` returns true when `type == "state"` AND `config.nodeType == "api"`, and returns false for all other inputs (including null config)
- **Preservation**: All other processors (`InputNodeProcessor`, `MessageNodeProcessor`, `WorkflowNodeProcessor`) continue to route nodes identically; `ApiNodeProcessor.process()` behavior remains unchanged; the resume handler in `WorkflowExecutionServiceImpl` continues to match on `session.getCurrentNodeType() == "api"`
- **NodeProcessor**: Interface with `canHandle(Map<String, Object> node)` and `process(...)` methods; Spring resolves processors via `@Order` annotation
- **canHandle**: Method on each processor that determines if the processor should handle a given node map
- **config.nodeType**: Field inside the node's `config` map used to discriminate between different processor types (e.g., `"input"`, `"workflow"`, `"api"`)

## Bug Details

### Bug Condition

The bug manifests when a workflow node is defined with `type: "state"` and `config.nodeType: "api"` (the consistent pattern used by all other processors). The `ApiNodeProcessor.canHandle()` method fails to match because it checks for `type == "api"` at the top level rather than inspecting `config.nodeType`.

**Formal Specification:**
```
FUNCTION isBugCondition(node)
  INPUT: node of type Map<String, Object>
  OUTPUT: boolean
  
  RETURN node.get("type") == "state"
         AND node.get("config") != null
         AND node.get("config").get("nodeType") == "api"
         AND ApiNodeProcessor.canHandle(node) == false
END FUNCTION
```

### Examples

- Node `{type: "state", config: {nodeType: "api", apiConfigId: 5}}` → **Expected**: routed to `ApiNodeProcessor` | **Actual**: NOT matched, falls through to `MessageNodeProcessor` (as a fallback since config has a nodeType key, it won't match MessageNodeProcessor either — node is unhandled)
- Node `{type: "api", config: {apiConfigId: 5}}` → **Expected** (after fix): NOT routed to `ApiNodeProcessor` (legacy format deprecated) | **Actual** (before fix): routed to `ApiNodeProcessor`
- Node `{type: "state", config: null}` → **Expected**: `ApiNodeProcessor.canHandle()` returns false | **Actual**: Not reached (returns false due to `"api" != "state"` check in current code, but post-fix must still return false with null-safe config check)
- Node `{type: "state", config: {nodeType: "api", apiConfigId: 10, displayVariable: "options"}}` → **Expected**: routed to `ApiNodeProcessor` for interactive selection | **Actual**: unhandled

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- `InputNodeProcessor.canHandle()` continues to match nodes with `type: "state"` and `config.nodeType: "input"` (Order 1)
- `MessageNodeProcessor.canHandle()` continues to match nodes with `type: "state"` and config null or without `nodeType` key (Order 2)
- `WorkflowNodeProcessor.canHandle()` continues to match nodes with `type: "state"` and `config.nodeType: "workflow"` (Order 4)
- `ApiNodeProcessor.process()` internal logic (HTTP execution, response extraction, conditional branching, interactive selection, button routing) remains identical
- `WorkflowExecutionServiceImpl.handleApiNodeResume()` continues to match on `"api".equals(session.getCurrentNodeType())` — no change needed since `ApiNodeProcessor.process()` already sets `session.setCurrentNodeType("api")`
- Session persistence fields (`currentNodeId`, `currentNodeType`, `currentType`) retain their semantics

**Scope:**
All inputs that do NOT involve the `canHandle()` discrimination logic should be completely unaffected by this fix. This includes:
- All `process()` method internals across all processors
- WebSocket message handling and STOMP routing
- Workflow resolution and transition traversal
- HTTP execution, placeholder resolution, condition evaluation

## Hypothesized Root Cause

Based on the code analysis, the root cause is straightforward:

1. **Inconsistent Pattern in `ApiNodeProcessor.canHandle()`**: When API node processing was originally implemented, it used a dedicated top-level type (`"api"`) rather than following the `"state"` + `config.nodeType` pattern established by `InputNodeProcessor` and `WorkflowNodeProcessor`. The `MessageNodeProcessor` acts as the fallback for `"state"` nodes without a `nodeType` config.

2. **No Null-Safety Concern in Current Code**: The current implementation never reaches the config check since it short-circuits on `type == "api"`. After the fix, the code must safely handle null config (return false) before attempting to read `config.get("nodeType")`.

3. **Order Annotation is Correct**: `ApiNodeProcessor` is at `@Order(3)`, which is correct — it should run after `InputNodeProcessor` (Order 1) and `MessageNodeProcessor` (Order 2), but before `WorkflowNodeProcessor` (Order 4). The order doesn't need to change since `canHandle()` is mutually exclusive across processors.

4. **No Workflow JSON Migration Needed at Runtime**: The fix assumes workflow JSON will be updated to use `type: "state"` with `config.nodeType: "api"` instead of `type: "api"`. Existing workflow definitions stored in the database need a data migration or the frontend needs to produce the new format.

## Correctness Properties

Property 1: Bug Condition - ApiNodeProcessor Accepts Unified Format

_For any_ node map where `type == "state"` AND `config != null` AND `config.nodeType == "api"`, the fixed `ApiNodeProcessor.canHandle()` SHALL return `true`.

**Validates: Requirements 2.1, 2.3**

Property 2: Preservation - Non-API Nodes Rejected

_For any_ node map where `type != "state"` OR `config == null` OR `config.nodeType != "api"`, the fixed `ApiNodeProcessor.canHandle()` SHALL return `false`, preserving the mutual exclusivity of processor routing and ensuring no regression in `InputNodeProcessor`, `MessageNodeProcessor`, or `WorkflowNodeProcessor` routing.

**Validates: Requirements 2.2, 2.4, 3.1, 3.2, 3.3**

## Fix Implementation

### High-Level Design

The fix is isolated to the `canHandle()` method of `ApiNodeProcessor`. The processor registration order (`@Order(3)`) remains unchanged. The discrimination logic aligns with the pattern used by `InputNodeProcessor` and `WorkflowNodeProcessor`:

```
┌─────────────────────────┐
│    Node (type: "state") │
└────────────┬────────────┘
             │
    ┌────────▼────────┐
    │ InputNodeProc   │  Order 1: config.nodeType == "input"
    │ (canHandle?)    │
    └────────┬────────┘
             │ false
    ┌────────▼────────┐
    │ MessageNodeProc │  Order 2: config == null || !config.containsKey("nodeType")
    │ (canHandle?)    │
    └────────┬────────┘
             │ false
    ┌────────▼────────┐
    │ ApiNodeProc     │  Order 3: config.nodeType == "api"  ← FIX HERE
    │ (canHandle?)    │
    └────────┬────────┘
             │ false
    ┌────────▼────────┐
    │ WorkflowNodeProc│  Order 4: config.nodeType == "workflow"
    │ (canHandle?)    │
    └────────┬────────┘
```

### Low-Level Design

#### Changes Required

**File**: `src/main/java/com/xpressbees/chatbot/processor/ApiNodeProcessor.java`

**Method**: `canHandle(Map<String, Object> node)`

**Current Code:**
```java
@Override
public boolean canHandle(Map<String, Object> node) {
    Object type = node.get("type");
    return "api".equals(type);
}
```

**Fixed Code:**
```java
@Override
@SuppressWarnings("unchecked")
public boolean canHandle(Map<String, Object> node) {
    String type = (String) node.get("type");
    if (!"state".equals(type)) {
        return false;
    }
    Map<String, Object> config = (Map<String, Object>) node.get("config");
    return config != null && "api".equals(config.get("nodeType"));
}
```

**Specific Changes**:
1. **Type Check**: Change from `"api".equals(type)` to `"state".equals(type)` — aligns with all other processors
2. **Config Extraction**: Extract `config` map from the node (same pattern as `InputNodeProcessor`, `WorkflowNodeProcessor`)
3. **Null-Safe Config Check**: Return `false` if config is null (prevents NPE, satisfies requirement 2.4)
4. **NodeType Discrimination**: Check `"api".equals(config.get("nodeType"))` — mirrors `"input".equals(config.get("nodeType"))` in `InputNodeProcessor`
5. **SuppressWarnings**: Add `@SuppressWarnings("unchecked")` for the config cast (consistent with other processors)

**No changes needed in**:
- `ApiNodeProcessor.process()` — already reads `config` from the node internally
- `session.setCurrentNodeType("api")` calls — these set the session field correctly for resume handling
- `WorkflowExecutionServiceImpl.handleApiNodeResume()` — matches on `"api".equals(nodeType)` from session, unaffected
- `@Order(3)` annotation — order remains correct for processor chain

**Workflow JSON Migration** (data concern, separate from code fix):
- Existing workflow definitions in the database must be updated: nodes with `type: "api"` should become `type: "state"` with `config.nodeType: "api"` added to their config map
- Frontend workflow editor must produce the new format going forward

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm that `ApiNodeProcessor.canHandle()` rejects nodes in the unified format.

**Test Plan**: Write property tests that generate nodes with `type: "state"` and `config.nodeType: "api"`, then assert `canHandle()` returns true. Run on UNFIXED code to observe failures and confirm the root cause.

**Test Cases**:
1. **Unified Format Test**: Generate node `{type: "state", config: {nodeType: "api", apiConfigId: N}}` — assert `canHandle()` returns true (will fail on unfixed code)
2. **Unified Format with Display Variable**: Generate node with `displayVariable` set — assert `canHandle()` returns true (will fail on unfixed code)
3. **Minimal Config Test**: Generate node `{type: "state", config: {nodeType: "api"}}` without other config fields — assert `canHandle()` returns true (will fail on unfixed code)

**Expected Counterexamples**:
- All nodes with `type: "state"` and `config.nodeType: "api"` will produce `canHandle() == false` on unfixed code
- Root cause confirmed: the method only checks `"api".equals(node.get("type"))` and never inspects config

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL node WHERE isBugCondition(node) DO
  result := ApiNodeProcessor_fixed.canHandle(node)
  ASSERT result == true
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function correctly returns false.

**Pseudocode:**
```
FOR ALL node WHERE NOT isBugCondition(node) DO
  ASSERT ApiNodeProcessor_fixed.canHandle(node) == false
END FOR
```

**Testing Approach**: Property-based testing (jqwik) is recommended for preservation checking because:
- It generates many random node configurations across the input domain
- It catches edge cases like null config, empty config, missing nodeType key
- It provides strong guarantees that the fix doesn't accidentally accept nodes meant for other processors

**Test Plan**: Generate random node maps with various type/config combinations and verify only the correct combination produces `true`.

**Test Cases**:
1. **Null Config Preservation**: Generate nodes with `type: "state"` and `config: null` → verify returns false (MessageNodeProcessor should handle these)
2. **Other NodeType Preservation**: Generate nodes with `type: "state"` and `config.nodeType` ∈ {"input", "workflow", "other"} → verify returns false
3. **Non-State Type Preservation**: Generate nodes with `type` ∈ {"api", "message", "random"} → verify returns false regardless of config
4. **Missing NodeType Key**: Generate nodes with `type: "state"` and config without `nodeType` key → verify returns false

### Unit Tests

- Test `canHandle()` returns true for `{type: "state", config: {nodeType: "api"}}`
- Test `canHandle()` returns false for `{type: "api"}` (legacy format)
- Test `canHandle()` returns false for `{type: "state", config: null}`
- Test `canHandle()` returns false for `{type: "state", config: {nodeType: "input"}}`
- Test `canHandle()` returns false for `{type: "state", config: {nodeType: "workflow"}}`
- Test `canHandle()` returns false for `{type: "state", config: {}}` (no nodeType key)
- Test `canHandle()` returns false for null type

### Property-Based Tests

- Generate random `config.nodeType` strings and verify only `"api"` produces true (with `type: "state"`)
- Generate random `type` strings and verify only `"state"` (with correct config) produces true
- Generate random node maps (all combinations of present/absent/null type and config fields) and verify mutual exclusivity with `InputNodeProcessor`, `MessageNodeProcessor`, `WorkflowNodeProcessor`
- Verify that for any generated node, exactly one processor's `canHandle()` returns true (classification property)

### Integration Tests

- Test full workflow execution with a node using `{type: "state", config: {nodeType: "api", apiConfigId: N}}` — verify HTTP call is made
- Test workflow with mixed node types (message → input → api → workflow) using unified format — verify correct routing
- Test session resume after API node pause — verify `handleApiNodeResume()` is triggered correctly
