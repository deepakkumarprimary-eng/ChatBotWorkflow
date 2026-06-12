# Bugfix Requirements Document

## Introduction

This spec addresses six bugs in the React Flow canvas implementation of the Chatbot Workflow Builder. The issues span CSS handle sizing conflicts, overlapping handles on Condition nodes, missing undo/redo for edge deletion, stale closure risk in connection validation, missing real-time connection validation, and missing edge labels for condition branches. Together these bugs degrade canvas usability — users struggle to connect edges precisely, lose work without undo support, and cannot visually distinguish condition branches.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN a custom StateNode is rendered THEN the system applies 50px × 50px handle dimensions from StateNode.module.css, creating oversized invisible hit areas that overlap adjacent handles and make precise edge connections difficult

1.2 WHEN a Condition node is rendered THEN the system places a target handle (id: "target-left") and a source handle (id: "false") both at Position.Left without CSS offset, causing them to render directly on top of each other and making the false-branch source handle unreachable

1.3 WHEN an edge is deleted via keyboard shortcut or React Flow's built-in remove action THEN the system applies the removal in onEdgesChange without creating an undo operation, making the deletion permanent and unrecoverable via Ctrl+Z

1.4 WHEN two connections are created in rapid succession THEN the system validates the second connection against a stale closure of canvasState.transitions captured at the time of the first render, potentially allowing duplicate transitions that violate the single-connection-per-source constraint

1.5 WHEN a user drags a connection line toward an invalid target THEN the system does not provide real-time feedback because isValidConnection is not implemented, allowing the connection line to snap to any handle regardless of validity

1.6 WHEN a condition branch edge (true/false/error) is drawn between nodes THEN the system does not render a label on the edge because NotionEdge ignores the label prop, making it impossible for users to visually distinguish between true, false, and error branches

### Expected Behavior (Correct)

2.1 WHEN a custom StateNode is rendered THEN the system SHALL apply consistent handle dimensions (12px × 12px visual size with a larger invisible hit area via ::before pseudo-element) so that handles are visually small but easy to click without overlapping each other

2.2 WHEN a Condition node is rendered THEN the system SHALL place the false-branch source handle at Position.Bottom (or apply a CSS offset) so that no two handles occupy the same physical position, and both the target-left and false-branch handles are independently accessible

2.3 WHEN an edge is deleted via keyboard shortcut or React Flow's built-in remove action THEN the system SHALL create a DELETE_TRANSITION undo operation before applying the removal, enabling the user to recover the edge via Ctrl+Z

2.4 WHEN a connection is created THEN the system SHALL read the current transitions from the latest state (via functional updater or ref) rather than a closure-captured value, ensuring validation always runs against up-to-date data even during rapid successive connections

2.5 WHEN a user drags a connection line toward a target THEN the system SHALL evaluate validity in real-time via the isValidConnection callback and prevent the connection line from snapping to invalid targets, providing immediate visual feedback

2.6 WHEN a condition branch edge (true/false/error) is drawn between nodes THEN the system SHALL render a visible text label on the edge indicating the branch condition, allowing users to distinguish between branches at a glance

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a non-Condition node (API_Call, Response, Input, Wait, Parallel) is rendered THEN the system SHALL CONTINUE TO display source handles at Position.Bottom and Position.Right without any offset or repositioning

3.2 WHEN a user drags a node on the canvas THEN the system SHALL CONTINUE TO record a MOVE_STATE undo operation and allow Ctrl+Z to restore the original position

3.3 WHEN a user adds a new state via drag-and-drop from the palette THEN the system SHALL CONTINUE TO record an ADD_STATE undo operation

3.4 WHEN a valid connection is drawn between two nodes THEN the system SHALL CONTINUE TO create a transition with the correct source, target, and condition fields

3.5 WHEN a user deletes a state THEN the system SHALL CONTINUE TO show a confirmation dialog and record a DELETE_STATE undo operation that also captures associated transitions

3.6 WHEN edges without a condition are rendered THEN the system SHALL CONTINUE TO render them without a label (no visual noise for unconditional transitions)

3.7 WHEN the canvas is zoomed or panned THEN the system SHALL CONTINUE TO respect the 25%-400% zoom range and snap-to-grid behavior

3.8 WHEN a node is selected via click THEN the system SHALL CONTINUE TO apply the selected outline style and notify the parent via onNodeSelect

---

## Bug Condition Derivation

### Bug 1: Handle Size Conflict

```pascal
FUNCTION isBugCondition_HandleSize(X)
  INPUT: X of type RenderedNode
  OUTPUT: boolean
  
  RETURN X.nodeType = "stateNode" AND X.hasCustomHandleCSS = true
END FUNCTION
```

```pascal
// Property: Fix Checking - Handle Size
FOR ALL X WHERE isBugCondition_HandleSize(X) DO
  handle ← getRenderedHandle(X)
  ASSERT handle.visualWidth = 12 AND handle.visualHeight = 12
  ASSERT handle.hitAreaWidth >= 24 AND handle.hitAreaHeight >= 24
END FOR
```

### Bug 2: Condition Node Handle Overlap

```pascal
FUNCTION isBugCondition_HandleOverlap(X)
  INPUT: X of type RenderedNode
  OUTPUT: boolean
  
  RETURN X.stateType = "Condition"
END FUNCTION
```

```pascal
// Property: Fix Checking - No Handle Overlap
FOR ALL X WHERE isBugCondition_HandleOverlap(X) DO
  handles ← getAllHandles(X)
  FOR EACH pair (h1, h2) IN handles WHERE h1.id ≠ h2.id DO
    ASSERT h1.position ≠ h2.position OR h1.cssOffset ≠ h2.cssOffset
  END FOR
END FOR
```

### Bug 3: Edge Deletion Bypasses Undo

```pascal
FUNCTION isBugCondition_EdgeDeleteNoUndo(X)
  INPUT: X of type EdgeChange
  OUTPUT: boolean
  
  RETURN X.type = "remove"
END FUNCTION
```

```pascal
// Property: Fix Checking - Edge Deletion Creates Undo Op
FOR ALL X WHERE isBugCondition_EdgeDeleteNoUndo(X) DO
  stateBefore ← getCanvasState()
  applyEdgeChange(X)
  stateAfter ← getCanvasState()
  ASSERT stateAfter.undoStack.length = stateBefore.undoStack.length + 1
  ASSERT stateAfter.undoStack.last.type = "DELETE_TRANSITION"
END FOR
```

### Bug 4: Stale Closure in onConnect

```pascal
FUNCTION isBugCondition_StaleClosure(X)
  INPUT: X of type ConnectionSequence
  OUTPUT: boolean
  
  RETURN X.connectionCount >= 2 AND X.timeBetweenMs < 100
END FUNCTION
```

```pascal
// Property: Fix Checking - Fresh State in Validation
FOR ALL X WHERE isBugCondition_StaleClosure(X) DO
  result ← applyConnections(X.connections)
  ASSERT noDuplicateTransitions(result.transitions)
END FOR
```

### Bug 5: Missing isValidConnection

```pascal
FUNCTION isBugCondition_NoRealtimeValidation(X)
  INPUT: X of type DragConnection
  OUTPUT: boolean
  
  RETURN X.targetIsInvalid = true
END FUNCTION
```

```pascal
// Property: Fix Checking - Real-time Validation
FOR ALL X WHERE isBugCondition_NoRealtimeValidation(X) DO
  result ← isValidConnection(X.connection)
  ASSERT result = false
END FOR
```

### Bug 6: Missing Edge Labels

```pascal
FUNCTION isBugCondition_NoEdgeLabel(X)
  INPUT: X of type Edge
  OUTPUT: boolean
  
  RETURN X.condition IN {"true", "false", "error"}
END FUNCTION
```

```pascal
// Property: Fix Checking - Edge Labels Rendered
FOR ALL X WHERE isBugCondition_NoEdgeLabel(X) DO
  rendered ← renderEdge(X)
  ASSERT rendered.labelText = X.condition
END FOR
```

### Preservation Property

```pascal
// Property: Preservation Checking
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT F(X) = F'(X)
END FOR
```

This ensures that for all non-buggy inputs (non-Condition nodes, edges without conditions, valid connections at normal pace, etc.), the fixed code behaves identically to the original.
