/**
 * Factory for creating default WorkflowState instances per state type.
 * Used when dragging a state from the palette onto the canvas.
 *
 * Requirements: 1.2, 2.1
 */
import {
  StateType,
  WorkflowState,
  StateConfig,
  Position,
} from '../types/canvas.types';

/** Default names for each state type */
const DEFAULT_NAMES: Record<StateType, string> = {
  API_Call: 'API Call',
  Condition: 'Condition',
  Response: 'Response',
  Input: 'User Input',
  Wait: 'Wait',
  Parallel: 'Parallel',
  End: 'End',
};

/** Creates a default StateConfig for the given state type */
function createDefaultConfig(type: StateType): StateConfig {
  switch (type) {
    case 'API_Call':
      return {
        type: 'API_Call',
        method: 'GET',
        url: '',
        headers: {},
        body: '',
        responseMapping: {},
        timeout: 30,
      };
    case 'Condition':
      return {
        type: 'Condition',
        expression: '',
      };
    case 'Response':
      return {
        type: 'Response',
        messageTemplate: '',
      };
    case 'Input':
      return {
        type: 'Input',
        prompt: '',
        variableName: '',
        timeout: 300,
      };
    case 'Wait':
      return {
        type: 'Wait',
        duration: 60,
      };
    case 'Parallel':
      return {
        type: 'Parallel',
        branches: [],
      };
    case 'End':
      return {
        type: 'End',
      };
  }
}

/**
 * Creates a new WorkflowState with a unique ID and default configuration
 * for the given state type at the specified position.
 */
export function createDefaultState(
  type: StateType,
  position: Position
): WorkflowState {
  return {
    id: crypto.randomUUID(),
    type,
    name: DEFAULT_NAMES[type],
    position: { x: position.x, y: position.y },
    config: createDefaultConfig(type),
  };
}
