/**
 * Client-side workflow validation.
 * Pure function that validates a workflow graph structure and state configurations.
 * Collects ALL errors before returning (no short-circuit).
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8
 */
import type {
  WorkflowState,
  Transition,
  ApiCallConfig,
  ConditionConfig,
  ResponseConfig,
  InputConfig,
  WaitConfig,
  ParallelConfig,
} from '../types/canvas.types';

export interface ValidationError {
  stateId: string | null; // null for workflow-level errors
  stateName?: string;
  message: string;
  errorType: string;
}

export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
}

/**
 * Validates a workflow graph.
 * All validation rules are applied simultaneously — every error is collected.
 */
export function validateWorkflow(
  states: Map<string, WorkflowState>,
  transitions: Map<string, Transition>
): ValidationResult {
  const errors: ValidationError[] = [];

  // Rule: Empty workflow
  if (states.size === 0) {
    errors.push({
      stateId: null,
      message: 'Workflow is empty',
      errorType: 'EMPTY_WORKFLOW',
    });
    return { valid: false, errors };
  }

  // Build helper structures
  const incomingCount = new Map<string, number>();
  const outgoingTransitions = new Map<string, Transition[]>();

  for (const [, state] of states) {
    incomingCount.set(state.id, 0);
    outgoingTransitions.set(state.id, []);
  }

  for (const [, transition] of transitions) {
    const count = incomingCount.get(transition.target);
    if (count !== undefined) {
      incomingCount.set(transition.target, count + 1);
    }
    const outgoing = outgoingTransitions.get(transition.source);
    if (outgoing) {
      outgoing.push(transition);
    }
  }

  // Rule: Single start state (state with no incoming transitions)
  const startStates: string[] = [];
  for (const [stateId, count] of incomingCount) {
    if (count === 0) {
      startStates.push(stateId);
    }
  }

  if (startStates.length === 0) {
    errors.push({
      stateId: null,
      message: 'No start state found',
      errorType: 'NO_START_STATE',
    });
  } else if (startStates.length > 1) {
    errors.push({
      stateId: null,
      message: 'Multiple start states found',
      errorType: 'MULTIPLE_START_STATES',
    });
  }

  // Rule: All non-End states must have outgoing transitions
  for (const [, state] of states) {
    if (state.type === 'End') continue;
    const outgoing = outgoingTransitions.get(state.id) ?? [];
    if (outgoing.length === 0) {
      errors.push({
        stateId: state.id,
        stateName: state.name,
        message: `State "${state.name}" has no outgoing transitions`,
        errorType: 'NO_OUTGOING_TRANSITION',
      });
    }
  }

  // Rule: Condition state must have exactly 2 outgoing transitions (true/false)
  for (const [, state] of states) {
    if (state.type !== 'Condition') continue;
    const outgoing = outgoingTransitions.get(state.id) ?? [];
    if (outgoing.length !== 2) {
      errors.push({
        stateId: state.id,
        stateName: state.name,
        message: `Condition state "${state.name}" must have exactly 2 outgoing transitions (true/false), found ${outgoing.length}`,
        errorType: 'CONDITION_TRANSITION_COUNT',
      });
    } else {
      const conditions = outgoing.map((t) => t.condition).sort();
      const hasTrue = conditions.includes('true');
      const hasFalse = conditions.includes('false');
      if (!hasTrue || !hasFalse) {
        errors.push({
          stateId: state.id,
          stateName: state.name,
          message: `Condition state "${state.name}" must have one "true" and one "false" transition`,
          errorType: 'CONDITION_TRANSITION_LABELS',
        });
      }
    }
  }

  // Rule: Reachability from start state (BFS)
  // When there are multiple start states, pick the first one for reachability analysis.
  // Other start states will be reported as unreachable if not connected from the primary start.
  if (startStates.length >= 1) {
    const startId = startStates[0];
    const visited = new Set<string>();
    const queue: string[] = [startId];
    visited.add(startId);

    while (queue.length > 0) {
      const current = queue.shift()!;
      const outgoing = outgoingTransitions.get(current) ?? [];
      for (const t of outgoing) {
        if (!visited.has(t.target) && states.has(t.target)) {
          visited.add(t.target);
          queue.push(t.target);
        }
      }
    }

    for (const [, state] of states) {
      if (!visited.has(state.id)) {
        errors.push({
          stateId: state.id,
          stateName: state.name,
          message: `State "${state.name}" is unreachable from the start state`,
          errorType: 'UNREACHABLE_STATE',
        });
      }
    }
  }

  // Rule: Required config fields per state type
  for (const [, state] of states) {
    const configErrors = validateStateConfig(state);
    errors.push(...configErrors);
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Validates required configuration fields for a given state.
 */
function validateStateConfig(state: WorkflowState): ValidationError[] {
  const errors: ValidationError[] = [];
  const config = state.config;

  switch (state.type) {
    case 'API_Call': {
      const c = config as ApiCallConfig;
      if (!c.url || c.url.trim() === '') {
        errors.push({
          stateId: state.id,
          stateName: state.name,
          message: `API Call state "${state.name}" requires a URL`,
          errorType: 'MISSING_REQUIRED_FIELD',
        });
      }
      break;
    }
    case 'Condition': {
      const c = config as ConditionConfig;
      if (!c.expression || c.expression.trim() === '') {
        errors.push({
          stateId: state.id,
          stateName: state.name,
          message: `Condition state "${state.name}" requires an expression`,
          errorType: 'MISSING_REQUIRED_FIELD',
        });
      }
      break;
    }
    case 'Response': {
      const c = config as ResponseConfig;
      if (!c.messageTemplate || c.messageTemplate.trim() === '') {
        errors.push({
          stateId: state.id,
          stateName: state.name,
          message: `Response state "${state.name}" requires a message template`,
          errorType: 'MISSING_REQUIRED_FIELD',
        });
      }
      break;
    }
    case 'Input': {
      const c = config as InputConfig;
      if (!c.prompt || c.prompt.trim() === '') {
        errors.push({
          stateId: state.id,
          stateName: state.name,
          message: `Input state "${state.name}" requires a prompt`,
          errorType: 'MISSING_REQUIRED_FIELD',
        });
      }
      if (!c.variableName || c.variableName.trim() === '') {
        errors.push({
          stateId: state.id,
          stateName: state.name,
          message: `Input state "${state.name}" requires a variable name`,
          errorType: 'MISSING_REQUIRED_FIELD',
        });
      }
      break;
    }
    case 'Wait': {
      const c = config as WaitConfig;
      if (c.duration < 1 || c.duration > 86400) {
        errors.push({
          stateId: state.id,
          stateName: state.name,
          message: `Wait state "${state.name}" duration must be between 1 and 86400 seconds`,
          errorType: 'INVALID_FIELD_VALUE',
        });
      }
      break;
    }
    case 'Parallel': {
      const c = config as ParallelConfig;
      if (!c.branches || c.branches.length < 2 || c.branches.length > 10) {
        errors.push({
          stateId: state.id,
          stateName: state.name,
          message: `Parallel state "${state.name}" must have between 2 and 10 branches`,
          errorType: 'INVALID_FIELD_VALUE',
        });
      }
      break;
    }
    case 'End':
      // No required fields
      break;
  }

  return errors;
}
