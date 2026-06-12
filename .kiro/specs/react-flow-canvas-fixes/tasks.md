# Implementation Plan

## Overview

This plan fixes six bugs in the React Flow canvas implementation using the bug condition methodology: explore (confirm bugs via failing tests), preserve (capture non-buggy behavior), implement (apply fixes), and validate (verify fixes pass and no regressions). All property-based tests use `fast-check` and live in `frontend/src/__tests__/`.

## Tasks

- [-] 1. Write bug condition exploration tests
  - **Property 1: Bug Condition** - React Flow Canvas Six-Bug Exploration
  - **IMPORTANT**: Write these property-based tests BEFORE implementing any fixes
  - **CRITICAL**: These tests MUST FAIL on unfixed code - failure confirms the bugs exist
  - **DO NOT attempt to fix the tests or the code when they fail**
  - **NOTE**: These tests encode the expected behavior - they will validate the fixes when they pass after implementation
  - **GOAL**: Surface counterexamples that demonstrate all six bugs exist
  - **File**: `frontend/src/__tests__/canvas-bugs.properties.test.ts`
  - **Scoped PBT Approach**: For each bug, scope the property to concrete failing cases:
    - Handle Size: Generate random StateType values, render StateNode, assert handle computed width/height = 12px (will FAIL with 50px on unfixed code)
    - Condition Overlap: Render Condition StateNode, collect all handle bounding rects, assert no two overlap (will FAIL on unfixed code — target-left and false both at Position.Left)
    - Edge Delete Undo: Setup canvas state with transitions, fire edge remove change via onEdgesChange, assert undoStack contains DELETE_TRANSITION op (will FAIL — no undo op created on unfixed code)
    - Stale Closure: Create two connections in rapid succession from same source, assert no duplicate transitions in resulting state (will FAIL — stale closure allows duplicates on unfixed code)
    - isValidConnection: Assert self-loop connections return false from isValidConnection (will FAIL — isValidConnection not implemented on unfixed code)
    - Edge Label: Render NotionEdge with label="true", assert label text is rendered in DOM (will FAIL — label prop ignored on unfixed code)
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct - it proves the bugs exist)
  - Document counterexamples found:
    - Handle computed width = 50px instead of 12px
    - Two handle elements at identical positions on Condition nodes
    - undoStack.length unchanged after edge removal
    - Duplicate transitions after rapid connections
    - No isValidConnection prop / function not defined
    - No label element in NotionEdge render output
  - Mark task complete when tests are written, run, and failures are documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

- [~] 2. Write preservation property tests (BEFORE implementing fixes)
  - **Property 2: Preservation** - Non-Bug-Condition Behavior Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - **File**: `frontend/src/__tests__/canvas-preservation.properties.test.ts`
  - **Observation Phase** (run on UNFIXED code):
    - Observe: Non-Condition nodes (API_Call, Response, Input, Wait, Parallel, End) render handles at Position.Top, Position.Bottom, Position.Left, Position.Right with no custom offsets
    - Observe: MOVE_STATE, ADD_STATE, DELETE_STATE operations push correctly to undoStack
    - Observe: Edges without condition (label=undefined) render without any label element
    - Observe: Valid connections (non-self-loop, non-duplicate) are accepted by onConnect
  - **Property-Based Tests** (using fast-check):
    - Generate random non-Condition StateType values (`fc.constantFrom('API_Call', 'Response', 'Input', 'Wait', 'Parallel', 'End')`), render StateNode, verify handle positions unchanged (no Position.Bottom with left:25% offset applied to non-Condition nodes)
    - Generate random sequences of undo operations (ADD_STATE, MOVE_STATE, DELETE_STATE), verify undoStack length increments correctly and redo restores previous state
    - Generate random edge props with label=undefined, render NotionEdge, verify no label element exists in DOM
    - Generate random valid (source, target) pairs where source ≠ target and no existing transition matches, verify isValidConnection returns true and onConnect creates the transition
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

- [ ] 3. Fix for React Flow Canvas six-bug batch

  - [~] 3.1 Fix handle size conflict in StateNode.module.css
    - Change `.handle` width/height from 50px to 12px
    - Set `position: relative` on `.handle`
    - Update `::before` pseudo-element `inset` to `-10px` for 32px hit area
    - Keep `border-radius: 50%`, `background`, `border`, `z-index`, and `transition` unchanged
    - _Bug_Condition: isBugCondition_HandleSize(X) where X.nodeType = "stateNode" AND X.hasCustomHandleCSS = true_
    - _Expected_Behavior: handle.visualWidth = 12 AND handle.visualHeight = 12 AND handle.hitAreaWidth >= 28_
    - _Preservation: Non-Condition nodes retain existing handle positions with no offset changes_
    - _Requirements: 2.1, 3.1_

  - [~] 3.2 Fix Condition node handle overlap in StateNode.tsx
    - Move `false` source handle from `Position.Left` to `Position.Bottom`
    - Apply `style={{ left: '25%' }}` to position false handle at left quarter of bottom edge
    - Keep handle `id="false"` for backward compatibility with existing edge sourceHandle references
    - Remove redundant `source-bottom` handle from Condition branch (Condition only needs true/false outputs)
    - Keep `true` source handle at `Position.Right`
    - _Bug_Condition: isBugCondition_HandleOverlap(X) where X.stateType = "Condition"_
    - _Expected_Behavior: All Condition node handles have distinct positions (no pair shares same position AND offset)_
    - _Preservation: Non-Condition nodes (API_Call, Response, Input, Wait, Parallel, End) retain their existing handle positions_
    - _Requirements: 2.2, 3.1_

  - [~] 3.3 Integrate edge deletion with undo system in WorkflowCanvas.tsx
    - In `onEdgesChange` callback, filter for `type === 'remove'` changes
    - Before deleting from transitions Map, capture the full transition object
    - Push a `DELETE_TRANSITION` operation (containing the captured transition) onto undoStack
    - Clear redoStack on new operations
    - Cap undoStack at 50 entries (shift oldest if exceeded)
    - Update dependency array to `[updateCanvasState]`
    - _Bug_Condition: isBugCondition_EdgeDeleteNoUndo(X) where X.type = "remove"_
    - _Expected_Behavior: undoStack grows by 1 per removal, last entry type = "DELETE_TRANSITION"_
    - _Preservation: ADD_STATE, MOVE_STATE, DELETE_STATE operations continue to function identically_
    - _Requirements: 2.3, 3.2, 3.3, 3.5_

  - [~] 3.4 Fix stale closure in onConnect via transitions ref
    - Add `const transitionsRef = useRef(canvasState.transitions)` at component top level
    - Sync ref on every render: `transitionsRef.current = canvasState.transitions`
    - In `onConnect`, read `transitionsRef.current` instead of closing over `canvasState.transitions`
    - Remove `canvasState.transitions` from `onConnect` dependency array
    - Keep `addTransition` and `showToast` in dependency array
    - _Bug_Condition: isBugCondition_StaleClosure(X) where X.connectionCount >= 2 AND X.timeBetweenMs < 100_
    - _Expected_Behavior: No duplicate transitions after any rapid connection sequence_
    - _Preservation: Valid connections continue to create transitions with correct source, target, condition_
    - _Requirements: 2.4, 3.4_

  - [~] 3.5 Implement isValidConnection callback in WorkflowCanvas.tsx
    - Create `isValidConnection` callback using `useCallback`
    - Reject self-loops: `source === target` → return false
    - Reject duplicates: check `transitionsRef.current` for existing source→target pair → return false
    - Accept all other connections: return true
    - Pass `isValidConnection` as prop to `<ReactFlow>` component
    - Empty dependency array (reads from ref, so stable)
    - _Bug_Condition: isBugCondition_NoRealtimeValidation(X) where X.targetIsInvalid = true_
    - _Expected_Behavior: isValidConnection returns false for self-loops and duplicates, preventing snap_
    - _Preservation: Valid connections (non-self-loop, non-duplicate) continue to be accepted_
    - _Requirements: 2.5, 3.4_

  - [~] 3.6 Implement edge label rendering in NotionEdge.tsx
    - Import `EdgeLabelRenderer` from `@xyflow/react`
    - Extract `label` from EdgeProps destructuring
    - Destructure `labelX` and `labelY` from `getBezierPath` return value
    - Conditionally render `<EdgeLabelRenderer>` wrapper with label div when `label` is truthy
    - Position label with `transform: translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`
    - Style: 11px font, 500 weight, surface background, border, 4px radius, 2px 6px padding
    - Add `className="nodrag nopan"` to prevent label from interfering with canvas interactions
    - _Bug_Condition: isBugCondition_NoEdgeLabel(X) where X.condition IN {"true", "false", "error"}_
    - _Expected_Behavior: Label text matching condition string is rendered at edge midpoint_
    - _Preservation: Edges without condition (label=undefined) render without any label element_
    - _Requirements: 2.6, 3.6_

  - [~] 3.7 Verify bug condition exploration tests now pass
    - **Property 1: Expected Behavior** - All Six Bug Fixes Validated
    - **IMPORTANT**: Re-run the SAME tests from task 1 - do NOT write new tests
    - The tests from task 1 encode the expected behavior for all six bugs
    - When these tests pass, it confirms the expected behavior is satisfied for each fix
    - Run `npm test -- canvas-bugs.properties` from `frontend/`
    - **EXPECTED OUTCOME**: All tests PASS (confirms bugs are fixed)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [~] 3.8 Verify preservation tests still pass
    - **Property 2: Preservation** - No Regressions Introduced
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run `npm test -- canvas-preservation.properties` from `frontend/`
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all preservation properties still hold after fixes:
      - Non-Condition node handles unchanged
      - Undo/redo for non-edge operations unchanged
      - Unconditional edges render without labels
      - Valid connections still accepted

- [~] 4. Write integration tests for end-to-end canvas flows
  - **File**: `frontend/src/__tests__/canvas-fixes-integration.test.ts`
  - Test: Add Condition node → draw true edge → draw false edge → verify both labels visible and distinct
  - Test: Delete an edge via keyboard → press Ctrl+Z → verify edge is restored with correct source/target/condition
  - Test: Rapid-fire 5 connections from same source to different targets → verify all 5 unique transitions created, no duplicates
  - Test: Drag connection to self → verify isValidConnection returns false → verify no self-loop created
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [~] 5. Checkpoint - Ensure all tests pass
  - Run full frontend test suite: `npm test` from `frontend/`
  - Verify all property-based tests pass (canvas-bugs.properties.test.ts)
  - Verify all preservation tests pass (canvas-preservation.properties.test.ts)
  - Verify all integration tests pass (canvas-fixes-integration.test.ts)
  - Verify TypeScript compilation: `npm run build` from `frontend/`
  - Ensure no regressions in existing test files
  - Ask the user if questions arise


## Notes

- Property-based tests use `fast-check` library (already installed in frontend)
- Test files follow project convention: `*.properties.test.ts` for PBT, `*.test.ts` for unit/integration
- All test files go in `frontend/src/__tests__/`
- Implementation targets: `StateNode.module.css`, `StateNode.tsx`, `WorkflowCanvas.tsx`, `NotionEdge.tsx`
- The six fixes are independent and can be implemented in any order within task 3, but tests (tasks 1-2) must come first

## Task Dependency Graph

```json
{
  "waves": [
    { "wave": 1, "tasks": ["1", "2"] },
    { "wave": 2, "tasks": ["3.1", "3.2", "3.3", "3.4", "3.5", "3.6"] },
    { "wave": 3, "tasks": ["3.7", "3.8"] },
    { "wave": 4, "tasks": ["4"] },
    { "wave": 5, "tasks": ["5"] }
  ]
}
```
