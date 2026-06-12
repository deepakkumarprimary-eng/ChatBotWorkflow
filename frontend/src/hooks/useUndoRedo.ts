/**
 * Hook for managing undo/redo operations on the workflow canvas.
 * Handles all CanvasOperation types: ADD_STATE, DELETE_STATE, MOVE_STATE,
 * ADD_TRANSITION, DELETE_TRANSITION, UPDATE_STATE_CONFIG.
 *
 * Retains at least 50 operations in the undo history.
 * Wires to Ctrl+Z (undo) and Ctrl+Shift+Z (redo) keyboard shortcuts.
 *
 * Requirements: 1.8 - Undo/redo for all canvas operations
 */
import { useCallback, useEffect } from 'react';
import {
  CanvasState,
  CanvasOperation,
} from '../types/canvas.types';

/** Maximum number of operations retained in undo stack */
export const MAX_UNDO_STACK_SIZE = 50;

/**
 * Applies a canvas operation to the current state (forward direction).
 * Used for redo and initial application.
 */
export function applyOperation(
  state: CanvasState,
  operation: CanvasOperation
): CanvasState {
  const newStates = new Map(state.states);
  const newTransitions = new Map(state.transitions);

  switch (operation.type) {
    case 'ADD_STATE': {
      newStates.set(operation.state.id, operation.state);
      return { ...state, states: newStates, transitions: newTransitions };
    }
    case 'DELETE_STATE': {
      newStates.delete(operation.state.id);
      // Also remove associated transitions
      for (const t of operation.transitions) {
        newTransitions.delete(t.id);
      }
      return { ...state, states: newStates, transitions: newTransitions };
    }
    case 'MOVE_STATE': {
      const existing = newStates.get(operation.stateId);
      if (existing) {
        newStates.set(operation.stateId, {
          ...existing,
          position: { ...operation.to },
        });
      }
      return { ...state, states: newStates, transitions: newTransitions };
    }
    case 'ADD_TRANSITION': {
      newTransitions.set(operation.transition.id, operation.transition);
      return { ...state, states: newStates, transitions: newTransitions };
    }
    case 'DELETE_TRANSITION': {
      newTransitions.delete(operation.transition.id);
      return { ...state, states: newStates, transitions: newTransitions };
    }
    case 'UPDATE_STATE_CONFIG': {
      const s = newStates.get(operation.stateId);
      if (s) {
        newStates.set(operation.stateId, {
          ...s,
          config: operation.newConfig,
        });
      }
      return { ...state, states: newStates, transitions: newTransitions };
    }
    default:
      return state;
  }
}

/**
 * Reverses a canvas operation (undo direction).
 * Returns the state as it was before the operation was applied.
 */
export function reverseOperation(
  state: CanvasState,
  operation: CanvasOperation
): CanvasState {
  const newStates = new Map(state.states);
  const newTransitions = new Map(state.transitions);

  switch (operation.type) {
    case 'ADD_STATE': {
      // Undo add = remove
      newStates.delete(operation.state.id);
      return { ...state, states: newStates, transitions: newTransitions };
    }
    case 'DELETE_STATE': {
      // Undo delete = re-add the state and its transitions
      newStates.set(operation.state.id, operation.state);
      for (const t of operation.transitions) {
        newTransitions.set(t.id, t);
      }
      return { ...state, states: newStates, transitions: newTransitions };
    }
    case 'MOVE_STATE': {
      // Undo move = move back to original position
      const existing = newStates.get(operation.stateId);
      if (existing) {
        newStates.set(operation.stateId, {
          ...existing,
          position: { ...operation.from },
        });
      }
      return { ...state, states: newStates, transitions: newTransitions };
    }
    case 'ADD_TRANSITION': {
      // Undo add transition = remove it
      newTransitions.delete(operation.transition.id);
      return { ...state, states: newStates, transitions: newTransitions };
    }
    case 'DELETE_TRANSITION': {
      // Undo delete transition = re-add it
      newTransitions.set(operation.transition.id, operation.transition);
      return { ...state, states: newStates, transitions: newTransitions };
    }
    case 'UPDATE_STATE_CONFIG': {
      // Undo config update = restore old config
      const s = newStates.get(operation.stateId);
      if (s) {
        newStates.set(operation.stateId, {
          ...s,
          config: operation.oldConfig,
        });
      }
      return { ...state, states: newStates, transitions: newTransitions };
    }
    default:
      return state;
  }
}

/**
 * Pushes an operation onto the undo stack, capping at MAX_UNDO_STACK_SIZE.
 * Clears the redo stack (new operation invalidates redo history).
 */
export function pushOperation(
  state: CanvasState,
  operation: CanvasOperation
): CanvasState {
  const newUndoStack = [...state.undoStack, operation];
  if (newUndoStack.length > MAX_UNDO_STACK_SIZE) {
    newUndoStack.shift();
  }
  return {
    ...state,
    undoStack: newUndoStack,
    redoStack: [],
  };
}

/**
 * Performs an undo: pops from undoStack, reverses the operation,
 * and pushes to redoStack.
 */
export function performUndo(state: CanvasState): CanvasState {
  if (state.undoStack.length === 0) {
    return state;
  }

  const newUndoStack = [...state.undoStack];
  const operation = newUndoStack.pop()!;
  const reversedState = reverseOperation(state, operation);

  return {
    ...reversedState,
    undoStack: newUndoStack,
    redoStack: [...state.redoStack, operation],
  };
}

/**
 * Performs a redo: pops from redoStack, re-applies the operation,
 * and pushes to undoStack.
 */
export function performRedo(state: CanvasState): CanvasState {
  if (state.redoStack.length === 0) {
    return state;
  }

  const newRedoStack = [...state.redoStack];
  const operation = newRedoStack.pop()!;
  const appliedState = applyOperation(state, operation);

  return {
    ...appliedState,
    undoStack: [...state.undoStack, operation],
    redoStack: newRedoStack,
  };
}

export interface UseUndoRedoOptions {
  /** Current canvas state */
  canvasState: CanvasState;
  /** State updater callback */
  setCanvasState: (updater: (prev: CanvasState) => CanvasState) => void;
  /** Whether to attach keyboard listeners (default: true) */
  enableKeyboardShortcuts?: boolean;
}

export interface UseUndoRedoReturn {
  /** Perform undo action */
  undo: () => void;
  /** Perform redo action */
  redo: () => void;
  /** Whether undo is available */
  canUndo: boolean;
  /** Whether redo is available */
  canRedo: boolean;
  /** Push a new operation to the undo stack */
  pushOp: (operation: CanvasOperation) => void;
}

/**
 * React hook for undo/redo functionality.
 * Attaches Ctrl+Z / Ctrl+Shift+Z keyboard shortcuts.
 */
export function useUndoRedo({
  canvasState,
  setCanvasState,
  enableKeyboardShortcuts = true,
}: UseUndoRedoOptions): UseUndoRedoReturn {
  const undo = useCallback(() => {
    setCanvasState((prev) => performUndo(prev));
  }, [setCanvasState]);

  const redo = useCallback(() => {
    setCanvasState((prev) => performRedo(prev));
  }, [setCanvasState]);

  const pushOp = useCallback(
    (operation: CanvasOperation) => {
      setCanvasState((prev) => {
        const applied = applyOperation(prev, operation);
        return pushOperation(applied, operation);
      });
    },
    [setCanvasState]
  );

  const canUndo = canvasState.undoStack.length > 0;
  const canRedo = canvasState.redoStack.length > 0;

  // Keyboard shortcuts
  useEffect(() => {
    if (!enableKeyboardShortcuts) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      // Ctrl+Z for undo, Ctrl+Shift+Z for redo
      if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
        e.preventDefault();
        undo();
      } else if ((e.ctrlKey || e.metaKey) && e.key === 'Z' && e.shiftKey) {
        e.preventDefault();
        redo();
      } else if ((e.ctrlKey || e.metaKey) && e.key === 'y') {
        // Also support Ctrl+Y as redo (common alternative)
        e.preventDefault();
        redo();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [enableKeyboardShortcuts, undo, redo]);

  return { undo, redo, canUndo, canRedo, pushOp };
}
