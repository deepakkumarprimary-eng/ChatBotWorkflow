/**
 * Canvas operation utility functions for the Chatbot Workflow Builder.
 * Provides pure functions for zoom clamping, transition validation,
 * state deletion, and undo/redo operations.
 *
 * Requirements: 1.4, 1.6, 1.7, 1.8
 */
import type {
  CanvasState,
  CanvasOperation,
  Transition,
  WorkflowState,
} from '../types/canvas.types';
import {
  applyOperation as applyOp,
  reverseOperation,
  pushOperation,
  performUndo as undoOp,
} from '../hooks/useUndoRedo';

// Re-export zoom constants
export const MIN_ZOOM = 0.25;
export const MAX_ZOOM = 4.0;

/**
 * Clamps a zoom value to the valid range [0.25, 4.0].
 * Values below 0.25 produce 0.25, values above 4.0 produce 4.0,
 * and values within the range are unchanged.
 */
export function clampZoom(value: number): number {
  if (!Number.isFinite(value)) return MIN_ZOOM;
  if (value < MIN_ZOOM) return MIN_ZOOM;
  if (value > MAX_ZOOM) return MAX_ZOOM;
  return value;
}

/**
 * Checks whether a transition can be created between source and target.
 * Rejects self-loops (source === target) and duplicate transitions.
 */
export function canCreateTransition(
  transitions: Transition[],
  source: string,
  target: string
): { allowed: boolean; reason?: string } {
  // Reject self-loops
  if (source === target) {
    return { allowed: false, reason: 'Self-loop transitions are not allowed' };
  }

  // Reject duplicates
  const duplicate = transitions.some(
    (t) => t.source === source && t.target === target
  );
  if (duplicate) {
    return { allowed: false, reason: 'A transition between these states already exists' };
  }

  return { allowed: true };
}

/**
 * Deletes a state and removes all transitions where it is source or target.
 * Returns new Maps with the state and associated transitions removed.
 * Other states and unrelated transitions remain intact.
 */
export function deleteStateWithTransitions(
  states: Map<string, WorkflowState>,
  transitions: Map<string, Transition>,
  stateId: string
): { states: Map<string, WorkflowState>; transitions: Map<string, Transition> } {
  const newStates = new Map(states);
  const newTransitions = new Map(transitions);

  // Remove the state
  newStates.delete(stateId);

  // Remove all transitions involving this state
  for (const [id, t] of newTransitions) {
    if (t.source === stateId || t.target === stateId) {
      newTransitions.delete(id);
    }
  }

  return { states: newStates, transitions: newTransitions };
}

/**
 * Applies a canvas operation to the current state (forward direction).
 * Re-exports from useUndoRedo for convenience.
 */
export function applyOperation(
  state: CanvasState,
  operation: CanvasOperation
): CanvasState {
  return applyOp(state, operation);
}

/**
 * Undoes the last operation on the canvas state.
 * Pops from undoStack, reverses the operation, pushes to redoStack.
 * Re-exports from useUndoRedo for convenience.
 */
export function undoOperation(state: CanvasState): CanvasState {
  return undoOp(state);
}

// Re-export pushOperation for pushing ops onto the undo stack
export { pushOperation, reverseOperation };
