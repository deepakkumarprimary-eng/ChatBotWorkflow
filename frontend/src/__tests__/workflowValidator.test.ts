/**
 * Unit tests for the workflow validator.
 * Tests all validation rules: empty workflow, start state, outgoing transitions,
 * condition structure, reachability, and required config fields.
 *
 * Feature: chatbot-workflow-builder
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8
 */
import { describe, it, expect } from 'vitest';
import { validateWorkflow } from '../utils/workflowValidator';
import type {
  WorkflowState,
  Transition,
  ApiCallConfig,
  ConditionConfig,
  ResponseConfig,
  InputConfig,
  WaitConfig,
  ParallelConfig,
  EndConfig,
} from '../types/canvas.types';

// ---- Helpers ----

function makeState(
  id: string,
  type: WorkflowState['type'],
  name: string,
  config: WorkflowState['config']
): WorkflowState {
  return { id, type, name, position: { x: 0, y: 0 }, config };
}

function makeTransition(
  id: string,
  source: string,
  target: string,
  condition?: Transition['condition']
): Transition {
  return { id, source, target, condition };
}

function statesMap(...states: WorkflowState[]): Map<string, WorkflowState> {
  return new Map(states.map((s) => [s.id, s]));
}

function transitionsMap(...transitions: Transition[]): Map<string, Transition> {
  return new Map(transitions.map((t) => [t.id, t]));
}

const validApiConfig: ApiCallConfig = { type: 'API_Call', method: 'GET', url: 'https://example.com', headers: {}, body: '', responseMapping: {}, timeout: 30 };
const validConditionConfig: ConditionConfig = { type: 'Condition', expression: 'x > 5' };
const validResponseConfig: ResponseConfig = { type: 'Response', messageTemplate: 'Hello {{name}}' };
const endConfig: EndConfig = { type: 'End' };

// =============================================================================
// Empty Workflow (Requirement 4.8)
// =============================================================================

describe('Empty workflow validation', () => {
  it('reports error for empty workflow', () => {
    const result = validateWorkflow(new Map(), new Map());
    expect(result.valid).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0].errorType).toBe('EMPTY_WORKFLOW');
    expect(result.errors[0].message).toBe('Workflow is empty');
    expect(result.errors[0].stateId).toBeNull();
  });
});

// =============================================================================
// Single Start State (Requirement 4.1)
// =============================================================================

describe('Start state validation', () => {
  it('valid: exactly one state with no incoming transitions', () => {
    const states = statesMap(
      makeState('s1', 'API_Call', 'Start', validApiConfig),
      makeState('s2', 'End', 'End', endConfig)
    );
    const transitions = transitionsMap(makeTransition('t1', 's1', 's2'));
    const result = validateWorkflow(states, transitions);
    expect(result.valid).toBe(true);
  });

  it('reports error when no start state found (all states have incoming)', () => {
    const states = statesMap(
      makeState('s1', 'API_Call', 'A', validApiConfig),
      makeState('s2', 'End', 'B', endConfig)
    );
    // Circular: s1 -> s2, s2 -> s1 (both have incoming)
    const transitions = transitionsMap(
      makeTransition('t1', 's1', 's2'),
      makeTransition('t2', 's2', 's1')
    );
    const result = validateWorkflow(states, transitions);
    expect(result.valid).toBe(false);
    expect(result.errors.some((e) => e.errorType === 'NO_START_STATE')).toBe(true);
  });

  it('reports error when multiple start states found', () => {
    const states = statesMap(
      makeState('s1', 'API_Call', 'A', validApiConfig),
      makeState('s2', 'Response', 'B', validResponseConfig),
      makeState('s3', 'End', 'End', endConfig)
    );
    // s1 and s2 both have no incoming transitions
    const transitions = transitionsMap(
      makeTransition('t1', 's1', 's3'),
      makeTransition('t2', 's2', 's3')
    );
    const result = validateWorkflow(states, transitions);
    expect(result.valid).toBe(false);
    expect(result.errors.some((e) => e.errorType === 'MULTIPLE_START_STATES')).toBe(true);
  });
});

// =============================================================================
// Outgoing Transitions (Requirement 4.2)
// =============================================================================

describe('Outgoing transitions validation', () => {
  it('reports error when non-End state has no outgoing transitions', () => {
    const states = statesMap(
      makeState('s1', 'API_Call', 'Start', validApiConfig),
      makeState('s2', 'Response', 'NoExit', validResponseConfig)
    );
    const transitions = transitionsMap(makeTransition('t1', 's1', 's2'));
    const result = validateWorkflow(states, transitions);
    expect(result.valid).toBe(false);
    expect(result.errors.some((e) => e.errorType === 'NO_OUTGOING_TRANSITION' && e.stateId === 's2')).toBe(true);
  });

  it('End state without outgoing transitions is valid', () => {
    const states = statesMap(
      makeState('s1', 'API_Call', 'Start', validApiConfig),
      makeState('s2', 'End', 'End', endConfig)
    );
    const transitions = transitionsMap(makeTransition('t1', 's1', 's2'));
    const result = validateWorkflow(states, transitions);
    expect(result.valid).toBe(true);
  });
});

// =============================================================================
// Condition State Structure (Requirement 4.3)
// =============================================================================

describe('Condition state transition validation', () => {
  it('valid condition state with true and false transitions', () => {
    const states = statesMap(
      makeState('s1', 'Condition', 'Check', validConditionConfig),
      makeState('s2', 'End', 'TrueEnd', endConfig),
      makeState('s3', 'End', 'FalseEnd', endConfig)
    );
    const transitions = transitionsMap(
      makeTransition('t1', 's1', 's2', 'true'),
      makeTransition('t2', 's1', 's3', 'false')
    );
    const result = validateWorkflow(states, transitions);
    // Only check condition-related errors; there may be multiple start state errors
    const condErrors = result.errors.filter((e) => e.errorType.startsWith('CONDITION'));
    expect(condErrors).toHaveLength(0);
  });

  it('reports error when condition state has wrong number of transitions', () => {
    const states = statesMap(
      makeState('s1', 'Condition', 'Check', validConditionConfig),
      makeState('s2', 'End', 'End', endConfig)
    );
    const transitions = transitionsMap(makeTransition('t1', 's1', 's2', 'true'));
    const result = validateWorkflow(states, transitions);
    expect(result.errors.some((e) => e.errorType === 'CONDITION_TRANSITION_COUNT')).toBe(true);
  });

  it('reports error when condition state transitions lack true/false labels', () => {
    const states = statesMap(
      makeState('s1', 'Condition', 'Check', validConditionConfig),
      makeState('s2', 'End', 'End1', endConfig),
      makeState('s3', 'End', 'End2', endConfig)
    );
    // Two transitions but no proper labels
    const transitions = transitionsMap(
      makeTransition('t1', 's1', 's2', 'error'),
      makeTransition('t2', 's1', 's3', 'timeout')
    );
    const result = validateWorkflow(states, transitions);
    expect(result.errors.some((e) => e.errorType === 'CONDITION_TRANSITION_LABELS')).toBe(true);
  });
});

// =============================================================================
// Reachability (Requirement 4.4)
// =============================================================================

describe('Reachability validation', () => {
  it('reports unreachable states', () => {
    const states = statesMap(
      makeState('s1', 'API_Call', 'Start', validApiConfig),
      makeState('s2', 'End', 'End', endConfig),
      makeState('s3', 'Response', 'Orphan', validResponseConfig)
    );
    // s3 is not connected from s1
    const transitions = transitionsMap(
      makeTransition('t1', 's1', 's2'),
      makeTransition('t2', 's3', 's2') // s3 has outgoing but is unreachable
    );
    const result = validateWorkflow(states, transitions);
    expect(result.errors.some((e) => e.errorType === 'UNREACHABLE_STATE' && e.stateId === 's3')).toBe(true);
  });

  it('all states reachable in a chain', () => {
    const states = statesMap(
      makeState('s1', 'API_Call', 'Start', validApiConfig),
      makeState('s2', 'Response', 'Middle', validResponseConfig),
      makeState('s3', 'End', 'End', endConfig)
    );
    const transitions = transitionsMap(
      makeTransition('t1', 's1', 's2'),
      makeTransition('t2', 's2', 's3')
    );
    const result = validateWorkflow(states, transitions);
    expect(result.valid).toBe(true);
  });
});

// =============================================================================
// Required Config Fields (Requirement 4.5)
// =============================================================================

describe('Required config fields validation', () => {
  it('API_Call requires url', () => {
    const badConfig: ApiCallConfig = { ...validApiConfig, url: '' };
    const states = statesMap(
      makeState('s1', 'API_Call', 'API', badConfig),
      makeState('s2', 'End', 'End', endConfig)
    );
    const transitions = transitionsMap(makeTransition('t1', 's1', 's2'));
    const result = validateWorkflow(states, transitions);
    expect(result.errors.some((e) => e.errorType === 'MISSING_REQUIRED_FIELD' && e.stateId === 's1')).toBe(true);
  });

  it('Condition requires expression', () => {
    const badConfig: ConditionConfig = { type: 'Condition', expression: '' };
    const states = statesMap(
      makeState('s1', 'Condition', 'Cond', badConfig),
      makeState('s2', 'End', 'End1', endConfig),
      makeState('s3', 'End', 'End2', endConfig)
    );
    const transitions = transitionsMap(
      makeTransition('t1', 's1', 's2', 'true'),
      makeTransition('t2', 's1', 's3', 'false')
    );
    const result = validateWorkflow(states, transitions);
    expect(result.errors.some((e) => e.errorType === 'MISSING_REQUIRED_FIELD' && e.stateId === 's1')).toBe(true);
  });

  it('Response requires messageTemplate', () => {
    const badConfig: ResponseConfig = { type: 'Response', messageTemplate: '  ' };
    const states = statesMap(
      makeState('s1', 'Response', 'Resp', badConfig),
      makeState('s2', 'End', 'End', endConfig)
    );
    const transitions = transitionsMap(makeTransition('t1', 's1', 's2'));
    const result = validateWorkflow(states, transitions);
    expect(result.errors.some((e) => e.errorType === 'MISSING_REQUIRED_FIELD' && e.stateId === 's1')).toBe(true);
  });

  it('Input requires prompt and variableName', () => {
    const badConfig: InputConfig = { type: 'Input', prompt: '', variableName: '', timeout: 300 };
    const states = statesMap(
      makeState('s1', 'Input', 'In', badConfig),
      makeState('s2', 'End', 'End', endConfig)
    );
    const transitions = transitionsMap(makeTransition('t1', 's1', 's2'));
    const result = validateWorkflow(states, transitions);
    const fieldErrors = result.errors.filter((e) => e.errorType === 'MISSING_REQUIRED_FIELD' && e.stateId === 's1');
    expect(fieldErrors.length).toBe(2); // prompt + variableName
  });

  it('Wait requires duration in [1, 86400]', () => {
    const badConfig: WaitConfig = { type: 'Wait', duration: 0 };
    const states = statesMap(
      makeState('s1', 'Wait', 'Wait', badConfig),
      makeState('s2', 'End', 'End', endConfig)
    );
    const transitions = transitionsMap(makeTransition('t1', 's1', 's2'));
    const result = validateWorkflow(states, transitions);
    expect(result.errors.some((e) => e.errorType === 'INVALID_FIELD_VALUE' && e.stateId === 's1')).toBe(true);
  });

  it('Parallel requires 2-10 branches', () => {
    const badConfig: ParallelConfig = { type: 'Parallel', branches: [{ id: '1', name: 'b1', stateIds: [] }] };
    const states = statesMap(
      makeState('s1', 'Parallel', 'Par', badConfig),
      makeState('s2', 'End', 'End', endConfig)
    );
    const transitions = transitionsMap(makeTransition('t1', 's1', 's2'));
    const result = validateWorkflow(states, transitions);
    expect(result.errors.some((e) => e.errorType === 'INVALID_FIELD_VALUE' && e.stateId === 's1')).toBe(true);
  });

  it('End state has no required fields', () => {
    const states = statesMap(
      makeState('s1', 'API_Call', 'Start', validApiConfig),
      makeState('s2', 'End', 'End', endConfig)
    );
    const transitions = transitionsMap(makeTransition('t1', 's1', 's2'));
    const result = validateWorkflow(states, transitions);
    expect(result.valid).toBe(true);
  });
});

// =============================================================================
// All Errors Reported Simultaneously (Requirement 4.6)
// =============================================================================

describe('Simultaneous error reporting', () => {
  it('reports multiple errors from different categories at once', () => {
    // Multiple issues: missing config, no outgoing, unreachable
    const badApiConfig: ApiCallConfig = { ...validApiConfig, url: '' };
    const states = statesMap(
      makeState('s1', 'API_Call', 'Start', badApiConfig),   // missing url
      makeState('s2', 'Response', 'Orphan', validResponseConfig), // unreachable + no outgoing
      makeState('s3', 'End', 'End', endConfig)
    );
    const transitions = transitionsMap(makeTransition('t1', 's1', 's3'));
    const result = validateWorkflow(states, transitions);
    expect(result.valid).toBe(false);
    // Should report: missing URL, no outgoing on s2, unreachable s2
    expect(result.errors.length).toBeGreaterThanOrEqual(3);
  });
});

// =============================================================================
// Valid Workflow (Requirement 4.7)
// =============================================================================

describe('Valid workflow success', () => {
  it('reports success for a valid workflow', () => {
    const states = statesMap(
      makeState('s1', 'API_Call', 'Fetch', validApiConfig),
      makeState('s2', 'Condition', 'Check', validConditionConfig),
      makeState('s3', 'Response', 'Good', validResponseConfig),
      makeState('s4', 'End', 'Done', endConfig)
    );
    const transitions = transitionsMap(
      makeTransition('t1', 's1', 's2'),
      makeTransition('t2', 's2', 's3', 'true'),
      makeTransition('t3', 's2', 's4', 'false'),
      makeTransition('t4', 's3', 's4')
    );
    const result = validateWorkflow(states, transitions);
    expect(result.valid).toBe(true);
    expect(result.errors).toHaveLength(0);
  });
});
