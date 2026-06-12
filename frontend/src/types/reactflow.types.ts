/**
 * Library-specific type aliases bridging internal domain models to @xyflow/react types.
 * These aliases provide type-safe node/edge definitions and converter functions
 * for transforming between the application's WorkflowState/Transition models
 * and React Flow's Node/Edge representations.
 *
 * Requirements: 1.5, 5.1
 */
import { type Node, type Edge } from '@xyflow/react';
import { StateNodeData } from '../components/canvas/nodes/StateNode';
import { WorkflowState, Transition } from './canvas.types';

/**
 * Extended StateNodeData that satisfies @xyflow/react's Record<string, unknown> constraint.
 * This allows the data to be used as the generic parameter for Node<T>.
 */
export type AppNodeData = StateNodeData & Record<string, unknown>;

/**
 * Application-specific node type using @xyflow/react v12 generics.
 * Binds AppNodeData as the data payload and 'stateNode' as the type discriminator,
 * ensuring type safety when accessing node data throughout the app.
 */
export type AppNode = Node<AppNodeData, 'stateNode'>;

/**
 * Application-specific edge type alias.
 * Currently maps directly to the base Edge type from @xyflow/react.
 */
export type AppEdge = Edge;

/**
 * Converts a domain WorkflowState into an AppNode for rendering on the React Flow canvas.
 * Maps the state's id, position, and type/name into the node data structure.
 */
export function workflowStateToNode(state: WorkflowState): AppNode {
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

/**
 * Converts a domain Transition into an AppEdge for rendering on the React Flow canvas.
 * Maps the transition's id, source, target, and optional condition into the edge structure.
 */
export function transitionToEdge(transition: Transition): AppEdge {
  return {
    id: transition.id,
    source: transition.source,
    target: transition.target,
    ...(transition.condition && { sourceHandle: transition.condition }),
  };
}
