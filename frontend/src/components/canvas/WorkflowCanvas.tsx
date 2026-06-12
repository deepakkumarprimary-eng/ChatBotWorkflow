/**
 * Main workflow canvas component built on React Flow.
 * Renders workflow states as typed nodes, supports zoom/pan,
 * handles node positioning and movement, and maintains canvas state.
 *
 * Requirements: 1.1 - Visual Canvas for placing, moving, and connecting states
 * Requirements: 1.6 - Zoom (25%-400%) and pan controls
 * Requirements: 1.8 - Undo/redo for all canvas operations
 */
import { useCallback, useMemo, useState, useEffect, DragEvent, useRef } from 'react';
import {
  ReactFlow,
  type Node,
  type Edge,
  type NodeChange,
  type EdgeChange,
  type Connection,
  Background,
  BackgroundVariant,
  MiniMap,
  ReactFlowProvider,
  type Viewport,
  type NodeTypes,
  useReactFlow,
  ConnectionMode,
} from '@xyflow/react';
import '@xyflow/react/dist/base.css';

import StateNode, { StateNodeData } from './nodes/StateNode';
import { NotionEdge } from './edges/NotionEdge';
import ZoomControls, { MIN_ZOOM, MAX_ZOOM, clampZoom } from './ZoomControls';
import ConnectionToast, { ToastMessage } from './ConnectionToast';
import {
  CanvasState,
  WorkflowState,
  Transition,
  TransitionCondition,
  CanvasOperation,
  Position,
  ContextVariable,
  StateConfig,
} from '../../types/canvas.types';
import { canCreateTransition } from '../../utils/canvasOperations';
import { useUndoRedo } from '../../hooks/useUndoRedo';
import { DRAG_DATA_TYPE } from '../palette';
import { createDefaultState } from '../../utils/stateFactory';
import ConfirmDeleteDialog from './ConfirmDeleteDialog';
import styles from './WorkflowCanvas.module.css';

/** App-level node type combining StateNodeData with the custom node type identifier */
type AppNode = Node<StateNodeData & Record<string, unknown>, 'stateNode'>;

/** Custom node types registered with React Flow */
const nodeTypes: NodeTypes = {
  stateNode: StateNode,
};

/** Custom edge types registered with React Flow */
const edgeTypes = { notion: NotionEdge };

/** Convert internal WorkflowState to React Flow Node */
function workflowStateToNode(state: WorkflowState): AppNode {
  return {
    id: state.id,
    type: 'stateNode',
    position: { x: state.position.x, y: state.position.y },
    data: {
      label: state.name,
      stateType: state.type,
    },
  };
}

/** Convert internal Transition to React Flow Edge */
function transitionToEdge(transition: Transition): Edge {
  return {
    id: transition.id,
    source: transition.source,
    target: transition.target,
    sourceHandle: transition.condition === 'true' || transition.condition === 'false'
      ? transition.condition
      : undefined,
    label: transition.condition || undefined,
    type: 'notion',
    animated: false,
  };
}

export interface WorkflowCanvasProps {
  /** Initial states to render on the canvas */
  initialStates?: WorkflowState[];
  /** Initial transitions to render */
  initialTransitions?: Transition[];
  /** Initial context variables */
  initialContextVariables?: ContextVariable[];
  /** Callback when canvas state changes */
  onCanvasStateChange?: (state: CanvasState) => void;
  /** Callback when a node is selected */
  onNodeSelect?: (stateId: string | null) => void;
  /** External trigger to request deletion of a state (shows confirmation first) */
  deleteStateRequest?: string | null;
  /** Callback when delete request has been handled (confirmed or cancelled) */
  onDeleteRequestHandled?: () => void;
}

function WorkflowCanvasInner({
  initialStates = [],
  initialTransitions = [],
  initialContextVariables = [],
  onCanvasStateChange,
  onNodeSelect,
  deleteStateRequest,
  onDeleteRequestHandled,
}: WorkflowCanvasProps) {
  // Internal canvas state
  const [canvasState, setCanvasState] = useState<CanvasState>(() => {
    const statesMap = new Map<string, WorkflowState>();
    initialStates.forEach((s) => statesMap.set(s.id, s));

    const transitionsMap = new Map<string, Transition>();
    initialTransitions.forEach((t) => transitionsMap.set(t.id, t));

    return {
      states: statesMap,
      transitions: transitionsMap,
      selectedStateId: null,
      zoom: 1,
      panOffset: { x: 0, y: 0 },
      undoStack: [],
      redoStack: [],
      contextVariables: initialContextVariables,
    };
  });

  // Toast notification state for connection validation errors
  const [toastMessages, setToastMessages] = useState<ToastMessage[]>([]);

  // Confirmation dialog state for state deletion
  const [deleteConfirm, setDeleteConfirm] = useState<{
    stateId: string;
    stateName: string;
    transitionCount: number;
  } | null>(null);

  const dismissToast = useCallback((id: string) => {
    setToastMessages((prev) => prev.filter((msg) => msg.id !== id));
  }, []);

  const showToast = useCallback((message: string) => {
    const id = crypto.randomUUID();
    setToastMessages((prev) => [...prev, { id, message }]);
  }, []);

  // Convert canvas state to React Flow nodes/edges
  const nodes = useMemo(
    () => Array.from(canvasState.states.values()).map(workflowStateToNode),
    [canvasState.states]
  );

  const edges = useMemo(
    () => Array.from(canvasState.transitions.values()).map(transitionToEdge),
    [canvasState.transitions]
  );

  // Notify parent when canvas state changes
  const canvasStateChangedRef = useRef(false);
  useEffect(() => {
    if (canvasStateChangedRef.current) {
      onCanvasStateChange?.(canvasState);
    }
    canvasStateChangedRef.current = true;
  }, [canvasState, onCanvasStateChange]);

  // Update canvas state helper
  const updateCanvasState = useCallback(
    (updater: (prev: CanvasState) => CanvasState) => {
      setCanvasState(updater);
    },
    []
  );

  // Undo/redo hook with keyboard shortcuts
  const { undo: _undo, redo: _redo, canUndo: _canUndo, canRedo: _canRedo, pushOp } = useUndoRedo({
    canvasState,
    setCanvasState: updateCanvasState,
    enableKeyboardShortcuts: true,
  });

  // Handle node position changes (dragging)
  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      // Handle selection changes outside the state updater (side effect)
      for (const change of changes) {
        if (change.type === 'select') {
          const selectedId = change.selected ? change.id : null;
          onNodeSelect?.(selectedId);
        }
      }

      updateCanvasState((prev) => {
        const newStates = new Map(prev.states);

        for (const change of changes) {
          if (change.type === 'position' && change.position && change.dragging === false) {
            // Node drag ended - record for undo
            const state = newStates.get(change.id);
            if (state) {
              const oldPosition = { ...state.position };
              const newPosition: Position = {
                x: change.position.x,
                y: change.position.y,
              };
              newStates.set(change.id, { ...state, position: newPosition });

              // Push undo operation for completed drag
              const operation: CanvasOperation = {
                type: 'MOVE_STATE',
                stateId: change.id,
                from: oldPosition,
                to: newPosition,
              };
              const newUndoStack = [...prev.undoStack, operation];
              if (newUndoStack.length > 50) {
                newUndoStack.shift();
              }
              return {
                ...prev,
                states: newStates,
                undoStack: newUndoStack,
                redoStack: [],
              };
            }
          } else if (change.type === 'position' && change.position) {
            // Intermediate drag - update position without undo entry
            const state = newStates.get(change.id);
            if (state) {
              newStates.set(change.id, {
                ...state,
                position: { x: change.position.x, y: change.position.y },
              });
            }
          } else if (change.type === 'select') {
            // Update internal selection state
            const selectedId = change.selected ? change.id : null;
            return {
              ...prev,
              states: newStates,
              selectedStateId: selectedId,
            };
          }
        }

        return { ...prev, states: newStates };
      });
    },
    [updateCanvasState, onNodeSelect]
  );

  // Add a state to the canvas (tracked by undo/redo)
  const addState = useCallback(
    (state: WorkflowState) => {
      pushOp({ type: 'ADD_STATE', state });
    },
    [pushOp]
  );

  // Delete a state and its associated transitions (tracked by undo/redo)
  const deleteState = useCallback(
    (stateId: string) => {
      updateCanvasState((prev) => {
        const state = prev.states.get(stateId);
        if (!state) return prev;

        // Find all transitions associated with this state
        const associatedTransitions: Transition[] = [];
        for (const t of prev.transitions.values()) {
          if (t.source === stateId || t.target === stateId) {
            associatedTransitions.push(t);
          }
        }

        const operation: CanvasOperation = {
          type: 'DELETE_STATE',
          state,
          transitions: associatedTransitions,
        };

        // Apply the delete
        const newStates = new Map(prev.states);
        newStates.delete(stateId);
        const newTransitions = new Map(prev.transitions);
        for (const t of associatedTransitions) {
          newTransitions.delete(t.id);
        }

        // Push to undo stack
        const newUndoStack = [...prev.undoStack, operation];
        if (newUndoStack.length > 50) {
          newUndoStack.shift();
        }

        return {
          ...prev,
          states: newStates,
          transitions: newTransitions,
          undoStack: newUndoStack,
          redoStack: [],
        };
      });
    },
    [updateCanvasState]
  );

  // Show confirmation dialog before deleting a state
  const requestDeleteState = useCallback(
    (stateId: string) => {
      const state = canvasState.states.get(stateId);
      if (!state) return;

      // Count transitions associated with this state
      let transitionCount = 0;
      for (const t of canvasState.transitions.values()) {
        if (t.source === stateId || t.target === stateId) {
          transitionCount++;
        }
      }

      setDeleteConfirm({ stateId, stateName: state.name, transitionCount });
    },
    [canvasState.states, canvasState.transitions]
  );

  // Handle confirm from delete dialog
  const handleDeleteConfirm = useCallback(() => {
    if (deleteConfirm) {
      deleteState(deleteConfirm.stateId);
    }
    setDeleteConfirm(null);
    onDeleteRequestHandled?.();
  }, [deleteConfirm, deleteState, onDeleteRequestHandled]);

  // Handle cancel from delete dialog
  const handleDeleteCancel = useCallback(() => {
    setDeleteConfirm(null);
    onDeleteRequestHandled?.();
  }, [onDeleteRequestHandled]);

  // Listen for external delete requests (e.g., from PropertyPanel)
  useEffect(() => {
    if (deleteStateRequest) {
      requestDeleteState(deleteStateRequest);
    }
  }, [deleteStateRequest, requestDeleteState]);

  // Keyboard listener for Delete/Backspace to delete selected state
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Only trigger if Delete or Backspace and not inside an input/textarea
      if (e.key !== 'Delete' && e.key !== 'Backspace') return;
      const target = e.target as HTMLElement;
      if (
        target.tagName === 'INPUT' ||
        target.tagName === 'TEXTAREA' ||
        target.tagName === 'SELECT' ||
        target.isContentEditable
      ) {
        return;
      }
      // Don't trigger if the confirmation dialog is already open
      if (deleteConfirm) return;

      if (canvasState.selectedStateId) {
        e.preventDefault();
        requestDeleteState(canvasState.selectedStateId);
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [canvasState.selectedStateId, deleteConfirm, requestDeleteState]);

  // Add a transition (tracked by undo/redo)
  const addTransition = useCallback(
    (transition: Transition) => {
      pushOp({ type: 'ADD_TRANSITION', transition });
    },
    [pushOp]
  );

  // Handle new connection from React Flow (edge drawing between nodes)
  const onConnect = useCallback(
    (connection: Connection) => {
      const { source, target, sourceHandle } = connection;
      if (!source || !target) return;

      // Validate the connection using canCreateTransition
      const existingTransitions = Array.from(canvasState.transitions.values());
      const validation = canCreateTransition(existingTransitions, source, target);

      if (!validation.allowed) {
        showToast(validation.reason || 'Connection not allowed');
        return;
      }

      // Determine condition label based on source handle
      let condition: TransitionCondition | undefined;
      if (sourceHandle === 'true' || sourceHandle === 'false') {
        condition = sourceHandle;
      }

      // Create the transition
      const transition: Transition = {
        id: crypto.randomUUID(),
        source,
        target,
        condition,
      };

      addTransition(transition);
    },
    [canvasState.transitions, addTransition, showToast]
  );

  // Delete a transition (tracked by undo/redo)
  const deleteTransition = useCallback(
    (transitionId: string) => {
      updateCanvasState((prev) => {
        const transition = prev.transitions.get(transitionId);
        if (!transition) return prev;

        const operation: CanvasOperation = {
          type: 'DELETE_TRANSITION',
          transition,
        };

        const newTransitions = new Map(prev.transitions);
        newTransitions.delete(transitionId);

        const newUndoStack = [...prev.undoStack, operation];
        if (newUndoStack.length > 50) {
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

  // Update state config (tracked by undo/redo)
  const updateStateConfig = useCallback(
    (stateId: string, newConfig: StateConfig) => {
      updateCanvasState((prev) => {
        const state = prev.states.get(stateId);
        if (!state) return prev;

        const operation: CanvasOperation = {
          type: 'UPDATE_STATE_CONFIG',
          stateId,
          oldConfig: state.config,
          newConfig,
        };

        const newStates = new Map(prev.states);
        newStates.set(stateId, { ...state, config: newConfig });

        const newUndoStack = [...prev.undoStack, operation];
        if (newUndoStack.length > 50) {
          newUndoStack.shift();
        }

        return {
          ...prev,
          states: newStates,
          undoStack: newUndoStack,
          redoStack: [],
        };
      });
    },
    [updateCanvasState]
  );

  // These callbacks are infrastructure for undo/redo operations used by child components
  void deleteTransition;
  void updateStateConfig;

  // Handle edge changes
  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      // Only update state if there are actual removals
      const hasRemovals = changes.some((c) => c.type === 'remove');
      if (!hasRemovals) return;

      updateCanvasState((prev) => {
        const newTransitions = new Map(prev.transitions);
        for (const change of changes) {
          if (change.type === 'remove') {
            newTransitions.delete(change.id);
          }
        }
        return { ...prev, transitions: newTransitions };
      });
    },
    [updateCanvasState]
  );

  // Handle viewport (zoom/pan) changes
  const onViewportChange = useCallback(
    (viewport: Viewport) => {
      updateCanvasState((prev) => ({
        ...prev,
        zoom: clampZoom(viewport.zoom),
        panOffset: { x: viewport.x, y: viewport.y },
      }));
    },
    [updateCanvasState]
  );

  // Handle pane click (deselect)
  const onPaneClick = useCallback(() => {
    updateCanvasState((prev) => ({
      ...prev,
      selectedStateId: null,
    }));
    onNodeSelect?.(null);
  }, [updateCanvasState, onNodeSelect]);

  // Drop handler: create new state from palette drag
  const reactFlowInstance = useReactFlow();
  const wrapperRef = useRef<HTMLDivElement>(null);

  const onDragOver = useCallback((event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (event: DragEvent<HTMLDivElement>) => {
      event.preventDefault();

      const stateType = event.dataTransfer.getData(DRAG_DATA_TYPE);
      if (!stateType) return;

      const bounds = wrapperRef.current?.getBoundingClientRect();
      if (!bounds) return;

      // Convert screen position to flow position using React Flow's viewport
      const position = reactFlowInstance.screenToFlowPosition({
        x: event.clientX,
        y: event.clientY,
      });

      const newState = createDefaultState(
        stateType as WorkflowState['type'],
        position
      );

      addState(newState);
    },
    [reactFlowInstance, addState]
  );

  // Memoized nodeColor callback for MiniMap
  const miniMapNodeColor = useCallback((node: AppNode) => {
    const stateType = (node.data as StateNodeData & Record<string, unknown>)?.stateType;
    const colors: Record<string, string> = {
      API_Call: '#1565c0',
      Condition: '#e65100',
      Response: '#2e7d32',
      Input: '#6a1b9a',
      Wait: '#f9a825',
      Parallel: '#00695c',
      End: '#b71c1c',
    };
    return colors[stateType] || '#999';
  }, []);

  // Memoized onMoveEnd handler
  const handleMoveEnd = useCallback(
    (_event: unknown, viewport: Viewport) => onViewportChange(viewport),
    [onViewportChange]
  );

  return (
    <div
      ref={wrapperRef}
      className={styles.canvasWrapper}
      data-testid="workflow-canvas"
      onDragOver={onDragOver}
      onDrop={onDrop}
    >
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onMoveEnd={handleMoveEnd}
        onPaneClick={onPaneClick}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        minZoom={MIN_ZOOM}
        maxZoom={MAX_ZOOM}
        defaultViewport={{ x: 0, y: 0, zoom: 1 }}
        fitView={false}
        snapToGrid
        snapGrid={[16, 16]}
        connectionMode={ConnectionMode.Loose}
        connectionRadius={40}
        attributionPosition="bottom-right"
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="var(--color-canvas-dot)" />
        <MiniMap
          nodeColor={miniMapNodeColor}
          className={styles.minimap}
        />
        <ZoomControls zoom={canvasState.zoom} />
      </ReactFlow>
      <ConnectionToast messages={toastMessages} onDismiss={dismissToast} />
      <ConfirmDeleteDialog
        visible={deleteConfirm !== null}
        stateName={deleteConfirm?.stateName ?? ''}
        transitionCount={deleteConfirm?.transitionCount ?? 0}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
      />
    </div>
  );
}

/**
 * WorkflowCanvas wraps the inner component with ReactFlowProvider
 * to make React Flow hooks available.
 */
export default function WorkflowCanvas(props: WorkflowCanvasProps) {
  return (
    <ReactFlowProvider>
      <WorkflowCanvasInner {...props} />
    </ReactFlowProvider>
  );
}

// Re-export utilities for use in other components and tests
export { workflowStateToNode, transitionToEdge, clampZoom };
