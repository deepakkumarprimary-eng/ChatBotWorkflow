/**
 * Property-based tests for canvas operations using fast-check.
 * Tests correctness properties that must hold for all valid inputs.
 *
 * Feature: chatbot-workflow-builder
 * Validates: Requirements 1.4, 1.6, 1.7, 1.8
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import type {
  CanvasState,
  CanvasOperation,
  Transition,
  WorkflowState,
  StateType,
  StateConfig,
  Position,
} from '../types/canvas.types';
import {
  clampZoom,
  canCreateTransition,
  deleteStateWithTransitions,
  applyOperation,
  undoOperation,
  pushOperation,
  MIN_ZOOM,
  MAX_ZOOM,
} from '../utils/canvasOperations';

// --- Helpers & Arbitraries ---

const stateTypes: StateType[] = [
  'API_Call', 'Condition', 'Response', 'Input', 'Wait', 'Parallel', 'End',
];

function defaultConfigForType(type: StateType): StateConfig {
  switch (type) {
    case 'API_Call':
      return { type: 'API_Call', method: 'GET', url: 'http://example.com', headers: {}, body: '', responseMapping: {}, timeout: 30 };
    case 'Condition':
      return { type: 'Condition', expression: 'x == 1' };
    case 'Response':
      return { type: 'Response', messageTemplate: 'Hello' };
    case 'Input':
      return { type: 'Input', prompt: 'Enter value', variableName: 'input', timeout: 300 };
    case 'Wait':
      return { type: 'Wait', duration: 60 };
    case 'Parallel':
      return { type: 'Parallel', branches: [] };
    case 'End':
      return { type: 'End' };
  }
}

/** Arbitrary for a non-empty state ID string */
const arbStateId = fc.string({ minLength: 1, maxLength: 20 }).filter(s => s.trim().length > 0);

/** Arbitrary for a WorkflowState */
const arbWorkflowState: fc.Arbitrary<WorkflowState> = fc.record({
  id: fc.uuid(),
  type: fc.constantFrom(...stateTypes),
  name: fc.string({ minLength: 1, maxLength: 50 }),
  position: fc.record({ x: fc.integer({ min: 0, max: 2000 }), y: fc.integer({ min: 0, max: 2000 }) }),
}).map(({ id, type, name, position }) => ({
  id,
  type,
  name,
  position,
  config: defaultConfigForType(type),
}));

/** Create an empty canvas state */
function emptyCanvasState(): CanvasState {
  return {
    states: new Map(),
    transitions: new Map(),
    selectedStateId: null,
    zoom: 1,
    panOffset: { x: 0, y: 0 },
    undoStack: [],
    redoStack: [],
    contextVariables: [],
  };
}

// =============================================================================
// Property 1: Self-loop and duplicate transition rejection
// =============================================================================

describe('Property 1: Self-loop and duplicate transition rejection', () => {
  /**
   * **Validates: Requirements 1.4**
   *
   * For any state ID, attempting to create a transition from that state to itself
   * should be rejected. For any existing transition, attempting to create a duplicate
   * with the same source and target should be rejected.
   */

  it('rejects self-loop transitions for any state ID', () => {
    fc.assert(
      fc.property(arbStateId, (stateId) => {
        const result = canCreateTransition([], stateId, stateId);
        expect(result.allowed).toBe(false);
        expect(result.reason).toBeDefined();
      }),
      { numRuns: 100 }
    );
  });

  it('rejects duplicate transitions', () => {
    fc.assert(
      fc.property(
        arbStateId,
        arbStateId.filter(s => s.trim().length > 0),
        (source, target) => {
          fc.pre(source !== target);
          const existingTransition: Transition = {
            id: 'existing-1',
            source,
            target,
          };
          const result = canCreateTransition([existingTransition], source, target);
          expect(result.allowed).toBe(false);
          expect(result.reason).toBeDefined();
        }
      ),
      { numRuns: 100 }
    );
  });

  it('allows valid transitions (different source and target, no duplicate)', () => {
    fc.assert(
      fc.property(arbStateId, arbStateId, (source, target) => {
        fc.pre(source !== target);
        const result = canCreateTransition([], source, target);
        expect(result.allowed).toBe(true);
      }),
      { numRuns: 100 }
    );
  });
});

// =============================================================================
// Property 2: Zoom level clamping
// =============================================================================

describe('Property 2: Zoom level clamping', () => {
  /**
   * **Validates: Requirements 1.6**
   *
   * For any numeric zoom value, the result should be clamped to [0.25, 4.0].
   * Values below 0.25 produce 0.25, values above 4.0 produce 4.0,
   * and values within the range are unchanged.
   */

  it('always produces a value within [0.25, 4.0] for any finite float', () => {
    fc.assert(
      fc.property(fc.double({ min: -1e10, max: 1e10, noNaN: true }), (value) => {
        const result = clampZoom(value);
        expect(result).toBeGreaterThanOrEqual(MIN_ZOOM);
        expect(result).toBeLessThanOrEqual(MAX_ZOOM);
      }),
      { numRuns: 100 }
    );
  });

  it('values within [0.25, 4.0] are unchanged', () => {
    fc.assert(
      fc.property(
        fc.double({ min: MIN_ZOOM, max: MAX_ZOOM, noNaN: true }),
        (value) => {
          fc.pre(Number.isFinite(value) && value >= MIN_ZOOM && value <= MAX_ZOOM);
          const result = clampZoom(value);
          expect(result).toBe(value);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('values below 0.25 produce exactly 0.25', () => {
    fc.assert(
      fc.property(
        fc.double({ min: -1e10, max: MIN_ZOOM - 0.001, noNaN: true }),
        (value) => {
          fc.pre(value < MIN_ZOOM);
          const result = clampZoom(value);
          expect(result).toBe(MIN_ZOOM);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('values above 4.0 produce exactly 4.0', () => {
    fc.assert(
      fc.property(
        fc.double({ min: MAX_ZOOM + 0.001, max: 1e10, noNaN: true }),
        (value) => {
          fc.pre(value > MAX_ZOOM);
          const result = clampZoom(value);
          expect(result).toBe(MAX_ZOOM);
        }
      ),
      { numRuns: 100 }
    );
  });
});

// =============================================================================
// Property 3: State deletion removes exactly associated transitions
// =============================================================================

describe('Property 3: State deletion removes exactly associated transitions', () => {
  /**
   * **Validates: Requirements 1.7**
   *
   * Deleting a state removes exactly that state and all transitions where it
   * is source or target, leaving all other states and unrelated transitions intact.
   */

  it('removes the target state and only transitions involving it', () => {
    fc.assert(
      fc.property(
        // Generate 2-6 states
        fc.array(arbWorkflowState, { minLength: 2, maxLength: 6 }),
        (stateArray) => {
          // Ensure unique IDs
          const uniqueStates = stateArray.filter(
            (s, i, arr) => arr.findIndex(x => x.id === s.id) === i
          );
          fc.pre(uniqueStates.length >= 2);

          const stateIds = uniqueStates.map(s => s.id);
          const statesMap = new Map(uniqueStates.map(s => [s.id, s]));

          // Generate some transitions between states (no self-loops)
          const transitions: Transition[] = [];
          let tIdx = 0;
          for (let i = 0; i < stateIds.length; i++) {
            for (let j = 0; j < stateIds.length; j++) {
              if (i !== j && tIdx < 8) {
                transitions.push({
                  id: `t-${tIdx++}`,
                  source: stateIds[i],
                  target: stateIds[j],
                });
              }
            }
          }

          const transitionsMap = new Map(transitions.map(t => [t.id, t]));

          // Pick a random state to delete
          const stateToDelete = stateIds[0];

          const result = deleteStateWithTransitions(statesMap, transitionsMap, stateToDelete);

          // The deleted state should be gone
          expect(result.states.has(stateToDelete)).toBe(false);

          // All other states should remain
          for (const id of stateIds) {
            if (id !== stateToDelete) {
              expect(result.states.has(id)).toBe(true);
            }
          }

          // No transitions involving the deleted state should remain
          for (const [, t] of result.transitions) {
            expect(t.source).not.toBe(stateToDelete);
            expect(t.target).not.toBe(stateToDelete);
          }

          // All transitions NOT involving the deleted state should remain
          for (const [id, t] of transitionsMap) {
            if (t.source !== stateToDelete && t.target !== stateToDelete) {
              expect(result.transitions.has(id)).toBe(true);
            }
          }
        }
      ),
      { numRuns: 100 }
    );
  });
});

// =============================================================================
// Property 4: Undo reverses canvas operations
// =============================================================================

describe('Property 4: Undo reverses canvas operations', () => {
  /**
   * **Validates: Requirements 1.8**
   *
   * Performing any operation then undoing it restores the previous state.
   * The undo stack retains at least 50 operations.
   */

  /** Arbitrary for ADD_STATE operation */
  const arbAddStateOp: fc.Arbitrary<CanvasOperation> = arbWorkflowState.map(state => ({
    type: 'ADD_STATE' as const,
    state,
  }));

  /** Arbitrary for ADD_TRANSITION operation (uses fixed state IDs) */
  const arbAddTransitionOp: fc.Arbitrary<CanvasOperation> = fc.record({
    id: fc.uuid(),
    source: fc.constantFrom('state-a', 'state-b', 'state-c'),
    target: fc.constantFrom('state-a', 'state-b', 'state-c'),
  }).filter(t => t.source !== t.target).map(t => ({
    type: 'ADD_TRANSITION' as const,
    transition: t as Transition,
  }));

  /** Arbitrary for MOVE_STATE operation */
  const arbMoveStateOp: fc.Arbitrary<CanvasOperation> = fc.record({
    stateId: fc.constantFrom('state-a', 'state-b', 'state-c'),
    from: fc.record({ x: fc.integer({ min: 0, max: 1000 }), y: fc.integer({ min: 0, max: 1000 }) }),
    to: fc.record({ x: fc.integer({ min: 0, max: 1000 }), y: fc.integer({ min: 0, max: 1000 }) }),
  }).map(data => ({
    type: 'MOVE_STATE' as const,
    ...data,
  }));

  /** Create a canvas state with some pre-existing states for move/transition ops */
  function canvasWithStates(): CanvasState {
    const states = new Map<string, WorkflowState>();
    states.set('state-a', {
      id: 'state-a', type: 'API_Call', name: 'A',
      position: { x: 0, y: 0 },
      config: defaultConfigForType('API_Call'),
    });
    states.set('state-b', {
      id: 'state-b', type: 'Response', name: 'B',
      position: { x: 100, y: 100 },
      config: defaultConfigForType('Response'),
    });
    states.set('state-c', {
      id: 'state-c', type: 'End', name: 'C',
      position: { x: 200, y: 200 },
      config: defaultConfigForType('End'),
    });
    return {
      states,
      transitions: new Map(),
      selectedStateId: null,
      zoom: 1,
      panOffset: { x: 0, y: 0 },
      undoStack: [],
      redoStack: [],
      contextVariables: [],
    };
  }

  it('undo after ADD_STATE restores previous state', () => {
    fc.assert(
      fc.property(arbAddStateOp, (operation) => {
        const initial = emptyCanvasState();
        const afterApply = applyOperation(initial, operation);
        const withUndo = pushOperation(afterApply, operation);
        const afterUndo = undoOperation(withUndo);

        // States and transitions should match the initial
        expect(afterUndo.states.size).toBe(initial.states.size);
        expect(afterUndo.transitions.size).toBe(initial.transitions.size);
        expect(afterUndo.states.has((operation as { type: 'ADD_STATE'; state: WorkflowState }).state.id)).toBe(false);
      }),
      { numRuns: 100 }
    );
  });

  it('undo after ADD_TRANSITION restores previous state', () => {
    fc.assert(
      fc.property(arbAddTransitionOp, (operation) => {
        const initial = canvasWithStates();
        const afterApply = applyOperation(initial, operation);
        const withUndo = pushOperation(afterApply, operation);
        const afterUndo = undoOperation(withUndo);

        // Transitions should match the initial (empty)
        expect(afterUndo.transitions.size).toBe(initial.transitions.size);
      }),
      { numRuns: 100 }
    );
  });

  it('undo after MOVE_STATE restores original position', () => {
    fc.assert(
      fc.property(arbMoveStateOp, (operation) => {
        const initial = canvasWithStates();
        // Set the state to the "from" position first
        const moveOp = operation as { type: 'MOVE_STATE'; stateId: string; from: Position; to: Position };
        const stateId = moveOp.stateId;
        const fromPos = moveOp.from;
        const existingState = initial.states.get(stateId)!;
        initial.states.set(stateId, { ...existingState, position: { ...fromPos } });

        const afterApply = applyOperation(initial, operation);
        const withUndo = pushOperation(afterApply, operation);
        const afterUndo = undoOperation(withUndo);

        // Position should be restored to "from"
        const restoredState = afterUndo.states.get(stateId);
        expect(restoredState).toBeDefined();
        expect(restoredState!.position.x).toBe(fromPos.x);
        expect(restoredState!.position.y).toBe(fromPos.y);
      }),
      { numRuns: 100 }
    );
  });

  it('undo after DELETE_TRANSITION restores the transition', () => {
    fc.assert(
      fc.property(fc.uuid(), (transitionId) => {
        const initial = canvasWithStates();
        const transition: Transition = { id: transitionId, source: 'state-a', target: 'state-b' };
        initial.transitions.set(transitionId, transition);

        const operation: CanvasOperation = { type: 'DELETE_TRANSITION', transition };
        const afterApply = applyOperation(initial, operation);
        const withUndo = pushOperation(afterApply, operation);
        const afterUndo = undoOperation(withUndo);

        // Transition should be restored
        expect(afterUndo.transitions.has(transitionId)).toBe(true);
        expect(afterUndo.transitions.get(transitionId)!.source).toBe('state-a');
        expect(afterUndo.transitions.get(transitionId)!.target).toBe('state-b');
      }),
      { numRuns: 100 }
    );
  });

  it('undo stack retains at least 50 operations', () => {
    fc.assert(
      fc.property(
        fc.array(arbAddStateOp, { minLength: 50, maxLength: 55 }),
        (operations) => {
          let state = emptyCanvasState();
          for (const op of operations) {
            state = applyOperation(state, op);
            state = pushOperation(state, op);
          }
          // The undo stack should have exactly 50 (capped) or up to 50 items
          expect(state.undoStack.length).toBeLessThanOrEqual(50);
          expect(state.undoStack.length).toBe(Math.min(operations.length, 50));
        }
      ),
      { numRuns: 100 }
    );
  });
});
