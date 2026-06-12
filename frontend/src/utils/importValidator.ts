/**
 * Validation utility for imported workflow definition JSON files.
 * Validates structure, required fields, and state type validity.
 *
 * Requirements: 7.4, 7.5, 7.6, 7.7
 */

import type { StateType } from '../types/canvas.types';

const VALID_STATE_TYPES: StateType[] = [
  'API_Call',
  'Condition',
  'Response',
  'Input',
  'Wait',
  'Parallel',
  'End',
];

export interface ImportValidationResult {
  valid: boolean;
  error?: string;
}

/**
 * Validates an imported JSON object against the WorkflowDefinition schema.
 * Returns { valid: true } if the definition is well-formed, or { valid: false, error: "..." }
 * with a specific error message if validation fails.
 */
export function validateImportedDefinition(json: unknown): ImportValidationResult {
  if (json === null || json === undefined || typeof json !== 'object') {
    return { valid: false, error: 'Invalid JSON structure: expected an object' };
  }

  const obj = json as Record<string, unknown>;

  // Validate metadata
  if (!obj.metadata || typeof obj.metadata !== 'object') {
    return { valid: false, error: 'Missing required field: metadata' };
  }

  const metadata = obj.metadata as Record<string, unknown>;
  if (!metadata.name || typeof metadata.name !== 'string') {
    return { valid: false, error: 'Missing required field: metadata.name' };
  }

  // Validate states array
  if (!obj.states) {
    return { valid: false, error: 'Missing required field: states' };
  }
  if (!Array.isArray(obj.states)) {
    return { valid: false, error: 'Invalid type: states must be an array' };
  }

  // Validate transitions array
  if (!obj.transitions) {
    return { valid: false, error: 'Missing required field: transitions' };
  }
  if (!Array.isArray(obj.transitions)) {
    return { valid: false, error: 'Invalid type: transitions must be an array' };
  }

  // Validate each state has a valid type
  for (let i = 0; i < obj.states.length; i++) {
    const state = obj.states[i] as Record<string, unknown> | null;
    if (!state || typeof state !== 'object') {
      return { valid: false, error: `Invalid state at index ${i}: expected an object` };
    }
    if (!state.type || typeof state.type !== 'string') {
      return { valid: false, error: `Invalid state at index ${i}: missing or invalid type` };
    }
    if (!VALID_STATE_TYPES.includes(state.type as StateType)) {
      return {
        valid: false,
        error: `Invalid state type "${state.type}" at index ${i}. Valid types: ${VALID_STATE_TYPES.join(', ')}`,
      };
    }
    if (!state.id || typeof state.id !== 'string') {
      return { valid: false, error: `Invalid state at index ${i}: missing or invalid id` };
    }
  }

  // Validate each transition has source and target
  for (let i = 0; i < obj.transitions.length; i++) {
    const transition = obj.transitions[i] as Record<string, unknown> | null;
    if (!transition || typeof transition !== 'object') {
      return { valid: false, error: `Invalid transition at index ${i}: expected an object` };
    }
    if (!transition.source || typeof transition.source !== 'string') {
      return { valid: false, error: `Invalid transition at index ${i}: missing or invalid source` };
    }
    if (!transition.target || typeof transition.target !== 'string') {
      return { valid: false, error: `Invalid transition at index ${i}: missing or invalid target` };
    }
  }

  return { valid: true };
}
