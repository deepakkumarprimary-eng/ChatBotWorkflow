# React Flow Canvas Fixes — Bugfix Design

## Overview

This design addresses six bugs in the React Flow canvas implementation that degrade usability of the Chatbot Workflow Builder's visual editor. The bugs range from CSS sizing issues (oversized handles), positional overlaps (Condition node handles), missing undo integration (edge deletion), stale closure risks (rapid connections), missing real-time validation (isValidConnection), and absent edge labels (condition branches). The fix strategy targets each bug with minimal, isolated changes to avoid regressions in unrelated canvas behaviors.

## Glossary

- **Bug_Condition (C)**: The set of inputs/states that trigger one of the six identified bugs
- **Property (P)**: The desired correct behavior when the bug condition holds
- **Preservation**: Existing behaviors that must remain unchanged after all fixes are applied
- **Handle**: A React Flow `<Handle>` element that serves as a connection endpoint on a node
- **StateNode**: The custom React Flow node component (`StateNode.tsx`) rendering workflow states
- **NotionEdge**: The custom React Flow edge component (`NotionEdge.tsx`) rendering transitions
- **onEdgesChange**: The React Flow callback in `WorkflowCanvas.tsx` handling edge removal events
- **onConnect**: The React Flow callback creating new transitions when a user draws an edge
- **isValidConnection**: A React Flow prop that evaluates connection validity during drag in real-time
- **CanvasOperation**: A discriminated union type representing undoable/redoable canvas actions
- **pushOp**: The function from `useUndoRedo` that applies an operation and pushes it to the undo stack
- **EdgeLabelRenderer**: A React Flow component that renders labels in a portal above the SVG edge layer

## Bug Details

### Bug Condition

The bugs manifest across six distinct conditions in the canvas implementation. A user encounters defective behavior when:

1. Any StateNode renders with custom handle CSS (handles are 50px instead of 12px)
2. A Condition node renders with both target-left and false source at Position.Left
3. An edge is removed via `onEdgesChange` (no undo operation is created)
4. Two connections are created within the same render cycle (stale closure)
5. A user drags a connection line toward any target (no real-time feedback)
6. A condition branch edge renders (no label text is displayed)

**Formal Specification:**

```
FUNCTION isBugCondition(input)
  INPUT: input of type CanvasInteraction
  OUTPUT: boolean

  RETURN isBugCondition_HandleSize(input)
         OR isBugCondition_HandleOverlap(input)
         OR isBugCondition_EdgeDeleteNoUndo(input)
         OR isBugCondition_StaleClosure(input)
         OR isBugCondition_NoRealtimeValidation(input)
         OR isBugCondition_NoEdgeLabel(input)
END FUNCTION
```

### Examples

- **Handle Size**: A StateNode renders with `.handle { width: 50px; height: 50px }` — the handle visually dominates the node and overlaps adjacent handles. Expected: 12px visual circle with 28px hit area via `::before`.
- **Condition Overlap**: A Condition node has `target-left` at Position.Left and `false` source at Position.Left — both render at the same Y-center on the left edge. Expected: `false` source moves to Position.Bottom so handles are spatially distinct.
- **Edge Delete No Undo**: User selects an edge, presses Delete. Edge disappears. User presses Ctrl+Z — nothing happens. Expected: Ctrl+Z restores the deleted edge.
- **Stale Closure**: User draws edge A→B, then immediately (within same render) draws A→C. The second `onConnect` call validates against stale `canvasState.transitions` that doesn't include A→B. Expected: validation sees the freshly-added A→B transition.
- **No Real-time Validation**: User drags from source handle toward a self-loop target. The connection line snaps to the handle with no visual rejection. Expected: connection line does not snap; cursor indicates invalid.
- **Missing Label**: Edge from Condition node's "true" handle renders as a plain bezier with no text. Expected: "true" label appears at the edge midpoint.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Non-Condition nodes (API_Call, Response, Input, Wait, Parallel, End) retain their existing handle positions (top, bottom, left, right) with no offset changes
- Node dragging continues to record MOVE_STATE undo operations
- State addition via palette drag-and-drop continues to record ADD_STATE undo operations
- Valid connections continue to create transitions with correct source, target, and condition fields
- State deletion continues to show confirmation dialog and record DELETE_STATE operations
- Edges without a condition continue to render without labels
- Zoom/pan respects 25%–400% range and snap-to-grid (16px)
- Node selection applies outline style and triggers onNodeSelect callback
- Mouse-based interactions (clicks, drags) are completely unaffected by keyboard/connection fixes

**Scope:**
All inputs that do NOT trigger any of the six bug conditions should produce identical behavior before and after the fix. This includes all mouse-based node interactions, non-Condition node rendering, unconditional edge rendering, and single-connection workflows.

## Hypothesized Root Cause

Based on code analysis, the root causes are:

1. **Handle Size Conflict** (`StateNode.module.css` line ~76): The `.handle` class sets `width: 50px; height: 50px` directly. The `::before` pseudo-element with `inset: -8px` exists for hit area expansion, but the base handle itself is oversized. The intent was likely 12px visual with the `::before` providing the extended grab area, but the width/height were left at a debug or placeholder value of 50px.

2. **Condition Node Handle Overlap** (`StateNode.tsx` lines ~119-127): The Condition node renders a `<Handle type="source" position={Position.Left} id="false">` AND the shared `<Handle type="target" position={Position.Left} id="target-left">`. Both resolve to the same DOM position (vertical center of the left edge). No CSS `top`/`transform` offset is applied to separate them.

3. **Edge Deletion Bypasses Undo** (`WorkflowCanvas.tsx` `onEdgesChange`): The callback simply deletes transitions from the Map without capturing the deleted transition object or pushing a `DELETE_TRANSITION` operation. Compare with `deleteTransition()` in the same file which correctly creates undo operations — `onEdgesChange` was written as a minimal handler and never integrated with the undo system.

4. **Stale Closure in onConnect** (`WorkflowCanvas.tsx` `onConnect`): The callback closes over `canvasState.transitions` directly via the dependency array. When React batches state updates, rapid successive calls to `onConnect` all see the same snapshot of `transitions` from the render in which the callback was created. The second connection's validation doesn't see the first connection.

5. **Missing isValidConnection**: The `<ReactFlow>` component in `WorkflowCanvas.tsx` does not receive an `isValidConnection` prop. React Flow defaults to allowing all connections during drag, only rejecting them in `onConnect`. This means users get no visual feedback until they release the mouse.

6. **Missing Edge Labels** (`NotionEdge.tsx`): The component destructures `EdgeProps` but does not use the `label` prop. It only renders `<BaseEdge>` with the computed path. The `label` prop passed by React Flow (derived from `transitionToEdge` which sets `label: transition.condition`) is silently ignored.

## Correctness Properties

Property 1: Bug Condition - Handle Visual Size

_For any_ rendered StateNode with custom handle CSS, each handle SHALL have a computed visual size of 12px × 12px (border-box) with a hit area of at least 28px × 28px provided by the `::before` pseudo-element.

**Validates: Requirements 2.1**

Property 2: Bug Condition - Condition Node Handle Separation

_For any_ Condition node rendered on the canvas, all handles SHALL occupy distinct positions such that no two handles share both the same `position` prop value AND the same CSS offset, ensuring each handle is independently accessible for connection.

**Validates: Requirements 2.2**

Property 3: Bug Condition - Edge Deletion Creates Undo Operation

_For any_ edge removal processed through `onEdgesChange`, the system SHALL push a `DELETE_TRANSITION` operation onto the undo stack containing the full transition object, enabling recovery via Ctrl+Z.

**Validates: Requirements 2.3**

Property 4: Bug Condition - Fresh State in Connection Validation

_For any_ sequence of connections created within the same event loop, the validation logic SHALL read the current (latest) transitions state rather than a closure-captured snapshot, preventing duplicate transitions.

**Validates: Requirements 2.4**

Property 5: Bug Condition - Real-time Connection Validation

_For any_ connection drag where the target would create an invalid edge (self-loop or duplicate), `isValidConnection` SHALL return `false`, preventing the connection line from snapping to the target handle.

**Validates: Requirements 2.5**

Property 6: Bug Condition - Edge Label Rendering

_For any_ edge with a condition label (true/false/error/timeout/fallback), the NotionEdge component SHALL render visible label text at the edge midpoint using EdgeLabelRenderer.

**Validates: Requirements 2.6**

Property 7: Preservation - Non-Condition Node Handles

_For any_ non-Condition node (API_Call, Response, Input, Wait, Parallel, End), the handle positions and offsets SHALL remain identical to the original implementation, preserving existing connection topology.

**Validates: Requirements 3.1**

Property 8: Preservation - Undo/Redo for Non-Edge Operations

_For any_ canvas operation that is NOT an edge deletion (node move, node add, state delete, config update), the undo/redo system SHALL continue to function identically to the original implementation.

**Validates: Requirements 3.2, 3.3, 3.5**

Property 9: Preservation - Unconditional Edge Rendering

_For any_ edge without a condition label, the NotionEdge component SHALL render without label text, preserving clean visual appearance for unconditional transitions.

**Validates: Requirements 3.6**

## Fix Implementation

### Changes Required

#### Fix 1: Handle Size — `StateNode.module.css`

**File**: `frontend/src/components/canvas/nodes/StateNode.module.css`

**Specific Changes**:
1. **Reduce `.handle` dimensions**: Change `width: 50px; height: 50px` to `width: 12px; height: 12px`
2. **Expand hit area via `::before`**: Update `inset: -8px` to `inset: -10px` — this creates a 32px × 32px invisible grab area (12px + 10px on each side) which is comfortable for mouse targeting without overlapping adjacent handles
3. **Ensure `position: relative`** on `.handle` so that `::before` with `position: absolute` anchors correctly

```css
.handle {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--color-surface);
  border: 1.5px solid var(--color-border-strong);
  z-index: 10;
  position: relative;
  transition:
    border-color var(--transition-standard),
    transform var(--transition-standard);
}

.handle::before {
  content: "";
  position: absolute;
  inset: -10px; /* 32px total hit area */
}
```

#### Fix 2: Condition Node Handle Repositioning — `StateNode.tsx`

**File**: `frontend/src/components/canvas/nodes/StateNode.tsx`

**Specific Changes**:
1. **Move `false` source handle** from `Position.Left` to `Position.Bottom` — this eliminates the overlap with `target-left`
2. **Update handle id** remains `"false"` for backward compatibility with existing edge `sourceHandle` references
3. **Adjust CSS class** to use `handleFalse` with a slight offset to visually separate from `source-bottom`

```tsx
{/* Condition nodes: true (right), false (bottom), plus general source-bottom removed */}
{data.stateType === 'Condition' && (
  <>
    <Handle
      type="source"
      position={Position.Bottom}
      id="false"
      className={`${styles.handle} ${styles.handleFalse}`}
      style={{ left: '25%' }}
    />
    <Handle
      type="source"
      position={Position.Right}
      id="true"
      className={`${styles.handle} ${styles.handleTrue}`}
    />
  </>
)}
```

The `style={{ left: '25%' }}` positions the false handle at the left quarter of the bottom edge, while the existing `source-bottom` handle (if retained for non-condition routing) would sit at center. Since Condition nodes only need true/false outputs, we remove the redundant `source-bottom` handle from the Condition branch.

#### Fix 3: Edge Deletion Undo Integration — `WorkflowCanvas.tsx`

**File**: `frontend/src/components/canvas/WorkflowCanvas.tsx`

**Function**: `onEdgesChange`

**Specific Changes**:
1. **Capture deleted transitions** before removing them from the Map
2. **Push DELETE_TRANSITION operations** to the undo stack for each removed edge
3. **Clear redo stack** as with all new operations
4. **Cap undo stack** at 50 entries

```typescript
const onEdgesChange = useCallback(
  (changes: EdgeChange[]) => {
    const removals = changes.filter((c) => c.type === 'remove');
    if (removals.length === 0) return;

    updateCanvasState((prev) => {
      const newTransitions = new Map(prev.transitions);
      let newUndoStack = [...prev.undoStack];

      for (const change of removals) {
        if (change.type === 'remove') {
          const transition = prev.transitions.get(change.id);
          if (transition) {
            newTransitions.delete(change.id);
            const operation: CanvasOperation = {
              type: 'DELETE_TRANSITION',
              transition,
            };
            newUndoStack.push(operation);
          }
        }
      }

      // Cap undo stack
      while (newUndoStack.length > 50) {
        newUndoStack.shift();
      }

      return {
        ...prev,
        transitions: newTransitions,
        undoStack: newUndoStack,
        redoStack: [],
      };
    });
  },
  [updateCanvasState]
);
```

#### Fix 4: Stale Closure Fix — `WorkflowCanvas.tsx`

**File**: `frontend/src/components/canvas/WorkflowCanvas.tsx`

**Function**: `onConnect`

**Specific Changes**:
1. **Add a ref** to hold the latest transitions: `const transitionsRef = useRef(canvasState.transitions)`
2. **Sync the ref** on every render: `transitionsRef.current = canvasState.transitions` (inside a `useEffect` or directly in render body)
3. **Read from ref** in `onConnect` instead of closing over `canvasState.transitions`
4. **Remove `canvasState.transitions`** from the `onConnect` dependency array

```typescript
// At component top level:
const transitionsRef = useRef(canvasState.transitions);
transitionsRef.current = canvasState.transitions;

// In onConnect:
const onConnect = useCallback(
  (connection: Connection) => {
    const { source, target, sourceHandle } = connection;
    if (!source || !target) return;

    // Read latest transitions from ref (not closure)
    const existingTransitions = Array.from(transitionsRef.current.values());
    const validation = canCreateTransition(existingTransitions, source, target);

    if (!validation.allowed) {
      showToast(validation.reason || 'Connection not allowed');
      return;
    }

    let condition: TransitionCondition | undefined;
    if (sourceHandle === 'true' || sourceHandle === 'false') {
      condition = sourceHandle;
    }

    const transition: Transition = {
      id: crypto.randomUUID(),
      source,
      target,
      condition,
    };

    addTransition(transition);
  },
  [addTransition, showToast]  // canvasState.transitions removed
);
```

#### Fix 5: isValidConnection Implementation — `WorkflowCanvas.tsx`

**File**: `frontend/src/components/canvas/WorkflowCanvas.tsx`

**Specific Changes**:
1. **Create `isValidConnection` callback** that checks for self-loops and duplicate transitions
2. **Pass it to `<ReactFlow>`** as a prop
3. **Use the transitionsRef** (from Fix 4) to read current state without stale closure issues

```typescript
const isValidConnection = useCallback(
  (connection: Connection) => {
    const { source, target } = connection;
    if (!source || !target) return false;

    // Reject self-loops
    if (source === target) return false;

    // Reject duplicates
    const existingTransitions = Array.from(transitionsRef.current.values());
    return !existingTransitions.some(
      (t) => t.source === source && t.target === target
    );
  },
  [] // stable — reads from ref
);

// In JSX:
<ReactFlow
  // ... existing props
  isValidConnection={isValidConnection}
/>
```

#### Fix 6: Edge Label Rendering — `NotionEdge.tsx`

**File**: `frontend/src/components/canvas/edges/NotionEdge.tsx`

**Specific Changes**:
1. **Import `EdgeLabelRenderer`** from `@xyflow/react`
2. **Extract `label`** from `EdgeProps` (it's passed via the `...props` spread currently)
3. **Render label conditionally** — only when `label` is a non-empty string
4. **Position label at midpoint** using `transform` with `labelX`/`labelY` from `getBezierPath`

```typescript
import {
  BaseEdge,
  EdgeLabelRenderer,
  getBezierPath,
  type EdgeProps,
} from '@xyflow/react';

export function NotionEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  label,
  ...props
}: EdgeProps) {
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });

  return (
    <>
      <BaseEdge id={id} path={edgePath} {...props} />
      {label && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              fontSize: '11px',
              fontWeight: 500,
              background: 'var(--color-surface)',
              border: '1px solid var(--color-border)',
              borderRadius: '4px',
              padding: '2px 6px',
              pointerEvents: 'all',
            }}
            className="nodrag nopan"
          >
            {label}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}
```

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate each bug on unfixed code, then verify the fixes work correctly and preserve existing behavior. Property-based tests use `fast-check` to generate diverse inputs.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bugs BEFORE implementing fixes. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write tests that exercise each bug condition against the unfixed code to observe failures and confirm the root cause.

**Test Cases**:
1. **Handle Size Test**: Render a StateNode, query the `.handle` element's computed style — assert width/height are NOT 12px (will fail on unfixed code, confirming the 50px bug)
2. **Condition Overlap Test**: Render a Condition StateNode, collect all handle elements' bounding rects — assert overlap exists (will fail assertion that handles are distinct on unfixed code)
3. **Edge Delete Undo Test**: Setup canvas with an edge, fire an edge remove change, check `undoStack` — assert it does NOT contain a DELETE_TRANSITION op (will pass on unfixed code, confirming missing undo)
4. **Stale Closure Test**: Create two connections in rapid succession within the same state snapshot, check for duplicate transitions (will produce duplicates on unfixed code)
5. **isValidConnection Test**: Check that `<ReactFlow>` receives no `isValidConnection` prop (will confirm missing validation on unfixed code)
6. **Edge Label Test**: Render NotionEdge with `label="true"`, query for label text — assert it's NOT rendered (will confirm on unfixed code)

**Expected Counterexamples**:
- Handle computed width = 50px instead of 12px
- Two handle elements at identical bounding rect positions on Condition nodes
- undoStack.length unchanged after edge removal
- Duplicate transitions in state after rapid connections
- No isValidConnection prop on ReactFlow component
- No label element in NotionEdge render output

### Fix Checking

**Goal**: Verify that for all inputs where any bug condition holds, the fixed code produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := fixedFunction(input)
  ASSERT expectedBehavior(result)
END FOR
```

**Specific Assertions:**
- Handle visual width = 12px AND hit area >= 28px
- All Condition node handles have distinct bounding rects (no pair overlaps)
- undoStack grows by 1 for each edge removal with type = DELETE_TRANSITION
- No duplicate transitions exist after any sequence of rapid connections
- isValidConnection returns false for self-loops and duplicate edges
- EdgeLabelRenderer renders label text matching the condition string

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalFunction(input) = fixedFunction(input)
END FOR
```

**Testing Approach**: Property-based testing with fast-check is recommended for preservation checking because:
- It generates many random canvas states and operations to verify no regression
- It catches edge cases in undo/redo sequencing that manual tests might miss
- It provides strong guarantees that non-Condition nodes, unconditional edges, and existing undo operations are unaffected

**Test Plan**: Observe behavior on UNFIXED code first for non-bug inputs, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Non-Condition Handle Preservation**: Generate random non-Condition StateType nodes, render them, verify handle positions are unchanged (top, bottom, left, right) with no offsets
2. **Undo/Redo Stack Preservation**: Generate random sequences of ADD_STATE, MOVE_STATE, DELETE_STATE, ADD_TRANSITION operations — verify undo/redo continues to function identically
3. **Unconditional Edge Label Preservation**: Generate edges with no condition (label = undefined) — verify NotionEdge renders without any label element
4. **Valid Connection Preservation**: Generate valid connections (non-self-loop, non-duplicate) — verify onConnect and isValidConnection both accept them

### Unit Tests

- Render StateNode with each StateType, measure handle element dimensions (12px × 12px)
- Render Condition StateNode, verify false handle is at Position.Bottom with `left: 25%`
- Call onEdgesChange with a remove change, verify undoStack contains DELETE_TRANSITION
- Call undo after edge deletion, verify transition is restored
- Call onConnect twice rapidly with same source, verify second is rejected
- Call isValidConnection with self-loop, verify returns false
- Call isValidConnection with duplicate, verify returns false
- Call isValidConnection with valid connection, verify returns true
- Render NotionEdge with label="true", verify label text is rendered
- Render NotionEdge without label, verify no label element exists

### Property-Based Tests

- Generate random StateType values and verify handle dimensions are always 12px visual (fast-check)
- Generate random Condition node handle configurations and verify no two handles overlap (fast-check)
- Generate random sequences of edge additions and deletions, verify undoStack length equals total operations (fast-check)
- Generate random pairs of (source, target) and verify isValidConnection correctly identifies self-loops and duplicates (fast-check)
- Generate random edge props with/without labels and verify NotionEdge renders label if and only if condition is present (fast-check)

### Integration Tests

- Full canvas flow: add Condition node → draw true edge → draw false edge → verify both labels visible and distinct
- Full canvas flow: delete an edge via keyboard → press Ctrl+Z → verify edge is restored with correct source/target/condition
- Full canvas flow: rapid-fire 5 connections from same source to different targets → verify all 5 unique transitions created, no duplicates
- Full canvas flow: drag connection to self → verify connection line does not snap → release → verify no self-loop created
